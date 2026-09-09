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

data class TrackingState(val active: Boolean=false,val waiting: Boolean=false,val lastSent: java.time.Instant?=null)
@Singleton class SharingController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: SharingRepository,
    private val auth: AuthRepository,
    private val preferences: PreferencesRepository,
    private val location: LocationRepository
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
        check(location.hasPermission()) { "location_permission" }
        check(location.enabled()) { "location_disabled" }
        ContextCompat.startForegroundService(context,Intent(context,LocationForegroundService::class.java))
    }
    @OptIn(ExperimentalCoroutinesApi::class)
    fun attach(serviceScope: CoroutineScope, stopService: () -> Unit) {
        if(tracking?.isActive==true) return
        tracking=serviceScope.launch {
            try {
                mutex.withLock {
                    check(preferences.pendingStop.first()==null) { "stop_pending" }
                    check(auth.userId!=null)
                    mutableState.value=TrackingState(active=true,waiting=true)
                }
                val session=UUID.randomUUID().toString()
                val latest=MutableStateFlow<UserLocation?>(null)
                val updates=launch {
                    preferences.highAccuracy.flatMapLatest { location.fixes(it) }.collect { latest.value=it }
                }
                try {
                    var started=false
                    var revision: Long?=null
                    var sent: UserLocation?=null
                    while(isActive) {
                        try {
                            if(!started) {
                                if(revision==null) revision=repository.sharingRevision()
                                repository.sharing(true,session,revision); started=true
                            }
                            latest.value?.takeIf { it!=sent }?.let {
                                if(java.time.Duration.between(it.recordedAt,java.time.Instant.now()).toHours()<2) {
                                    repository.publish(session,it); sent=it
                                    mutableState.value=TrackingState(active=true,lastSent=java.time.Instant.now())
                                }
                            }
                        } catch(e: CancellationException) { throw e
                        } catch(e: Exception) {
                            if("sharing_stopped" in e.message.orEmpty()) break
                            mutableState.value=state.value.copy(waiting=true)
                        }
                        delay(3_000)
                    }
                } finally { updates.cancel() }
            } catch(e: CancellationException) { throw e
            } catch(_: Exception) { mutableState.value=state.value.copy(waiting=true)
            } finally { mutableState.value=state.value.copy(active=false); stopService() }
        }
    }
    fun requestStop() { scope.launch { stop() } }
    suspend fun stop() = mutex.withLock {
        val id=auth.userId ?: return@withLock
        // Persist intent before terminating the foreground service or making a network call.
        preferences.pendingStop(id)
        enqueueStop(id)
        tracking?.cancelAndJoin(); tracking=null
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
    suspend fun logout() {
        stop()
        check(preferences.pendingStop.first()==null) { "stop_pending" }
        auth.logout()
    }
}
