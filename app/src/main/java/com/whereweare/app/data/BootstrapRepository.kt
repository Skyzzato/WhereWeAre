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
    val minimum_supported_version_code: Int,val maintenance_mode: Boolean,val maintenance_message: String?=null,
    val defaults: GlobalDefaults=GlobalDefaults(),val features: FeatureFlags=FeatureFlags(),val api_version: Int=2,val update_url: String?=null) {
    fun valid()=minimum_supported_version_code>0 && latest_version_code>=minimum_supported_version_code && latest_version_name.isNotBlank()
}
@Serializable data class GlobalDefaults(val gps_interval_seconds: Int=5,val high_accuracy: Boolean=true,val theme: String="default",val avatar_scale: Float=1f,val stale_grace_seconds: Int=180)
@Serializable data class FeatureFlags(val meeting_points: Boolean=true,val invite_links: Boolean=false,val client_analytics: Boolean=false,val device_status: Boolean=false)
enum class BootstrapGate { LOADING, READY, UPDATE, MAINTENANCE, FIRST_CONNECTION, BACKEND_UPDATE }
fun bootstrapGate(config: BootstrapConfig?,installed: Int): BootstrapGate=when {
    config==null || !config.valid() -> BootstrapGate.FIRST_CONNECTION
    installed<config.minimum_supported_version_code -> BootstrapGate.UPDATE
    config.maintenance_mode -> BootstrapGate.MAINTENANCE
    installed>=4 && config.api_version<3 -> BootstrapGate.BACKEND_UPDATE
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
    private var lastVerified: Long?=null
    suspend fun refresh() {
        if(!mutex.tryLock()) return
        try {
            val cached=try { preferences.bootstrap.first()?.let { json.decodeFromString<BootstrapConfig>(it) } }
                catch(e: CancellationException) {throw e} catch(_: Exception) {null}
            var config=resolveBootstrap(state.value.config ?: cached,
                fetch={withTimeout(8_000) { client.postgrest.rpc("app_bootstrap").decodeAs<BootstrapConfig>().also {check(it.valid());lastVerified=android.os.SystemClock.elapsedRealtime()} }},
                save={preferences.bootstrap(json.encodeToString(BootstrapConfig.serializer(),it))})
            // Known obsolescence is sticky, including cache-write failures and offline refreshes.
            val blocked=listOfNotNull(state.value.config,cached).maxByOrNull {it.minimum_supported_version_code}
            if(blocked!=null && blocked.minimum_supported_version_code>BuildConfig.VERSION_CODE &&
                (config?.minimum_supported_version_code ?: 0)<blocked.minimum_supported_version_code) config=blocked
            val gate=bootstrapGate(config,BuildConfig.VERSION_CODE)
            val fresh=lastVerified?.let {android.os.SystemClock.elapsedRealtime()-it in 0..900_000}==true
            state.value=BootstrapState(if(gate==BootstrapGate.READY && !fresh) BootstrapGate.FIRST_CONNECTION else gate,config)
        } finally { mutex.unlock() }
    }
}
