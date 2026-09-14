package com.whereweare.app.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.*
import com.whereweare.app.data.*
import com.whereweare.app.domain.UserLocation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class TrackingState(val active: Boolean=false,val waiting: Boolean=false,val lastSent: java.time.Instant?=null,val fix: UserLocation?=null,val starting: Boolean=false)
@Singleton class SharingController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: SharingRepository,
    private val auth: AuthRepository,
    private val preferences: PreferencesRepository,
    private val location: LocationRepository,private val bootstrap: BootstrapRepository
) {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val mutex=Mutex()
    private var tracking: Job?=null
    private val mutableState=MutableStateFlow(TrackingState())
    val state=mutableState.asStateFlow()
    val pendingStop=preferences.pendingStop
    init { scope.launch { preferences.pendingStop.first()?.let(::enqueueStop) } }

    private fun enqueueStop(id: String) {
        val request=OneTimeWorkRequestBuilder<StopSharingWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf("user_id" to id)).build()
        WorkManager.getInstance(context).enqueueUniqueWork("stop-$id",ExistingWorkPolicy.APPEND_OR_REPLACE,request)
    }

    fun start() {
        check(bootstrap.state.value.gate==BootstrapGate.READY) { "bootstrap_blocked" }
        check(location.hasPermission()) { "location_permission" }
        check(location.enabled()) { "location_disabled" }
        if(state.value.starting || state.value.active) return
        mutableState.value=state.value.copy(starting=true)
        try {ContextCompat.startForegroundService(context,Intent(context,LocationForegroundService::class.java))}
        catch(e: Exception) {mutableState.value=TrackingState();throw e}
    }
    @OptIn(ExperimentalCoroutinesApi::class)
    fun attach(serviceScope: CoroutineScope,restarting: Boolean=false,stopService: () -> Unit) {
        if(tracking?.isActive==true) return
        mutableState.value=state.value.copy(starting=true)
        tracking=serviceScope.launch {
            try {
                auth.awaitSession()
                var saved: TrackingSession
                mutex.withLock {
                    check(preferences.pendingStop.first()==null) { "stop_pending" }
                    val user=requireNotNull(auth.userId)
                    val previous=preferences.trackingSession.first()
                    if(restarting) check(previous?.userId==user) { "sharing_stopped" }
                    saved=if(restarting) requireNotNull(previous) else TrackingSession(user,UUID.randomUUID().toString())
                    preferences.trackingSession(saved)
                    mutableState.value=TrackingState(active=true,waiting=true)
                }
                // A system restart has no Activity/BootstrapViewModel to initialize these repositories.
                bootstrap.refresh()
                bootstrap.state.first { it.gate!=BootstrapGate.LOADING }
                check(bootstrap.state.value.gate==BootstrapGate.READY) { "bootstrap_blocked" }
                val session=saved.sessionId
                val latest=MutableStateFlow<UserLocation?>(null)
                val signals=kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)
                val updates=launch {
                    combine(preferences.highAccuracy,preferences.interval) { high,seconds -> high to seconds }
                        .flatMapLatest { (high,seconds) -> location.fixes(high,seconds).retryWhen { cause,_ ->
                            if(cause is SecurityException || !location.hasPermission()) false
                            else { mutableState.value=state.value.copy(waiting=true); delay(30_000); true }
                        } }
                        .catch { cause ->
                            if(cause is CancellationException) throw cause
                            // A revoked permission must terminate sharing, not escape a child job and crash.
                            requestStop()
                        }
                        .collect { latest.value=it; mutableState.value=state.value.copy(fix=it); signals.trySend(Unit) }
                }
                try {
                    var started=false
                    var revision: Long?=saved.revision
                    var sent: UserLocation?=null
                    while(isActive) {
                        try {
                            if(bootstrap.state.value.gate!=BootstrapGate.READY) break
                            if(!started) {
                                repository.rpc("set_update_interval",kotlinx.serialization.json.buildJsonObject {put("seconds",kotlinx.serialization.json.JsonPrimitive(preferences.interval.first()))})
                                if(revision==null) {
                                    revision=repository.sharingRevision()
                                    saved=saved.copy(revision=revision)
                                    // Persist before the RPC, so a retry cannot undo a later server stop.
                                    preferences.trackingSession(saved)
                                }
                                repository.sharing(true,session,revision); started=true
                            }
                            latest.value?.takeIf { it!=sent }?.let {
                                if(java.time.Duration.between(it.recordedAt,java.time.Instant.now()).toHours()<2) {
                                    repository.publish(session,it); sent=it; repository.saveOwn(it)
                                    mutableState.value=TrackingState(active=true,lastSent=it.recordedAt,fix=it)
                                }
                            }
                        } catch(e: CancellationException) { throw e
                        } catch(e: Exception) {
                            if("sharing_stopped" in e.message.orEmpty()) break
                            mutableState.value=state.value.copy(waiting=true)
                        }
                        withTimeoutOrNull(3_000) { signals.receive() }
                    }
                } finally { updates.cancel() }
            } catch(e: CancellationException) { throw e
            } catch(_: Exception) { mutableState.value=state.value.copy(waiting=true)
            } finally {
                // Destruction cancels the scope: preserve the session for Android's sticky restart.
                // A normal termination (remote stop, revoked permission, blocked bootstrap) clears it.
                if(currentCoroutineContext().isActive) {
                    preferences.trackingSession(null)
                    auth.userId?.let {preferences.pendingStop(it);enqueueStop(it)}
                }
                mutableState.value=state.value.copy(active=false,starting=false); stopService()
            }
        }
    }
    fun requestStop() { scope.launch { stop() } }
    fun serviceStartFailed() {mutableState.value=TrackingState();requestStop()}
    suspend fun stop() = mutex.withLock {
        val id=auth.userId ?: return@withLock
        // Persist intent before terminating the foreground service or making a network call.
        preferences.pendingStop(id)
        enqueueStop(id)
        // Cancel without holding a join on a job that may be awaiting this same mutex.
        val stopped=tracking;tracking=null;stopped?.cancel()
        context.stopService(Intent(context,LocationForegroundService::class.java))
        mutableState.value=TrackingState()
        try { withTimeout(12_000) { repository.sharing(false) }; preferences.pendingStop(null)
        } catch(e: CancellationException) { if(e !is TimeoutCancellationException) throw e
        } catch(_: Exception) { /* Durable WorkManager request owns the retry. */ }
    }
    suspend fun completePendingStop(expectedUser: String): Boolean = mutex.withLock {
        if(preferences.pendingStop.first()!=expectedUser) return@withLock true
        if(auth.userId!=expectedUser) return@withLock false
        repository.sharing(false)
        preferences.pendingStop(null)
        true
    }
    fun reconcileRemote(remote: Boolean) {
        if(remote && !state.value.active && !state.value.starting && tracking?.isActive!=true) requestStop()
    }
    suspend fun logout() {
        stop()
        check(preferences.pendingStop.first()==null) { "stop_pending" }
        auth.logout()
    }
}
