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
    val accountDeleted=auth.accountDeleted
    val session=auth.session
    fun recoverPassword() { message(R.string.password_recovery_unavailable) }
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
    val location: LocationRepository,private val preferences: PreferencesRepository,private val auth: AuthRepository,val avatars: AvatarRepository
): OperationViewModel() {
    private val ticks=flow { while(true) { emit(sharing.now()); delay(1_000) } }
    val state=combine(sharing.state,ticks,preferences.hidden(auth.userId.orEmpty())) { snapshot,now,hidden ->
        val visible=snapshot.locations.filter { fix ->
            fix.userId!=auth.userId && fix.userId !in hidden &&
                (snapshot.contacts[fix.userId]?.commonGroup==true || snapshot.shares.any { it.owner==fix.userId && it.viewer==auth.userId && it.enabled }) &&
                snapshot.statuses.any { it.userId==fix.userId && it.sharing } &&
                withinVisibility(fix.recordedAt,now,snapshot.contacts[fix.userId]?.visibilitySeconds ?: 600)
        }.map { VisiblePerson(snapshot.names[it.userId].orEmpty(),it,freshness(it.recordedAt,now)) }
        MapState(snapshot,visible,now)
    }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(0),MapState())
    private val permissionEpoch=MutableStateFlow(0)
    val local=combine(sharing.state,controller.state) { snapshot,tracking ->
        (snapshot.locations.filter { it.userId==auth.userId }+listOfNotNull(tracking.fix)).maxByOrNull { it.recordedAt }
    }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(0),null)
    val threshold=preferences.threshold.stateIn(viewModelScope,SharingStarted.WhileSubscribed(0),100)
    val mapStyle=preferences.mapStyle.stateIn(viewModelScope,SharingStarted.WhileSubscribed(0),"standard")
    val permission=permissionEpoch.map { location.permission() }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(0),location.permission())
    val pendingStop=controller.pendingStop.stateIn(viewModelScope,SharingStarted.WhileSubscribed(0),null)
    fun permissionsChanged() { permissionEpoch.value++ }
    fun start() { perform { controller.start() } }
    fun stop() { controller.requestStop() }
    fun refresh() { sharing.refresh() }
}
@HiltViewModel class PeopleViewModel @Inject constructor(private val sharing: SharingRepository,private val auth: AuthRepository,private val preferences: PreferencesRepository,val avatars: AvatarRepository): OperationViewModel() {
    val state=sharing.state
    val userId get()=auth.userId
    val hidden=preferences.hidden(auth.userId.orEmpty()).stateIn(viewModelScope,SharingStarted.WhileSubscribed(0),emptySet())
    fun hide(ids: Set<String>,value: Boolean) { perform { preferences.hide(requireNotNull(userId),ids,value) } }
    fun permissions(ids: Set<String>,value: Boolean) { perform { ids.forEach { sharing.permission(it,value) } } }
    fun remove(ids: Set<String>) { perform { ids.forEach { sharing.remove(it) } } }
    fun dismiss(id: String) { perform { sharing.dismiss(id) } }
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
    private val preferences: PreferencesRepository,private val controller: SharingController,val avatars: AvatarRepository,val location: LocationRepository): OperationViewModel() {
    val state=sharing.state
    val email get()=auth.email
    val registeredSince get()=registrationDate(auth.createdAt)
    val highAccuracy=preferences.highAccuracy.stateIn(viewModelScope,SharingStarted.WhileSubscribed(0),false)
    val interval=preferences.interval.stateIn(viewModelScope,SharingStarted.WhileSubscribed(0),60)
    val threshold=preferences.threshold.stateIn(viewModelScope,SharingStarted.WhileSubscribed(0),100)
    val mapStyle=preferences.mapStyle.stateIn(viewModelScope,SharingStarted.WhileSubscribed(0),"standard")
    fun interval(value: Int) { perform { preferences.interval(value) } }
    fun threshold(value: Int) { perform { preferences.threshold(value) } }
    fun visibility(value: Int) { perform { sharing.visibility(value) } }
    fun mapStyle(value: String) { perform { preferences.mapStyle(value) } }
    fun avatar(bytes: ByteArray) { perform(R.string.saved) { avatars.upload(bytes,state.value.profile?.avatarPath) } }
    fun removeAvatar() { state.value.profile?.avatarPath?.let { path -> perform(R.string.saved) { avatars.remove(path) } } }
    fun deleteAccount() { perform {
        controller.stop(); check(preferences.pendingStop.first()==null) { "stop_pending" }
        val id=requireNotNull(auth.userId); avatars.deleteAccount(); preferences.clearUser(id)
        auth.clearLocalSession()
    } }
    fun accuracy(value: Boolean) { perform { preferences.accuracy(value) } }
    fun rename(name: String) { if(!validName(name)) message(R.string.invalid_form) else perform(R.string.saved) { sharing.rename(name) } }
    fun logout() { perform { val id=auth.userId; controller.logout(); avatars.clear(); if(id!=null) preferences.clearUser(id) } }
}
@HiltViewModel class GroupsViewModel @Inject constructor(private val sharing: SharingRepository,private val auth: AuthRepository,private val preferences: PreferencesRepository,val avatars: AvatarRepository): OperationViewModel() {
    val state=sharing.state
    val userId get()=auth.userId
    val hidden=preferences.hidden(auth.userId.orEmpty()).stateIn(viewModelScope,SharingStarted.WhileSubscribed(0),emptySet())
    fun hide(ids: Set<String>,value: Boolean) { perform { preferences.hide(requireNotNull(userId),ids,value) } }
    fun create(name: String,emoji: String) { perform { sharing.createGroup(name,emoji) } }
    fun join(code: String) { perform { sharing.joinGroup(code) } }
    fun remove(group: String,ids: Set<String>) { perform { ids.forEach { sharing.removeMember(group,it) } } }
    fun delete(group: String) { perform { sharing.deleteGroup(group) } }
}
@HiltViewModel class BootstrapViewModel @Inject constructor(val repository: BootstrapRepository,private val controller: SharingController): ViewModel() {
    init { refresh() }
    fun refresh() { viewModelScope.launch { repository.refresh(); if(repository.state.value.gate in listOf(BootstrapGate.UPDATE,BootstrapGate.MAINTENANCE)) controller.requestStop() } }
}
