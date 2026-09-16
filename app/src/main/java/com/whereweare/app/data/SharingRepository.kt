package com.whereweare.app.data

import android.os.SystemClock
import com.whereweare.app.domain.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Log

@Singleton class SharingRepository @Inject constructor(private val client: SupabaseClient, private val auth: AuthRepository,private val preferences: PreferencesRepository,private val network: NetworkMonitor) {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val connectionDiagnostics=ConnectionDiagnostics()
    val diagnostics=connectionDiagnostics.state
    init { scope.launch {auth.session.map {auth.userId}.distinctUntilChanged().collect {connectionDiagnostics.reset()}} }
    private suspend fun serverRpc(name: String,params: JsonObject=buildJsonObject {})=connectionDiagnostics.measure(
        operation=name,write=when(name) {
            "app_metadata","lookup_user_by_invite_code","visible_locations","location_audience","location_request_inbox","event_inbox","places_rules","sos_status","sos_registered","own_active_sos" -> false
            "resolve_invite_link" -> params["confirm"]?.jsonPrimitive?.booleanOrNull==true
            else -> true
        }
    ) { withTimeout(12_000) {auth.awaitSession();client.postgrest.rpc(name,params)} }
    suspend fun testConnection() { withTimeout(12_000) {ownSharingStatus()} }
    val online=network.online
    suspend fun awaitRecoverySignal() { merge(refreshes,network.state.map {it.online}.distinctUntilChanged().drop(1).filter {it}.map {Unit}).first() }
    private val refreshes=MutableSharedFlow<Unit>(extraBufferCapacity=1)
    private data class AvatarChange(val user: String,val path: String?)
    private val avatarChange=MutableStateFlow<AvatarChange?>(null)
    private val optimistic=OptimisticSnapshots()
    private data class ClockAnchor(val server: Instant,val elapsed: Long)
    @Volatile private var clock=ClockAnchor(Instant.now(),SystemClock.elapsedRealtime())
    fun now(): Instant = clock.let { it.server.plusMillis(SystemClock.elapsedRealtime()-it.elapsed) }
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<Snapshot> = auth.session.map { auth.userId }.distinctUntilChanged().flatMapLatest { id ->
        if(id==null) flowOf(Snapshot(loading=false)) else observe(id)
    }.combine(avatarChange) { snapshot,change ->
        if(change==null || snapshot.profile?.id!=change.user) snapshot
        else if(snapshot.profile.avatarPath==change.path) { avatarChange.compareAndSet(change,null); snapshot }
        else snapshot.copy(profile=snapshot.profile.copy(avatarPath=change.path))
    }.combine(optimistic.changes) { snapshot,pending -> optimistic.render(snapshot,pending) }
        .stateIn(scope,SharingStarted.WhileSubscribed(5_000),Snapshot())

