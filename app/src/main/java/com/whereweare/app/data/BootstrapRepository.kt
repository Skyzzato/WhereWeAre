package com.whereweare.app.data

import com.whereweare.app.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable data class BootstrapConfig(val latest_version_code: Int,val latest_version_name: String,
    val minimum_supported_version_code: Int,val maintenance_mode: Boolean,val maintenance_message: String?=null) {
    fun valid()=minimum_supported_version_code>0 && latest_version_code>=minimum_supported_version_code && latest_version_name.isNotBlank()
}
enum class BootstrapGate { LOADING, READY, UPDATE, MAINTENANCE, FIRST_CONNECTION }
fun bootstrapGate(config: BootstrapConfig?,installed: Int): BootstrapGate=when {
    config==null || !config.valid() -> BootstrapGate.FIRST_CONNECTION
    installed<config.minimum_supported_version_code -> BootstrapGate.UPDATE
    config.maintenance_mode -> BootstrapGate.MAINTENANCE
    else -> BootstrapGate.READY
}
data class BootstrapState(val gate: BootstrapGate=BootstrapGate.LOADING,val config: BootstrapConfig?=null)
// A cache write failure must never override a newly received blocking response.
suspend fun resolveBootstrap(cached: BootstrapConfig?,fetch: suspend ()->BootstrapConfig,save: suspend (BootstrapConfig)->Unit): BootstrapConfig? {
    val current=try { fetch().also { check(it.valid()) } }
    catch(e: CancellationException) { if(e !is TimeoutCancellationException) throw e; null }
    catch(_: Exception) { null }
    if(current==null) return cached?.takeIf { it.valid() }
    try { save(current) } catch(e: CancellationException) { throw e } catch(_: Exception) { /* Keep the authoritative response in memory. */ }
    return current
}
@Singleton class BootstrapRepository @Inject constructor(private val client: SupabaseClient,private val preferences: PreferencesRepository) {
    private val json=Json { ignoreUnknownKeys=true }
    val state=MutableStateFlow(BootstrapState())
    private val mutex=kotlinx.coroutines.sync.Mutex()
    suspend fun refresh() {
        if(!mutex.tryLock()) return
        try {
            val cached=try { preferences.bootstrap.first()?.let { json.decodeFromString<BootstrapConfig>(it) } }
                catch(e: CancellationException) {throw e} catch(_: Exception) {null}
            val config=resolveBootstrap(cached,
                fetch={withTimeout(8_000) { client.postgrest.rpc("app_bootstrap").decodeAs<BootstrapConfig>() }},
                save={preferences.bootstrap(json.encodeToString(BootstrapConfig.serializer(),it))})
            state.value=BootstrapState(bootstrapGate(config,BuildConfig.VERSION_CODE),config)
        } finally { mutex.unlock() }
    }
}
