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
                    "location_timeout" in key -> R.string.checkin_location_timeout
                    "request_cooldown" in key -> R.string.location_request_cooldown
                    "request_expired" in key -> R.string.location_request_expired
                    "group_expired" in key -> R.string.group_expired
                    "invalid_expiry" in key -> R.string.group_invalid_expiry
                    "already_connected" in key -> R.string.error_connected
                    "self_invite" in key -> R.string.self_invite
                    "not_authorized" in key -> R.string.error_forbidden
                    "group_not_found" in key -> R.string.group_not_found
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
    val location: LocationRepository,private val preferences: PreferencesRepository,private val auth: AuthRepository,val avatars: AvatarRepository,
    network: NetworkMonitor,bootstrap: BootstrapRepository,private val feedback: MeetingFeedback
): OperationViewModel() {
    private val ticks=flow { while(true) { emit(sharing.now()); delay(1_000) } }
    val state=combine(sharing.state,ticks,preferences.hidden(auth.userId.orEmpty()),preferences.hiddenGroups(auth.userId.orEmpty())) { snapshot,now,hidden,hiddenGroups ->
        val groupHidden=snapshot.members.filter { it.groupId in hiddenGroups }.map {it.userId}.toSet()
        val visible=snapshot.locations.filter { fix ->
            fix.userId!=auth.userId && fix.userId !in hidden && fix.userId !in groupHidden &&
                snapshot.contacts[fix.userId]?.canView==true &&
                activeLocationGrant(snapshot,fix.userId,auth.userId,now) &&
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
    val online=network.online
    val updateInterval=preferences.interval.stateIn(viewModelScope,SharingStarted.WhileSubscribed(0),60)
    val config=bootstrap.state
    val avatarScale=preferences.avatarScale.stateIn(viewModelScope,SharingStarted.WhileSubscribed(0),1f)
    fun meeting(lat: Double,lon: Double,all: Boolean,people: Set<String>,groups: Set<String>) { perform {
        val user=requireNotNull(auth.userId)
        val id=java.util.UUID.randomUUID().toString()
        val style=preferences.flareStyle.first()
        feedback.created(user,id,style)
        sharing.createMeeting(id,lat,lon,all,people,groups,style)
    } }
    fun removeMeeting(id: String) {perform {sharing.removeMeeting(id)}}
    fun checkin(id: String,type: String,message: String,people: Set<String>,groups: Set<String>,meeting: String?,completed: ()->Unit) {perform(R.string.checkin_sent) {
        val fix=try {location.snapshot(preferences.highAccuracy.first())} catch(_: TimeoutCancellationException) {error("location_timeout")}
        sharing.checkin(id,type,message,fix,people,groups,meeting)
        completed()
    }}
    fun removeEvent(id: String) {perform {sharing.removeEvent(id)}}
    val mapStyle=preferences.mapStyle.stateIn(viewModelScope,SharingStarted.WhileSubscribed(0),"standard")
    val permission=permissionEpoch.map { location.permission() }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(0),location.permission())
    val pendingStop=controller.pendingStop.stateIn(viewModelScope,SharingStarted.WhileSubscribed(0),null)
    fun permissionsChanged() { permissionEpoch.value++ }
    fun start() { perform { controller.start() } }
    fun stop() { controller.requestStop() }
    fun refresh() { sharing.refresh() }
}
@HiltViewModel class PeopleViewModel @Inject constructor(private val sharing: SharingRepository,private val auth: AuthRepository,private val preferences: PreferencesRepository,val avatars: AvatarRepository,network: NetworkMonitor,val location: LocationRepository,private val controller: SharingController): OperationViewModel() {
    val online=network.online
    val state=sharing.state
    val userId get()=auth.userId
    val hidden=preferences.hidden(auth.userId.orEmpty()).stateIn(viewModelScope,SharingStarted.WhileSubscribed(0),emptySet())
    fun hide(ids: Set<String>,value: Boolean) { perform { preferences.hide(requireNotNull(userId),ids,value) } }
    fun permissions(ids: Set<String>,value: Boolean) { perform { ids.forEach { sharing.permission(it,value) } } }
    fun remove(ids: Set<String>) { perform { ids.forEach { sharing.remove(it) } } }
    fun dismiss(id: String) { perform { sharing.dismiss(id) } }
    val found=MutableStateFlow<UserProfile?>(null)
    fun clearLookup() { found.value=null; message(null) }
    fun requestPerson(person: String) {perform(R.string.request_sent) {sharing.rpc("request_saved_person",kotlinx.serialization.json.buildJsonObject {put("person",kotlinx.serialization.json.JsonPrimitive(person))})}}
    fun lookup(code: String) {
        if(!validInviteCode(code)) { message(R.string.not_found); return }
        perform { val result=sharing.lookup(code);check(result!=null) {"not_found"}
            check(result.id!=userId) {"self_invite"}
            check(result.id !in state.value.savedPeople && state.value.shares.none {it.owner==result.id || it.viewer==result.id}) {"already_connected"}
            found.value=result }
    }
    fun send(completed: ()->Unit={}) { val code=found.value?.inviteCode ?: return; perform(R.string.request_sent) { sharing.sendRequest(code); found.value=null;completed() } }
    fun respond(id: String,accept: Boolean) { perform { sharing.respond(id,accept) } }
    fun cancel(id: String) { perform { sharing.cancel(id) } }
    fun permission(viewer: String,enabled: Boolean) { perform { sharing.permission(viewer,enabled) } }
    fun precision(viewer: String,value: Int?) {perform {sharing.sharedPrecision("person",viewer,value)}}
    fun requestLocation(person: String) {perform(R.string.request_sent) {sharing.requestLocation(person)}}
    fun respondLocation(id: String,accept: Boolean,completed: ()->Unit={}) {perform {
        if(accept) {check(location.hasPermission()) {"location_permission"};check(location.enabled()) {"location_disabled"}}
        val accepted=sharing.respondLocation(id,accept)
        if(accept && accepted) controller.start()
        completed()
    }}
    fun invite(group: String,person: String) { perform(R.string.request_sent) {sharing.inviteMember(group,person)} }
    fun reveal(person: String) {perform {preferences.hide(requireNotNull(userId),setOf(person),false);state.value.members.filter {it.userId==person}.forEach {preferences.hideGroup(requireNotNull(userId),it.groupId,false)}}}
}
@HiltViewModel class SettingsViewModel @Inject constructor(private val sharing: SharingRepository,private val auth: AuthRepository,
    private val preferences: PreferencesRepository,private val controller: SharingController,val avatars: AvatarRepository,val location: LocationRepository,private val push: com.whereweare.app.service.PushRegistration,private val analytics: ClientAnalytics,val avatarDrafts: AvatarDraftStore,network: NetworkMonitor): OperationViewModel() {
    val connectionDiagnostics=sharing.diagnostics
    val networkDiagnostics=network.state
    val trackingDiagnostics=controller.state
    val foregroundDiagnostics=controller.foregroundService.asStateFlow()
    val pendingStopDiagnostics=controller.pendingStop
    val diagnosticTestResult=MutableStateFlow<Int?>(null)
    fun precision(value: Int?) {perform {sharing.sharedPrecision("default",null,value)}}
    suspend fun audience()=sharing.audience()
    fun testConnection() { perform {
        diagnosticTestResult.value=null
        try { sharing.testConnection(); diagnosticTestResult.value=R.string.diag_test_success }
        catch(e: CancellationException) {if(e !is TimeoutCancellationException) throw e; diagnosticTestResult.value=R.string.diag_test_unreachable}
        catch(e: Exception) { diagnosticTestResult.value=when {
            e is io.github.jan.supabase.exceptions.RestException && e.statusCode==401 -> R.string.diag_test_auth
            e is io.github.jan.supabase.exceptions.RestException -> R.string.diag_test_server
            else -> R.string.diag_test_unreachable
        } }
    } }
    val userId get()=auth.userId
    val state=sharing.state
    val email get()=auth.email
    val flareStyle=preferences.flareStyle.stateIn(viewModelScope,SharingStarted.WhileSubscribed(0),1)
    val flareSound=preferences.flareSound.stateIn(viewModelScope,SharingStarted.WhileSubscribed(0),true)
    fun flareStyle(id: Int) {perform {preferences.flareStyle(id)}}
    fun flareSound(enabled: Boolean) {perform {preferences.flareSound(enabled)}}
    val registeredSince get()=registrationDate(auth.createdAt)
    val highAccuracy=preferences.highAccuracy.stateIn(viewModelScope,SharingStarted.WhileSubscribed(0),true)
    val interval=preferences.interval.stateIn(viewModelScope,SharingStarted.WhileSubscribed(0),60)
    val threshold=preferences.threshold.stateIn(viewModelScope,SharingStarted.WhileSubscribed(0),100)
    val mapStyle=preferences.mapStyle.stateIn(viewModelScope,SharingStarted.WhileSubscribed(0),"standard")
    val theme=preferences.theme.stateIn(viewModelScope,SharingStarted.WhileSubscribed(0),"default")
    val avatarScale=preferences.avatarScale.stateIn(viewModelScope,SharingStarted.WhileSubscribed(0),1f)
    val language=preferences.language.stateIn(viewModelScope,SharingStarted.WhileSubscribed(0),"system")
    fun theme(value: String) {perform {preferences.theme(value); viewModelScope.launch {analytics.preferencesChanged()}}}
    fun avatarScale(value: Float) {perform {preferences.avatarScale(value); viewModelScope.launch {analytics.preferencesChanged()}}}
    fun language(value: String) {perform {preferences.language(value); viewModelScope.launch {analytics.preferencesChanged()}}}
    fun interval(value: Int) { perform { sharing.rpc("set_update_interval",kotlinx.serialization.json.buildJsonObject {put("seconds",kotlinx.serialization.json.JsonPrimitive(value))}); preferences.interval(value) } }
    fun threshold(value: Int) { perform { preferences.threshold(value) } }
    fun visibility(value: Int) { perform { sharing.visibility(value) } }
    fun mapStyle(value: String) { perform { preferences.mapStyle(value) } }
    fun avatar(bytes: ByteArray,completed: ()->Unit) { perform(R.string.saved) {
        val user=requireNotNull(auth.userId)
        avatars.upload(bytes,state.value.profile?.avatarPath)
        avatarDrafts.clear(user)
        completed()
    } }
    fun removeAvatar() { state.value.profile?.avatarPath?.let { path -> perform(R.string.saved) { avatars.remove(path) } } }
    fun deleteAccount() { perform {
        controller.stop(); check(preferences.pendingStop.first()==null) { "stop_pending" }
        val id=requireNotNull(auth.userId); avatars.deleteAccount(); preferences.clearUser(id); avatarDrafts.clear(id)
        auth.clearLocalSession()
    } }
    fun accuracy(value: Boolean) { perform { preferences.accuracy(value) } }
    fun rename(name: String) { if(!validName(name)) message(R.string.invalid_form) else perform(R.string.saved) { sharing.rename(name) } }
    fun logout() { perform { val id=auth.userId; push.unregister(); controller.logout(); avatars.clear(); if(id!=null) {preferences.clearUser(id);avatarDrafts.clear(id)} } }
}
@HiltViewModel class GroupsViewModel @Inject constructor(private val sharing: SharingRepository,private val auth: AuthRepository,private val preferences: PreferencesRepository,val avatars: AvatarRepository,network: NetworkMonitor): OperationViewModel() {
    val now=flow {while(true) {emit(sharing.now());delay(1_000)}}.stateIn(viewModelScope,SharingStarted.WhileSubscribed(0),sharing.now())
    val online=network.online
    fun savePerson(id: String) {perform(R.string.saved) {sharing.savePerson(id)}}
    val state=sharing.state
    val userId get()=auth.userId
    val hidden=preferences.hidden(auth.userId.orEmpty()).stateIn(viewModelScope,SharingStarted.WhileSubscribed(0),emptySet())
    val hiddenGroups=preferences.hiddenGroups(auth.userId.orEmpty()).stateIn(viewModelScope,SharingStarted.WhileSubscribed(0),emptySet())
    fun hideGroup(gid: String,hide: Boolean) {perform {preferences.hideGroup(requireNotNull(userId),gid,hide)}}
    fun groupSharing(gid: String,enabled: Boolean) {perform {sharing.groupSharing(gid,enabled)}}
    fun precision(gid: String,value: Int?) {perform {sharing.sharedPrecision("group",gid,value)}}
    fun rename(gid: String,name: String) {perform {sharing.renameGroup(gid,name)}}
    fun edit(gid: String,name: String,emoji: String,expiry: Instant?,completed: ()->Unit={}) {perform {sharing.editGroup(gid,name,emoji,expiry);completed()}}
    fun cancelInvitation(id: String) {perform {sharing.cancelGroupInvitation(id)}}
    fun invite(gid: String,person: String) {perform(R.string.request_sent) {sharing.inviteMember(gid,person)}}
    fun inviteMany(gid: String,people: Set<String>) {perform(R.string.request_sent) {people.forEach {sharing.inviteMember(gid,it)}}}
    fun respond(request: String,accept: Boolean) {perform {sharing.respondGroup(request,accept)}}
    fun hide(ids: Set<String>,value: Boolean) { perform { preferences.hide(requireNotNull(userId),ids,value) } }
    fun create(name: String,emoji: String,expiry: Instant?=null,completed: ()->Unit={}) { perform { sharing.createGroup(name,emoji,expiry);completed() } }
    fun join(code: String,completed: ()->Unit={}) {
        if(state.value.groups.any {normalizeInviteCode(it.code)==normalizeInviteCode(code)}) {message(R.string.already_group_member);return}
        if(!validInviteCode(code)) {message(R.string.group_not_found);return}
        perform(R.string.request_sent) {sharing.joinGroup(code);completed()}
    }
    fun remove(group: String,ids: Set<String>) { perform { ids.forEach { sharing.removeMember(group,it) } } }
    fun delete(group: String) { perform { sharing.deleteGroup(group) } }
}
@HiltViewModel class AppearanceViewModel @Inject constructor(val invites: InviteStore,val preferences: PreferencesRepository, val sharing: SharingRepository,private val auth: AuthRepository,private val feedback: MeetingFeedback,val avatarDrafts: AvatarDraftStore,val controller: SharingController,@dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context): OperationViewModel() {
    val theme=preferences.theme.stateIn(viewModelScope,SharingStarted.Eagerly,"default")
    val language=preferences.language.stateIn(viewModelScope,SharingStarted.Eagerly,"system")
    val flareSound=preferences.flareSound.stateIn(viewModelScope,SharingStarted.WhileSubscribed(0),true)
    val notification=MutableStateFlow<MeetingPoint?>(null)
    val eventNotice=MutableStateFlow<AppEvent?>(null)
    val flare=combine(feedback.events,auth.session) {events,_ -> events.firstOrNull {it.user==auth.userId}}
        .stateIn(viewModelScope,SharingStarted.WhileSubscribed(0),null)
    fun finishFlare(event: FlareEvent?) {if(event!=null) auth.userId?.let {feedback.finish(it,event.id)}}
    val invite=MutableStateFlow<kotlinx.serialization.json.JsonObject?>(null)
    init {
        viewModelScope.launch {auth.session.map {auth.userId}.distinctUntilChanged().collect {
            notification.value=null;eventNotice.value=null;feedback.clear();invite.value=null
            val manager=context.getSystemService(android.app.NotificationManager::class.java)
            manager.activeNotifications.filter {it.notification.channelId in setOf("meetings","meetings-v032","location-requests","app-events")}.forEach {manager.cancel(it.tag,it.id)}
        }}
        viewModelScope.launch {combine(auth.session,preferences.language) {_,lang -> auth.userId to lang}.distinctUntilChanged().collect {(id,_) -> if(id!=null) com.whereweare.app.service.PushRegistration.enqueue(context)}}
    }
    suspend fun observeMeetings() {sharing.state.collect { snapshot ->
        val user=auth.userId ?: return@collect
        if(snapshot.profile?.id!=user) return@collect
        if(snapshot.events.none {it.id==eventNotice.value?.id && it.active(sharing.now())}) eventNotice.value=null
        snapshot.events.filter {it.sender_id!=user && it.active(sharing.now())}.sortedBy {it.created_at}.forEach {event ->
            if(preferences.markMeetingSeen(user,"event:${event.id}") && auth.userId==user) eventNotice.value=event
        }
        snapshot.meetings.sortedBy {it.created_at}.forEach { point ->
            val event=point.id+if(point.active) ":created" else ":removed"
            val fresh=if(point.active) feedback.created(user,point.id,point.flare_style_id) else preferences.markMeetingSeen(user,event)
            if(fresh && point.creator_id!=user) notification.value=point
        }
    }}
    fun resolve(token: String,confirm: Boolean=false) {perform {invite.value=sharing.inviteLink(token,confirm); if(confirm) invite.value=null}}
}
@HiltViewModel class BootstrapViewModel @Inject constructor(val repository: BootstrapRepository,private val controller: SharingController): ViewModel() {
    init { refresh() }
    fun refresh() { viewModelScope.launch { repository.refresh(); if(repository.state.value.gate in listOf(BootstrapGate.UPDATE,BootstrapGate.MAINTENANCE)) controller.requestStop() } }
}