    private fun observe(id: String): Flow<Snapshot> = channelFlow {
        var last=state.value.takeIf {it.profile?.id==id}?:Snapshot()
        var sessionRecoveryAttempted=false
        var failures=0
        var automaticBlocked=false
        var realtimeFailures=0
        val cached=preferences.lastFix(id).first()?.let { runCatching { Json.decodeFromString<LocationDto>(it).domain() }.getOrNull() }
        if(cached!=null) { last=last.copy(locations=listOf(cached)); send(last) }
        var realtimeOnline=false
        var realtimeStatus="CONNECTING"
        var disconnectedAt=SystemClock.elapsedRealtime()
        var lastPoll=0L
        var metadataDirty=true
        val generation=AtomicLong(0)
        val signals=Channel<Unit>(Channel.CONFLATED)
        launch {network.state.map {it.online}.distinctUntilChanged().collect {online -> generation.incrementAndGet();if(online) {automaticBlocked=false;failures=0;metadataDirty=true;signals.trySend(Unit)}}}
        launch { refreshes.collect { automaticBlocked=false;failures=0;metadataDirty=true; generation.incrementAndGet(); signals.trySend(Unit) } }
        launch {while(isActive) {
            delay(5_000)
            val time=SystemClock.elapsedRealtime()
            val unavailable=!realtimeOnline && time-disconnectedAt>=20_000
            if(last.realtimeUnavailable!=unavailable) {last=last.copy(realtimeUnavailable=unavailable);send(last)}
            if(!automaticBlocked && (!realtimeOnline || last.sharedPrecisionAvailable || last.eventsAvailable || last.locationRequestsAvailable) && time-lastPoll>=30_000) {lastPoll=time;metadataDirty=true;signals.trySend(Unit)}
        }}
        launch {
            for(signal in signals) {
                val readingGeneration=generation.get()
                try {
                    if(automaticBlocked) continue
                    if(!network.state.value.online) {last=last.copy(loading=false,offline=true,syncInProgress=false,syncFailed=false,syncError=null);send(last);continue}
                    last=last.copy(syncInProgress=true,syncError=null);send(last)
                    withTimeout(12_000) {auth.awaitSession()}
                    val readMetadata=metadataDirty
                    val requestStarted=SystemClock.elapsedRealtime()
                    val metadata=if(readMetadata) connectionDiagnostics.measure(false,"metadata_decode") {serverRpc("app_metadata").decodeAs<MetadataDto>()} else null
                    if(metadata!=null) {
                        clock=ClockAnchor(Instant.parse(metadata.server_time),requestStarted)
                        if(readingGeneration!=generation.get()) {signals.trySend(Unit);continue}
                        last=applyMetadata(last,metadata).copy(syncInProgress=true);send(last)
                    }
                    val profile=metadata?.profile?.domain() ?: last.profile
                    val names=metadata?.names?.associate {it.user_id to it.display_name} ?: last.names
                    val requests=if(metadata?.location_requests_available==true) connectionDiagnostics.measure(false,"share_requests") {
                        withTimeout(12_000) {client.from("share_requests").select().decodeList<RequestDto>().map {it.domain()}}
                    } else metadata?.requests?.map {it.domain()} ?: last.requests
                    val shares=metadata?.shares?.map {it.domain()} ?: last.shares
                    val statuses=metadata?.statuses?.map {it.domain()} ?: last.statuses
                    val precisionAvailable=metadata?.shared_precision ?: last.sharedPrecisionAvailable
                    val locations=if(precisionAvailable) connectionDiagnostics.measure(false,"locations_decode") {serverRpc("visible_locations").decodeList<LocationDto>().map {it.domain()}}
                        else connectionDiagnostics.measure(write=false,operation="latest_locations") {withTimeout(12_000) {client.from("latest_locations").select().decodeList<LocationDto>().map {it.domain()}}}
                    val contacts=metadata?.contacts?.map {it.domain()}?.associateBy {it.id} ?: last.contacts
                    val groups=metadata?.groups?.map {it.domain()} ?: last.groups
                    val members=metadata?.members?.map {it.domain()} ?: last.members
                    val groupRequests=metadata?.group_requests ?: last.groupRequests
                    val meetings=metadata?.meetings ?: last.meetings
                    if(readingGeneration!=generation.get()) { signals.trySend(Unit); continue }
                    if(readMetadata) metadataDirty=false
                    // REST success means data is current enough to display; Realtime status is tracked separately.
                    last=Snapshot(profile,names,requests,shares,statuses,locations,loading=false,offline=false,contacts=contacts,groups=groups,members=members,groupRequests=groupRequests,meetings=meetings,syncFailed=false,realtimeUnavailable=last.realtimeUnavailable,savedPeople=metadata?.saved_people?.toSet() ?: last.savedPeople,sharedPrecisionAvailable=precisionAvailable,locationRequestsAvailable=metadata?.location_requests_available ?: last.locationRequestsAvailable,locationRequests=metadata?.location_requests ?: last.locationRequests,eventsAvailable=metadata?.events_available ?: last.eventsAvailable,events=metadata?.events ?: last.events,temporaryGroupsAvailable=metadata?.temporary_groups_available ?: last.temporaryGroupsAvailable,placesAvailable=metadata?.places_available ?: last.placesAvailable,sosAvailable=metadata?.sos_available ?: last.sosAvailable,nearbySosAvailable=metadata?.nearby_sos_available ?: last.nearbySosAvailable)
                    locations.firstOrNull { it.userId==id }?.let { saveOwn(it) }
                    failures=0
                    sessionRecoveryAttempted=false
                    send(last)
                } catch(e: CancellationException) {
                    if(e !is TimeoutCancellationException) throw e
                    if(readingGeneration!=generation.get()) {signals.trySend(Unit);continue}
                    last=last.copy(loading=false,syncFailed=true,syncInProgress=false,syncError="unreachable");send(last)
                    failures=(failures+1).coerceAtMost(4); if(failures>=4) automaticBlocked=true
                    delay((1000L shl failures)+kotlin.random.Random.nextLong(1000));if(failures<=3) signals.trySend(Unit)
                } catch(e: Exception) {
                    if(readingGeneration!=generation.get()) {signals.trySend(Unit);continue}
                    if(com.whereweare.app.BuildConfig.DEBUG) Log.w("WhereWeAreRealtime","REST refresh failed: ${e.javaClass.simpleName}")
                    if(connectionFailure(e)=="session" && !sessionRecoveryAttempted) {
                        sessionRecoveryAttempted=true
                        last=last.copy(syncInProgress=true,syncError=null);send(last)
                        try {
                            withTimeout(12_000) {auth.recoverSession()}
                            automaticBlocked=false;signals.trySend(Unit);continue
                        } catch(cancel: CancellationException) {if(cancel !is TimeoutCancellationException) throw cancel}
                        catch(_: Exception) { /* Report the original session failure after recovery fails. */ }
                        if(readingGeneration!=generation.get()) {signals.trySend(Unit);continue}
                    }
                    last=last.copy(loading=false,offline=connectionFailure(e)=="unreachable",syncFailed=true,syncInProgress=false,syncError=connectionFailure(e)); send(last)
                    failures=(failures+1).coerceAtMost(4)
                    if(retryableRead(e)) delay((1000L shl failures)+kotlin.random.Random.nextLong(1000))
                    // Stop automatic retries on authentication/authorization failures; resume/network/manual refresh can retry.
                    automaticBlocked=failures>=4 || !retryableRead(e)
                    if(!automaticBlocked && failures<=3) signals.trySend(Unit)
                }
            }
        }
        // Profiles and requests remain usable even if the WebSocket is temporarily unavailable.
        signals.trySend(Unit)
        while(isActive) {
            network.state.first {it.online}
            val channel=client.realtime.channel("whereweare-$id")
            try {
                coroutineScope {
                    listOf("latest_locations","share_requests","location_shares","sharing_status","account_events").forEach { tableName ->
                        // DELETE events don't apply row RLS in Supabase. Expiry is handled locally;
                        // explicit stop/revoke always uses UPDATE and remains observable under RLS.
                        val changes=merge(
                            channel.postgresChangeFlow<PostgresAction.Insert>(schema="public") { table=tableName },
                            channel.postgresChangeFlow<PostgresAction.Update>(schema="public") { table=tableName }
                        )
                        launch {
                            changes.collect {
                                if(tableName!="latest_locations") { metadataDirty=true; generation.incrementAndGet() }
                                if(tableName=="location_shares" || tableName=="sharing_status" || tableName=="account_events") {
                                    generation.incrementAndGet()
                                    // Invalidate first: an older in-memory fix must not survive revocation.
                                    val record=when(it) {is PostgresAction.Update -> it.record;is PostgresAction.Insert -> it.record;else -> buildJsonObject {}}
                                    last=invalidateRealtime(last,tableName,record,id); send(last)
                                }
                                signals.trySend(Unit)
                            }
                        }
                    }
                    launch { channel.status.collect { status ->
                        val previous=realtimeStatus; realtimeStatus=status.name
                        if(com.whereweare.app.BuildConfig.DEBUG) Log.d("WhereWeAreRealtime","status $previous -> $realtimeStatus")
                        if(realtimeOnline && status!=RealtimeChannel.Status.SUBSCRIBED) disconnectedAt=SystemClock.elapsedRealtime()
                        realtimeOnline=status==RealtimeChannel.Status.SUBSCRIBED
                        connectionDiagnostics.realtime(realtimeOnline)
                        if(realtimeOnline) {realtimeFailures=0; metadataDirty=true; signals.trySend(Unit) }
                        // CONNECTING/RECONNECTING is not an offline REST failure; avoid bootstrap flicker.
                    } }
                    launch { channel.systemFlow().collect { event ->
                        if(com.whereweare.app.BuildConfig.DEBUG) Log.d("WhereWeAreRealtime","system status=${event.status}")
                        check(event.status!="error") { "realtime_unavailable" }
                    } }
                    withTimeout(12_000) {auth.awaitSession();channel.subscribe(blockUntilSubscribed=true)}
                    signals.trySend(Unit)
                    awaitCancellation()
                }
            } catch(e: Exception) {
                if(e is CancellationException && e !is TimeoutCancellationException) throw e
                if(realtimeOnline) disconnectedAt=SystemClock.elapsedRealtime()
                realtimeOnline=false;realtimeStatus="ERROR"
                realtimeFailures=(realtimeFailures+1).coerceAtMost(5)
            } finally { connectionDiagnostics.realtime(false); withContext(NonCancellable) { withTimeoutOrNull(12_000) {runCatching { client.realtime.removeChannel(channel) }} } }
            if(realtimeFailures>=4) {awaitRecoverySignal();realtimeFailures=0}
            else delay((1000L shl realtimeFailures)+kotlin.random.Random.nextLong(1000))
        }
    }.flowOn(Dispatchers.IO.limitedParallelism(1))
    fun refresh() { refreshes.tryEmit(Unit) }
    private suspend fun mutate(change: (Snapshot)->Snapshot,confirmed: (Snapshot)->Boolean,action: suspend ()->Unit) {
        val key=optimistic.begin(requireNotNull(auth.userId),change,confirmed)
        try { action(); refresh() } catch(e: Exception) { optimistic.rollback(key); throw e }
    }
    suspend fun setAvatar(path: String?) {
        val user=requireNotNull(auth.userId)
        serverRpc("set_avatar",buildJsonObject { put("path",path?.let(::JsonPrimitive) ?: JsonNull) })
        avatarChange.value=AvatarChange(user,path)
        refresh()
    }
    suspend fun saveOwn(fix: UserLocation) { preferences.lastFix(fix.userId,Json.encodeToString(LocationDto.serializer(),LocationDto(fix.userId,fix.latitude,fix.longitude,fix.accuracy,fix.speed,fix.bearing,fix.recordedAt.toString()))) }
    suspend fun rpc(name: String,params: JsonObject=buildJsonObject {}) { serverRpc(name,params); refresh() }
    suspend fun visibility(seconds: Int) = rpc("set_visibility",buildJsonObject { put("seconds",seconds) })
    suspend fun sharedPrecision(scope: String,target: String?,value: Int?)=rpc("set_shared_precision",buildJsonObject {
        put("scope",scope);put("target",target?.let(::JsonPrimitive) ?: JsonNull);put("value",value?.let(::JsonPrimitive) ?: JsonNull)
    })
    suspend fun sendSos(id: String,category: String,people: Set<String>,groups: Set<String>,fix: UserLocation?,nearby: Boolean=false) {
        val result=serverRpc(if(nearby) "send_sos_v042" else "send_sos",buildJsonObject {
            if(nearby) put("nearby",true)
            put("eid",id);put("category",category);putJsonArray("people") {people.forEach {add(it)}};putJsonArray("group_ids") {groups.forEach {add(it)}}
            put("lat",fix?.latitude?.let(::JsonPrimitive)?:JsonNull);put("lon",fix?.longitude?.let(::JsonPrimitive)?:JsonNull)
            put("accuracy_m",fix?.accuracy?.let(::JsonPrimitive)?:JsonNull);put("fix_at",fix?.recordedAt?.toString()?.let(::JsonPrimitive)?:JsonNull)
        }).decodeAs<String>()
        check(result==id)
        refresh()
    }
    suspend fun sosRegistered(id: String): Boolean=serverRpc("sos_registered",buildJsonObject {put("eid",id)}).decodeAs()
    suspend fun ownActiveSos(): AppEvent?=serverRpc("own_active_sos").decodeAs()
    suspend fun sosStatus(id: String): SosStatus=serverRpc("sos_status",buildJsonObject {put("eid",id)}).decodeAs()
    suspend fun respondSos(id: String,response: String?)=rpc("respond_sos",buildJsonObject {put("eid",id);put("answer",response?.let(::JsonPrimitive)?:JsonNull)})
    suspend fun closeSos(id: String,reason: String)=rpc("close_sos",buildJsonObject {put("eid",id);put("closure_reason",reason)})
    suspend fun places(): PlacesBundle=serverRpc("places_rules").decodeAs()
    suspend fun savePlace(p: SavedPlace)=rpc("save_place_v043",buildJsonObject {
        put("icon",p.emoji);put("pid",p.id);put("slot_number",p.slot);put("label",p.name);put("lat",p.latitude);put("lon",p.longitude);put("radius",p.radius_m)
    })
    suspend fun removePlace(id: String)=rpc("remove_place",buildJsonObject {put("pid",id)})
    suspend fun saveRule(r: PlaceRule)=rpc("save_place_rule",buildJsonObject {
        put("rid",r.id);put("pid",r.place_id);put("subject",r.subject_id);put("rule_kind",r.kind);put("armed",r.enabled)
        putJsonArray("targets") {r.recipients.forEach {add(it)}}
    })
    suspend fun removeRule(id: String)=rpc("remove_place_rule",buildJsonObject {put("rid",id)})
    suspend fun audience(): List<AudienceMember> = serverRpc("location_audience").decodeList()
    suspend fun requestLocation(person: String)=rpc("request_location",buildJsonObject {put("person",person)})
    suspend fun respondLocation(id: String,accept: Boolean): Boolean = serverRpc("respond_location_request",buildJsonObject {put("request_id",id);put("accept",accept)}).data.let {refresh();it=="true"}
    suspend fun checkin(id: String,type: String,message: String,fix: UserLocation,people: Set<String>,groups: Set<String>,meeting: String?)=rpc("create_checkin",buildJsonObject {
        put("eid",id);put("checkin_type",type);put("message",message);put("lat",fix.latitude);put("lon",fix.longitude);put("accuracy_m",fix.accuracy);put("fix_at",fix.recordedAt.toString())
        putJsonArray("people") {people.forEach {add(it)}};putJsonArray("group_ids") {groups.forEach {add(it)}};put("meeting",meeting?.let(::JsonPrimitive) ?: JsonNull)
    })
    suspend fun removeEvent(id: String)=rpc("dismiss_event",buildJsonObject {put("eid",id)})
    suspend fun remove(person: String)=mutate({ s -> s.copy(shares=s.shares.filter { it.owner!=person && it.viewer!=person },savedPeople=s.savedPeople-person) },{ s -> person !in s.savedPeople && s.shares.none { it.owner==person || it.viewer==person } }) { rpc("remove_connection",buildJsonObject { put("other_user_id",person) }) }
    suspend fun savePerson(person: String)=mutate({it.copy(savedPeople=it.savedPeople+person)},{person in it.savedPeople}) {rpc("save_group_person",buildJsonObject {put("person",person)})}
    suspend fun dismiss(id: String)=rpc("dismiss_request",buildJsonObject { put("request_id",id) })
    suspend fun createGroup(name: String,emoji: String,expiry: Instant?=null) { require(validGroupName(name))
        val timed=state.value.temporaryGroupsAvailable
        check(timed || expiry==null)
        rpc(if(timed) "create_group_timed" else "create_group",buildJsonObject {put("group_name",name.trim());put("group_emoji",emoji);if(timed) put("end_at",expiry?.toString()?.let(::JsonPrimitive) ?: JsonNull)})
    }
    suspend fun joinGroup(code: String) { check(serverRpc("join_group",buildJsonObject { put("code",code) }).data!="null") { "group_not_found" }; refresh() }
    suspend fun removeMember(group: String,member: String)=mutate({ s -> s.copy(members=s.members.filterNot { it.groupId==group && it.userId==member }) },{ s -> s.members.none { it.groupId==group && it.userId==member } }) { rpc("remove_group_member",buildJsonObject { put("gid",group); put("member",member) }) }
    suspend fun deleteGroup(group: String)=mutate({s -> s.copy(groups=s.groups.filterNot { it.id==group }) },{s -> s.groups.none {it.id==group} }) { rpc("delete_group",buildJsonObject { put("gid",group) }) }
    suspend fun renameGroup(group: String,name: String)=mutate({ s -> s.copy(groups=s.groups.map { if(it.id==group) it.copy(name=name) else it }) },{s -> s.groups.any {it.id==group && it.name==name} }) { rpc("rename_group",buildJsonObject { put("gid",group); put("group_name",name) }) }
    suspend fun editGroup(group: String,name: String,emoji: String,expiry: Instant?)=mutate({s -> s.copy(
        groups=s.groups.map {if(it.id==group) it.copy(name=name.trim(),emoji=emoji,expiresAt=expiry) else it},
        groupRequests=s.groupRequests.map {if(it.group_id==group) it.copy(group_name=name.trim(),group_emoji=emoji) else it})},
        {s -> s.groups.any {it.id==group && it.name==name.trim() && it.emoji==emoji && it.expiresAt==expiry}}) {
        val timed=state.value.temporaryGroupsAvailable
        check(timed || expiry==null)
        rpc(if(timed) "edit_group_timed" else "edit_group",buildJsonObject {put("gid",group);put("group_name",name.trim());put("group_emoji",emoji);if(timed) put("end_at",expiry?.toString()?.let(::JsonPrimitive) ?: JsonNull)})
    }
    suspend fun cancelGroupInvitation(id: String)=mutate({s -> s.copy(groupRequests=s.groupRequests.filterNot {it.id==id})},
        {s -> s.groupRequests.none {it.id==id && it.status=="pending"}}) {
        rpc("cancel_group_invitation",buildJsonObject {put("request_id",id)})
    }
    suspend fun groupSharing(group: String,enabled: Boolean)=mutate({s -> s.copy(members=s.members.map {if(it.groupId==group && it.userId==auth.userId) it.copy(sharingEnabled=enabled) else it}) },{s -> s.members.any {it.groupId==group && it.userId==auth.userId && it.sharingEnabled==enabled} }) { rpc("set_group_sharing",buildJsonObject { put("gid",group); put("enabled",enabled) }) }
    suspend fun inviteMember(group: String,person: String)=rpc("invite_group_member",buildJsonObject { put("gid",group); put("person",person) })
    suspend fun respondGroup(request: String,accept: Boolean)=mutate({s -> s.copy(groupRequests=s.groupRequests.map {if(it.id==request) it.copy(status=if(accept) "accepted" else "rejected") else it}) },{s -> s.groupRequests.none {it.id==request && it.status=="pending"} }) { rpc("respond_group_request",buildJsonObject { put("request_id",request); put("accept",accept) }) }
    suspend fun createMeeting(id: String,lat: Double,lon: Double,all: Boolean,people: Set<String>,groups: Set<String>,styleId: Int=FlareStyles.DEFAULT_ID)=rpc("create_meeting_styled",buildJsonObject {
        put("flare_style",com.whereweare.app.domain.FlareStyles.normalize(styleId))
        put("mid",id); put("lat",lat); put("lon",lon); put("all_people",all)
        putJsonArray("people") {people.forEach {add(it)}}; putJsonArray("group_ids") {groups.forEach {add(it)}}
    })
    suspend fun removeMeeting(id: String)=mutate({s -> s.copy(meetings=s.meetings.map {if(it.id==id) it.copy(active=false) else it}) },{s -> s.meetings.none {it.id==id && it.active} }) { rpc("remove_meeting",buildJsonObject {put("mid",id)}) }
    suspend fun inviteLink(token: String,confirm: Boolean): JsonObject? = serverRpc("resolve_invite_link",buildJsonObject {put("token",token);put("confirm",confirm)}).data.let { if(it=="null") null else Json.parseToJsonElement(it).jsonObject }.also { if(confirm) refresh() }
    suspend fun lookup(code: String): UserProfile? = serverRpc("lookup_user_by_invite_code",buildJsonObject { put("code",normalizeInviteCode(code)) }).decodeList<LookupDto>().firstOrNull()?.domain()
    suspend fun sendRequest(code: String) {
        val result=serverRpc("send_share_request",buildJsonObject { put("code",normalizeInviteCode(code)) }).data
        check(result!="null") { "not_found" }; refresh()
    }
    suspend fun respond(id: String,accept: Boolean) { serverRpc("respond_to_share_request",buildJsonObject { put("request_id",id); put("accept",accept) }); refresh() }
    suspend fun cancel(id: String) { serverRpc("cancel_share_request",buildJsonObject { put("request_id",id) }); refresh() }
    suspend fun permission(viewer: String,enabled: Boolean)=mutate({s -> s.copy(shares=s.shares.map {if(it.owner==auth.userId && it.viewer==viewer) it.copy(enabled=enabled) else it}) },{s -> s.shares.any {it.owner==auth.userId && it.viewer==viewer && it.enabled==enabled} }) { rpc("set_location_share",buildJsonObject { put("viewer",viewer); put("enabled",enabled) }) }
    suspend fun ownSharingStatus(): StatusDto = connectionDiagnostics.measure(write=false,operation="own_sharing_status") {
        sessionRead(auth::awaitSession,auth::recoverSession) {
            client.from("sharing_status").select { filter { eq("user_id",auth.userId ?: throw SessionUnavailableException()) } }.decodeSingle<StatusDto>()
        }
    }
    suspend fun sharingRevision(): Long = ownSharingStatus().revision
    suspend fun deviceStatus(session: String,status: DeviceStatus): Boolean = serverRpc("update_device_status",buildJsonObject {
        put("session",session);put("battery",status.batteryLevel?.let(::JsonPrimitive) ?: JsonNull);put("location_enabled",status.locationEnabled)
    }).data=="true"
    suspend fun sharing(active: Boolean,session: String?=null,revision: Long?=null) { serverRpc("set_sharing",buildJsonObject {
        put("active",active); put("session",session?.let(::JsonPrimitive)?:JsonNull)
        put("expected_revision",revision?.let(::JsonPrimitive)?:JsonNull)
    }); refresh() }
    suspend fun publish(session: String,location: UserLocation) {
        serverRpc("publish_location",buildJsonObject {
            put("session",session); put("lat",location.latitude); put("lon",location.longitude); put("acc",location.accuracy)
            put("fix_at",location.recordedAt.toString())
            put("velocity",location.speed?.let(::JsonPrimitive)?:JsonNull); put("heading",location.bearing?.let(::JsonPrimitive)?:JsonNull)
        })
    }
    suspend fun rename(name: String) {
        require(validName(name))
        mutate({it.copy(profile=it.profile?.copy(displayName=name.trim()))},{it.profile?.displayName==name.trim()}) {
            connectionDiagnostics.measure(write=true) {client.from("profiles").update(buildJsonObject {put("display_name",name.trim())}) {filter {eq("id",requireNotNull(auth.userId))}}}
        }
    }
}
