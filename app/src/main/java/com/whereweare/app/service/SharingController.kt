package com.whereweare.app.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.*
import com.whereweare.app.data.*
import com.whereweare.app.domain.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class TrackingState(val active: Boolean=false,val waiting: Boolean=false,val lastSent: java.time.Instant?=null,val fix: UserLocation?=null,val starting: Boolean=false,val pendingUpload: Boolean=false,val lastAcknowledged: java.time.Instant?=null,val publishedFix: UserLocation?=null,val recovering: Boolean=false,val deviceStatus: DeviceStatusObservation?=null,val retrying: Boolean=false,val failure: String?=null)
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
    private var generation=0L
    private val mutableState=MutableStateFlow(TrackingState())
    val state=mutableState.asStateFlow()
    val foregroundService=MutableStateFlow(false)
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
        val owner=++generation
        mutableState.value=state.value.copy(starting=true)
        tracking=serviceScope.launch {
            var remoteEnded=false
            var ownedSession: TrackingSession?=null
            try {
                withTimeout(12_000) {auth.awaitSession()}
                var saved: TrackingSession
                mutex.withLock {
                    check(preferences.pendingStop.first()==null) { "stop_pending" }
                    val user=requireNotNull(auth.userId)
                    val previous=preferences.trackingSession.first()
                    if(restarting) check(previous?.userId==user) { "sharing_stopped" }
                    saved=if(restarting) requireNotNull(previous) else TrackingSession(user,UUID.randomUUID().toString())
                    preferences.trackingSession(saved)
                    ownedSession=saved
                    mutableState.value=TrackingState(starting=true,waiting=true,recovering=restarting)
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
                            else { delay(30_000); true }
                        } }
                        .catch { cause ->
                            if(cause is CancellationException) throw cause
                            // A revoked permission must terminate sharing, not escape a child job and crash.
                            if(owner==generation) scope.launch { stopSessionAfterProviderFailure(owner) }
                        }
                        .collect { latest.value=it; mutableState.value=state.value.copy(fix=it,pendingUpload=true); signals.trySend(Unit) }
                }
                try {
                    var failures=0
                    var recoveredSession=false
                    var started=false
                    var revision: Long?=saved.revision
                    var sent: UserLocation?=null
                    var lastStatusCheck=0L
                    var lastDeviceSample: Long?=null
                    var lastDeviceReport=0L
                    var reportedDevice: DeviceStatus?=null
                    while(isActive) {
                        mutableState.value=state.value.copy(retrying=true)
                        try {
                            if(!location.hasPermission()) break
                            if(bootstrap.state.value.gate!=BootstrapGate.READY) break
                            if(!started) {
                                withTimeout(12_000) {
                                    repository.rpc("set_update_interval",kotlinx.serialization.json.buildJsonObject {put("seconds",kotlinx.serialization.json.JsonPrimitive(preferences.interval.first()))})
                                    if(revision==null) {
                                        revision=repository.sharingRevision()
                                        saved=saved.copy(revision=revision)
                                        // Persist before the RPC, so a retry cannot undo a later server stop.
                                        preferences.trackingSession(saved)
                                    }
                                    repository.sharing(true,session,revision)
                                }
                                started=true
                                mutableState.value=state.value.copy(active=true,starting=false,waiting=false,recovering=false)
                            }
                            val elapsed=android.os.SystemClock.elapsedRealtime()
                            if(lastStatusCheck==0L || elapsed-lastStatusCheck>=30_000) {
                                val remote=withTimeout(12_000) { repository.ownSharingStatus() }
                                if(!remote.is_sharing || remote.session_id!=session) { remoteEnded=true; break }
                                lastStatusCheck=elapsed
                                mutableState.value=state.value.copy(waiting=false)
                            }
                            latest.value?.takeIf { it!=sent }?.let {
                                if(java.time.Duration.between(it.recordedAt,java.time.Instant.now()).toHours()<2) {
                                    withTimeout(12_000) { repository.publish(session,it) }; sent=it; repository.saveOwn(it)
                                    mutableState.value=state.value.copy(active=true,waiting=false,lastSent=it.recordedAt,
                                        lastAcknowledged=java.time.Instant.now(),publishedFix=it,pendingUpload=latest.value!=sent)
                                }
                            }
                            if(lastDeviceSample==null || elapsed-lastDeviceSample>=60_000) {
                                lastDeviceSample=elapsed
                                val device=location.deviceStatus()
                                mutableState.value=state.value.copy(deviceStatus=DeviceStatusObservation(device,java.time.Instant.now()))
                                if(bootstrap.state.value.config?.features?.device_status==true && (sent!=null || restarting) &&
                                    shouldPublishDeviceStatus(reportedDevice,device,elapsed-lastDeviceReport)) {
                                    try {
                                        if(withTimeout(12_000) {repository.deviceStatus(session,device)}) {reportedDevice=device;lastDeviceReport=elapsed}
                                    } catch(e: CancellationException) {if(e !is TimeoutCancellationException) throw e}
                                    catch(_: Exception) { /* Device telemetry has separate diagnostics; sharing is still operational. */ }
                                }
                            }
                            failures=0
                            recoveredSession=false
                            mutableState.value=state.value.copy(waiting=false,failure=null)
                        } catch(_: TimeoutCancellationException) {
                            failures=(failures+1).coerceAtMost(5)
                            mutableState.value=state.value.copy(waiting=true,failure="unreachable")
                        } catch(e: CancellationException) { throw e
                        } catch(e: Exception) {
                            if("sharing_stopped" in e.message.orEmpty()) { remoteEnded=true; break }
                            mutableState.value=state.value.copy(waiting=true,failure=connectionFailure(e))
                            if(e is io.github.jan.supabase.exceptions.RestException && e.statusCode==401 && !recoveredSession) {
                                recoveredSession=true
                                try {withTimeout(12_000) {auth.recoverSession()}}
                                catch(cancel: CancellationException) {if(cancel !is TimeoutCancellationException) throw cancel;break}
                                catch(_: Exception) {break}
                            } else if(!retryableRead(e)) break
                            failures=(failures+1).coerceAtMost(5)
                        } finally {mutableState.value=state.value.copy(retrying=false)}
                        if(failures>0) delay((1_000L shl failures).coerceAtMost(30_000))
                        else withTimeoutOrNull(3_000) { signals.receive() }
                    }
                } finally { updates.cancel() }
            } catch(e: CancellationException) { throw e
            } catch(e: Exception) { mutableState.value=state.value.copy(waiting=true,failure=connectionFailure(e))
            } finally {
                // Destruction cancels the scope: preserve the session for Android's sticky restart.
                // A normal termination (remote stop, revoked permission, blocked bootstrap) clears it.
                if(currentCoroutineContext().isActive) {
                    mutex.withLock {
                        if(owner==generation) {
                            // A remote stop or session replacement must not stop another device.
                            val ended=ownedSession
                            if(!remoteEnded && ended!=null) {
                                // Persist stop and clear tracking in the same DataStore transaction.
                                preferences.pendingStop(ended.userId,ended.sessionId)
                                enqueueStop(ended.userId)
                            } else preferences.trackingSession(null)
                            mutableState.value=state.value.copy(active=false,starting=false)
                            stopService()
                        }
                    }
                } else if(owner==generation) {
                    mutableState.value=state.value.copy(active=false,starting=false)
                }
            }
        }
    }
    fun requestStop() { scope.launch { stop() } }
    private suspend fun stopSessionAfterProviderFailure(owner: Long) = mutex.withLock {
        if(owner!=generation) return@withLock
        val saved=preferences.trackingSession.first() ?: return@withLock
        preferences.pendingStop(saved.userId,saved.sessionId)
        enqueueStop(saved.userId)
        ++generation
        tracking?.cancel(); tracking=null
        context.stopService(Intent(context,LocationForegroundService::class.java))
        mutableState.value=TrackingState()
    }
    fun serviceStartFailed() {
        val failedGeneration=generation
        mutableState.value=TrackingState()
        scope.launch { stopSessionAfterProviderFailure(failedGeneration) }
    }
    suspend fun stop() = mutex.withLock {
        val id=auth.userId ?: return@withLock
        // Persist intent before terminating the foreground service or making a network call.
        preferences.pendingStop(id)
        enqueueStop(id)
        // Cancel without holding a join on a job that may be awaiting this same mutex.
        ++generation
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
        repository.sharing(false,preferences.pendingStopSession.first())
        preferences.pendingStop(null)
        true
    }
    suspend fun resumeFromVisibleActivity() {
        auth.awaitSession()
        if(state.value.active || state.value.starting || tracking?.isActive==true) return
        if(preferences.pendingStop.first()!=null) return
        val saved=preferences.trackingSession.first() ?: return
        if(saved.userId!=auth.userId || !location.hasPermission() || !location.enabled()) return
        if(bootstrap.state.value.gate!=BootstrapGate.READY) return
        if(!com.whereweare.app.MainActivity.visible) return
        mutableState.value=state.value.copy(starting=true)
        try { ContextCompat.startForegroundService(context,Intent(context,LocationForegroundService::class.java).setAction("RESUME")) }
        catch(_: Exception) { mutableState.value=TrackingState() }
    }
    suspend fun logout() {
        stop()
        check(preferences.pendingStop.first()==null) { "stop_pending" }
        auth.logout()
    }
}
