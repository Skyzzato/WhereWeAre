package com.whereweare.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.whereweare.app.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class AvatarRepository @Inject constructor(private val client: SupabaseClient,private val sharing: SharingRepository) {
    // Memory-only, versioned, account-scoped cache. No durable signed/public URL.
    private val cache=object: LruCache<String,Bitmap>(8*1024*1024) { override fun sizeOf(key: String,value: Bitmap)=value.byteCount }
    private val mutex=Mutex()
    private suspend fun request(path: String,method: String="GET",bytes: ByteArray?=null,type: String="application/json"): ByteArray=withContext(Dispatchers.IO) {
        val connection=URI(BuildConfig.SUPABASE_URL+path).toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod=method; connection.connectTimeout=10_000; connection.readTimeout=20_000
            connection.setRequestProperty("apikey",BuildConfig.SUPABASE_ANON_KEY)
            connection.setRequestProperty("Authorization","Bearer "+requireNotNull(client.auth.currentAccessTokenOrNull()))
            connection.setRequestProperty("Content-Type",type)
            connection.useCaches=false
            connection.setRequestProperty("Cache-Control","no-store, max-age=0")
            if(bytes!=null) { connection.doOutput=true; connection.outputStream.use { it.write(bytes) } }
            check(connection.responseCode in 200..299) { "server_operation_failed" }
            connection.inputStream.use {input ->
                val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                while(true) {val size=input.read(buffer);if(size<0) break;check(output.size()+size<=2*1024*1024) {"response_too_large"};output.write(buffer,0,size)}
                output.toByteArray()
            }
        } finally { connection.disconnect() }
    }
    suspend fun load(path: String): Bitmap?=mutex.withLock {
        if(SafeAvatar.reference(path)==null) {SafeAvatar.diagnostic("remote_reference_ignored");return@withLock null}
        val key=client.auth.currentUserOrNull()?.id+":"+path
        // Revalidate authorization at the origin after an in-memory cache miss.
        cache.get(key) ?: request("/storage/v1/object/authenticated/avatars/$path?cacheNonce=${UUID.randomUUID()}").let { bytes ->
            SafeAvatar.decode(bytes)?.also { cache.put(key,it) }
        }
    }
    suspend fun upload(bytes: ByteArray,previous: String?) {
        require(bytes.size<=1_048_576)
        val path=requireNotNull(client.auth.currentUserOrNull()).id+"/"+UUID.randomUUID()+".webp"
        request("/storage/v1/object/avatars/$path","POST",bytes,"image/webp")
        sharing.setAvatar(path)
        cache.evictAll()
        if(previous!=null) removeOwnedFile(previous)
    }
    suspend fun remove(previous: String) {
        sharing.setAvatar(null)
        cache.evictAll()
        removeOwnedFile(previous)
    }
    private suspend fun removeOwnedFile(path: String) {
        val owner=client.auth.currentUserOrNull()?.id ?: return
        // Profile RPC only accepts paths owned by that profile; never delete another user's file.
        if(!path.startsWith("$owner/") || path.removePrefix("$owner/").contains('/')) return
        try { removeFile(path) }
        catch(e: kotlinx.coroutines.CancellationException) { throw e }
        catch(_: Exception) { /* Profile already updated. Private orphan is removed by account cleanup. */ }
    }
    private suspend fun removeFile(path: String) { request("/storage/v1/object/avatars","DELETE",buildJsonObject { put("prefixes",buildJsonArray { add(path) }) }.toString().toByteArray()) }
    suspend fun deleteAccount() { request("/functions/v1/delete-account","POST","{}".toByteArray()); cache.evictAll() }
    fun clear() { cache.evictAll() }
}
