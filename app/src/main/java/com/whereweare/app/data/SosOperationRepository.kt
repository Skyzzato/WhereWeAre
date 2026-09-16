package com.whereweare.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.whereweare.app.domain.*
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable data class PendingSos(val id: String,val category: String,val people: Set<String>,val groups: Set<String>,val nearby: Boolean,val created: Long,val failed: Boolean=false)
fun definiteSosFailure(error: Exception)=error is RestException && error.statusCode in listOf(400,401,403,404,409,422,429)

/** Account-scoped durable intent, written before the network request. Recovery only reads. */
@Singleton class SosOperationRepository internal constructor(private val sharing: SharingRepository,private val auth: AuthRepository,
    private val location: LocationRepository,private val store: DataStore<Preferences>,private val scope: CoroutineScope) {
    @Inject constructor(sharing: SharingRepository,auth: AuthRepository,location: LocationRepository,store: DataStore<Preferences>):this(sharing,auth,location,store,CoroutineScope(SupervisorJob()+Dispatchers.IO))
    private val mutex=Mutex()
    val state=MutableStateFlow(SosSendState.IDLE)
    val error=MutableStateFlow<Int?>(null)
    private fun key(user: String)=stringPreferencesKey("pending_sos_$user")
    init {scope.launch {auth.session.map {auth.userId}.distinctUntilChanged().collectLatest {user ->
        mutex.withLock {
            state.value=SosSendState.IDLE;error.value=null
            if(user!=null) pending(user)?.let {state.value=if(it.failed) SosSendState.FAILED else SosSendState.UNKNOWN;verify(user)}
        }
    }}}
    private suspend fun pending(user: String)=store.data.first()[key(user)]?.let {Json.decodeFromString<PendingSos>(it)}
    private suspend fun verify(user: String): Boolean {
        val draft=pending(user) ?: return false
        return try {
            val exists=sharing.sosRegistered(draft.id)
            if(auth.userId==user && exists) {state.value=SosSendState.CONFIRMED;error.value=null}
            exists
        } catch(e: CancellationException) {if(e !is TimeoutCancellationException) throw e;false}
        catch(_: Exception) {false}
    }
    fun verify() {scope.launch {mutex.withLock {auth.userId?.let {verify(it)}}}}
    fun dismiss() {scope.launch {mutex.withLock {
        val user=auth.userId ?: return@withLock
        store.edit {it.remove(key(user))};state.value=SosSendState.IDLE;error.value=null
    }}}
    fun send(id: String,category: String,people: Set<String>,groups: Set<String>,nearby: Boolean) {
        // Try-lock drops double taps instead of queuing a second send after completion.
        if(!mutex.tryLock()) return
        scope.launch {
            try {
                val user=auth.userId ?: return@launch
                val existing=pending(user)
                if(existing!=null && verify(user)) return@launch
                val draft=(existing ?: PendingSos(id,category,people,groups,nearby,System.currentTimeMillis())).copy(failed=false)
                if(System.currentTimeMillis()-draft.created>5*60_000) {
                    state.value=SosSendState.UNKNOWN;error.value=com.whereweare.app.R.string.sos_old_request;return@launch
                }
                store.edit {it[key(user)]=Json.encodeToString(PendingSos.serializer(),draft)}
                state.value=SosSendState.SENDING;error.value=null
                val fallback=sharing.state.value.locations.firstOrNull {it.userId==user && it.recordedAt>=sharing.now().minusSeconds(86400)}
                val fix=try {withTimeout(8000) {location.snapshot(true)}} catch(e: CancellationException) {
                    if(e !is TimeoutCancellationException) throw e;fallback
                } catch(_: Exception) {fallback}
                check(auth.userId==user) {"not_authorized"}
                try {
                    withTimeout(15_000) {sharing.sendSos(draft.id,draft.category,draft.people,draft.groups,fix?.takeIf {it.userId==user},draft.nearby)}
                    if(auth.userId==user) state.value=SosSendState.CONFIRMED
                } catch(e: Exception) {
                    if(e is CancellationException && e !is TimeoutCancellationException) throw e
                    if(auth.userId==user) {
                        state.value=if(definiteSosFailure(e) && (existing==null || existing.failed)) SosSendState.FAILED else SosSendState.UNKNOWN
                        error.value=when {
                            "sos_cooldown" in e.message.orEmpty() -> com.whereweare.app.R.string.nearby_send_limit
                            "sos_already_active" in e.message.orEmpty() -> com.whereweare.app.R.string.sos_active
                            e is RestException && e.statusCode==401 -> com.whereweare.app.R.string.error_auth
                            e is RestException && e.statusCode==403 -> com.whereweare.app.R.string.error_forbidden
                            else -> null
                        }
                        store.edit {it[key(user)]=Json.encodeToString(PendingSos.serializer(),draft.copy(failed=state.value==SosSendState.FAILED))}
                        verify(user)
                    }
                }
            } catch(e: Exception) {
                state.value=SosSendState.UNKNOWN
                if(e is CancellationException) throw e
            } finally {mutex.unlock()}
        }
    }
}
