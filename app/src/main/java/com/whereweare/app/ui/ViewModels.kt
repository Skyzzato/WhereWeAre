package com.whereweare.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whereweare.app.R
import com.whereweare.app.data.*
import com.whereweare.app.domain.*
import com.whereweare.app.service.SharingController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Instant
import javax.inject.Inject

data class OperationState(val busy: Boolean=false,val message: Int?=null)
open class OperationViewModel: ViewModel() {
    private val mutableOperation=MutableStateFlow(OperationState())
    val operation=mutableOperation.asStateFlow()
    fun message(id: Int?) { mutableOperation.value=mutableOperation.value.copy(message=id) }
    protected fun perform(success: Int?=null,block: suspend () -> Unit) {
        if(mutableOperation.value.busy) return
        viewModelScope.launch {
            mutableOperation.value=OperationState(busy=true)
            try { block(); mutableOperation.value=OperationState(message=success)
            } catch(e: CancellationException) { throw e
            } catch(e: Exception) {
                val key=e.message.orEmpty()
                val resource=when {
                    e is java.io.IOException -> R.string.error_network
                    "configuration" in key -> R.string.error_config
                    "stop_pending" in key -> R.string.stop_pending
                    "location_permission" in key -> R.string.location_permission
                    "location_disabled" in key -> R.string.location_disabled
                    "already_connected" in key -> R.string.error_connected
                    "not_authorized" in key -> R.string.error_forbidden
                    "not_found" in key -> R.string.not_found
                    "invalid_credentials" in key || "email_not_confirmed" in key -> R.string.error_auth
                    else -> R.string.error_generic
                }
                mutableOperation.value=OperationState(message=resource)
            }
        }
    }
}
@HiltViewModel class AuthViewModel @Inject constructor(private val auth: AuthRepository): OperationViewModel() {
    val session=auth.session
    fun login(email: String,password: String) {
        if(!validEmail(email)||password.isEmpty()) { message(R.string.invalid_form); return }
        perform { auth.login(email,password) }
    }
    fun register(name: String,email: String,password: String,confirm: String) {
        if(!validName(name)||!validEmail(email)||!validPassword(password)||password!=confirm) { message(R.string.invalid_form); return }
        // Confirmation state is retained after the operation completes.
        perform { if(auth.register(name,email,password)) confirmation.value=true }
    }
    val confirmation=MutableStateFlow(false)
}
data class MapState(val snapshot: Snapshot=Snapshot(),val visible: List<VisiblePerson> = emptyList(),val now: Instant=Instant.now())
@HiltViewModel class MapViewModel @Inject constructor(
    val controller: SharingController,private val sharing: SharingRepository,
    val location: LocationRepository,private val preferences: PreferencesRepository,private val auth: AuthRepository
): OperationViewModel() {
    private val ticks=flow { while(true) { emit(sharing.now()); delay(1_000) } }
    val state=combine(sharing.state,ticks) { snapshot,now ->
        val visible=snapshot.locations.filter { fix ->
            fix.userId!=auth.userId && canSee(
                snapshot.shares.any { it.owner==fix.userId && it.viewer==auth.userId && it.enabled },
                snapshot.statuses.any { it.userId==fix.userId && it.sharing },fix.recordedAt,now)
        }.map { VisiblePerson(snapshot.names[it.userId].orEmpty(),it,freshness(it.recordedAt,now)) }
        MapState(snapshot,visible,now)
    }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(0),MapState())
    private val permissionEpoch=MutableStateFlow(0)
    @OptIn(ExperimentalCoroutinesApi::class)
    val local=combine(preferences.highAccuracy,permissionEpoch) { high,_ -> high }.flatMapLatest { high ->
        if(location.hasPermission()) location.fixes(high).catch { message(R.string.location_permission) } else flowOf(null)
    }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(0),null)
    val pendingStop=controller.pendingStop.stateIn(viewModelScope,SharingStarted.WhileSubscribed(0),null)
    fun permissionsChanged() { permissionEpoch.value++ }
    fun start() { perform { controller.start() } }
    fun stop() { controller.requestStop() }
    fun refresh() { sharing.refresh() }
}
@HiltViewModel class PeopleViewModel @Inject constructor(private val sharing: SharingRepository,private val auth: AuthRepository): OperationViewModel() {
    val state=sharing.state
    val userId get()=auth.userId
    val found=MutableStateFlow<UserProfile?>(null)
    fun clearLookup() { found.value=null; message(null) }
    fun lookup(code: String) {
        if(!validInviteCode(code)) { message(R.string.not_found); return }
        perform { found.value=sharing.lookup(code); check(found.value!=null) { "not_found" } }
    }
    fun send() { val code=found.value?.inviteCode ?: return; perform(R.string.request_sent) { sharing.sendRequest(code); found.value=null } }
    fun respond(id: String,accept: Boolean) { perform { sharing.respond(id,accept) } }
    fun cancel(id: String) { perform { sharing.cancel(id) } }
    fun permission(viewer: String,enabled: Boolean) { perform { sharing.permission(viewer,enabled) } }
}
@HiltViewModel class SettingsViewModel @Inject constructor(private val sharing: SharingRepository,private val auth: AuthRepository,
    private val preferences: PreferencesRepository,private val controller: SharingController): OperationViewModel() {
    val state=sharing.state
    val email get()=auth.email
    val highAccuracy=preferences.highAccuracy.stateIn(viewModelScope,SharingStarted.WhileSubscribed(0),false)
    fun accuracy(value: Boolean) { perform { preferences.accuracy(value) } }
    fun rename(name: String) { if(!validName(name)) message(R.string.invalid_form) else perform(R.string.saved) { sharing.rename(name) } }
    fun logout() { perform { controller.logout() } }
}
