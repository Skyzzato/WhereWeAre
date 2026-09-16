package com.whereweare.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.whereweare.app.domain.UserLocation
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import javax.inject.Inject
import javax.inject.Singleton

@Serializable data class NearbyStatus(val enabled: Boolean=false,val opted_in: Boolean=false,val available: Boolean=false,
    val available_until: String?=null,val server_time: String="",val radius_m: Int=2000,val availability_seconds: Int=900)
data class NearbyState(val status: NearbyStatus?=null,val pending: Boolean?=null,val failed: Boolean=false,
    val checkedElapsed: Long=android.os.SystemClock.elapsedRealtime()) {
    fun availableNow(elapsed: Long): Boolean {
        val confirmed=status ?: return false
        return !failed && confirmed.available && runCatching {
            java.time.Instant.parse(confirmed.available_until).isAfter(java.time.Instant.parse(confirmed.server_time).plusMillis((elapsed-checkedElapsed).coerceAtLeast(0)))
        }.getOrDefault(false)
    }
}
@Singleton class NearbySosRepository @Inject constructor(private val client: SupabaseClient,private val auth: AuthRepository,
    private val store: DataStore<Preferences>,private val location: LocationRepository,private val network: NetworkMonitor) {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val mutex=Mutex()
    private var lastRefresh=-300_000L
    @Volatile private var ending=false
    private val mutable=MutableStateFlow(NearbyState())
    val state=mutable.asStateFlow()
    private fun key(user: String)=booleanPreferencesKey("nearby_consent_pending_$user")
    init {
        scope.launch {location.details.filterNotNull().collect {details ->
            if(!com.whereweare.app.MainActivity.visible && !ending && network.online.value && mutable.value.status?.opted_in==true && mutable.value.pending!=false &&
                android.os.SystemClock.elapsedRealtime()-lastRefresh>=300_000) {
                lastRefresh=android.os.SystemClock.elapsedRealtime()
                try {refresh(details.fix)} catch(e: CancellationException) {if(e !is TimeoutCancellationException) throw e} catch(_: Exception) { }
            }
        }}
        scope.launch {network.online.collect { }};scope.launch {auth.session.map {auth.userId}.distinctUntilChanged().collectLatest {user ->
        mutable.value=NearbyState();ending=false;lastRefresh=-300_000L
        if(user!=null) while(isActive) {
            if(!ending && com.whereweare.app.MainActivity.visible && network.online.value) {
                sync()
                if(mutable.value.status?.opted_in==true && mutable.value.pending!=false && location.hasPermission() && location.enabled() &&
                    android.os.SystemClock.elapsedRealtime()-lastRefresh>=300_000) {
                    lastRefresh=android.os.SystemClock.elapsedRealtime()
                    try {refresh(location.snapshot(false))} catch(e: CancellationException) {if(e !is TimeoutCancellationException) throw e} catch(_: Exception) { /* Keep consent, show stale availability. */ }
                }
            }
            delay(15_000)
        }
    }} }
    suspend fun consent(agree: Boolean) {
        val user=auth.userId ?: return
        mutex.withLock {store.edit {it[key(user)]=agree};mutable.value=mutable.value.copy(pending=agree)}
        sync()
    }
    suspend fun endAvailability()=mutex.withLock {
        ending=true
        try {withTimeout(12_000) {client.postgrest.rpc("end_nearby_availability")}} catch(e: Exception) {ending=false;throw e}
    }
    suspend fun sync()=mutex.withLock {
        auth.awaitSession()
        val user=auth.userId ?: return@withLock
        val pending=store.data.first()[key(user)]
        try {
            if(pending!=null) {
                client.postgrest.rpc("set_nearby_sos_consent",buildJsonObject {put("agree",pending)})
                if(auth.userId!=user) return@withLock
                store.edit {it.remove(key(user))}
            }
            val status=withTimeout(12_000) {client.postgrest.rpc("nearby_sos_status").decodeAs<NearbyStatus>()}
            if(auth.userId==user) mutable.value=NearbyState(status)
        } catch(e: CancellationException) {if(e !is TimeoutCancellationException) throw e;mutable.value=mutable.value.copy(failed=true)}
        catch(_: Exception) {if(auth.userId==user) mutable.value=mutable.value.copy(pending=store.data.first()[key(user)],failed=true)}
    }
    suspend fun refresh(fix: UserLocation) {
        mutex.withLock {
            check(!ending && auth.userId==fix.userId && store.data.first()[key(fix.userId)]!=false) {"not_authorized"}
            client.postgrest.rpc("refresh_nearby_sos",buildJsonObject {
                put("lat",fix.latitude);put("lon",fix.longitude);put("accuracy_m",fix.accuracy);put("fix_at",fix.recordedAt.toString())
            })
        }
        sync()
    }
}
