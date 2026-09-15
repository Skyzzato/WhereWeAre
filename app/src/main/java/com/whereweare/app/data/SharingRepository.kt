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

@Singleton class SharingRepository @Inject constructor(private val client: SupabaseClient, private val auth: AuthRepository,private val preferences: PreferencesRepository) {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val connectionDiagnostics=ConnectionDiagnostics()
    val diagnostics=connectionDiagnostics.state
    init { scope.launch {auth.session.map {auth.userId}.distinctUntilChanged().collect {connectionDiagnostics.reset()}} }
    private suspend fun serverRpc(name: String,params: JsonObject=buildJsonObject {})=connectionDiagnostics.measure(
        write=when(name) {
            "app_metadata","lookup_user_by_invite_code" -> false
            "resolve_invite_link" -> params["confirm"]?.jsonPrimitive?.booleanOrNull==true
            else -> true
        }
    ) { client.postgrest.rpc(name,params) }
    suspend fun testConnection() { withTimeout(12_000) {ownSharingStatus()} }
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
        .stateIn(scope,SharingStarted.WhileSubscribed(5_000,0),Snapshot())

    private fun observe(id: String): Flow<Snapshot> = channelFlow {
        var last=Snapshot()
        val cached=preferences.lastFix(id).first()?.let { runCatching { Json.decodeFromString<LocationDto>(it).domain() }.getOrNull() }
        if(cached!=null) { last=last.copy(locations=listOf(cached)); send(last) }
        var realtimeOnline=false
        var realtimeStatus="CONNECTING"
        var disconnectedAt=SystemClock.elapsedRealtime()
        var lastPoll=0L
        var metadataDirty=true
        val generation=AtomicLong(0)
        val signals=Channel<Unit>(Channel.CONFLATED)
        launch { refreshes.collect { metadataDirty=true; signals.trySend(Unit) } }
        launch {while(isActive) {
            delay(5_000)
            val time=SystemClock.elapsedRealtime()
            val unavailable=!realtimeOnline && time-disconnectedAt>=20_000
            if(last.realtimeUnavailable!=unavailable) {last=last.copy(realtimeUnavailable=unavailable);send(last)}
            if(!realtimeOnline && time-lastPoll>=30_000) {lastPoll=time;metadataDirty=true;signals.trySend(Unit)}
        }}
        launch {
            for(signal in signals) {
                try {
                    val readingGeneration=generation.get()
                    val readMetadata=metadataDirty
                    val requestStarted=SystemClock.elapsedRealtime()
                    val metadata=if(readMetadata) serverRpc("app_metadata").decodeAs<MetadataDto>() else null
                    if(metadata!=null) clock=ClockAnchor(Instant.parse(metadata.server_time),requestStarted)
                    val profile=metadata?.profile?.domain() ?: last.profile
                    val names=metadata?.names?.associate {it.user_id to it.display_name} ?: last.names
                    val requests=metadata?.requests?.map {it.domain()} ?: last.requests
                    val shares=metadata?.shares?.map {it.domain()} ?: last.shares
                    val statuses=metadata?.statuses?.map {it.domain()} ?: last.statuses
                    val locations=connectionDiagnostics.measure(write=false) {client.from("latest_locations").select().decodeList<LocationDto>().map {it.domain()}}
                    val contacts=metadata?.contacts?.map {it.domain()}?.associateBy {it.id} ?: last.contacts
                    val groups=metadata?.groups?.map {it.domain()} ?: last.groups
                    val members=metadata?.members?.map {it.domain()} ?: last.members
                    val groupRequests=metadata?.group_requests ?: last.groupRequests
                    val meetings=metadata?.meetings ?: last.meetings
                    if(readingGeneration!=generation.get()) { signals.trySend(Unit); continue }
                    if(readMetadata) metadataDirty=false
                    // REST success means data is current enough to display; Realtime status is tracked separately.
                    last=Snapshot(profile,names,requests,shares,statuses,locations,loading=false,offline=false,contacts=contacts,groups=groups,members=members,groupRequests=groupRequests,meetings=meetings,syncFailed=false,realtimeUnavailable=last.realtimeUnavailable,savedPeople=metadata?.saved_people?.toSet() ?: last.savedPeople)
                    locations.firstOrNull { it.userId==id }?.let { saveOwn(it) }
                    send(last)
                } catch(e: CancellationException) { throw e
                } catch(e: Exception) {
                    if(com.whereweare.app.BuildConfig.DEBUG) Log.w("WhereWeAreRealtime","REST refresh failed: ${e.javaClass.simpleName}")
                    last=last.copy(loading=false,offline=true,syncFailed=true); send(last)
                    delay(5_000); signals.trySend(Unit)
                }
            }
        }
        // Profiles and requests remain usable even if the WebSocket is temporarily unavailable.
        signals.trySend(Unit)
        while(isActive) {
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
                                    last=last.copy(locations=last.locations.filter { it.userId==id }); send(last)
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
                        if(realtimeOnline) { metadataDirty=true; signals.trySend(Unit) }
                        // CONNECTING/RECONNECTING is not an offline REST failure; avoid bootstrap flicker.
                    } }
                    launch { channel.systemFlow().collect { event ->
                        if(com.whereweare.app.BuildConfig.DEBUG) Log.d("WhereWeAreRealtime","system status=${event.status}")
                        check(event.status!="error") { "realtime_unavailable" }
                    } }
                    channel.subscribe(blockUntilSubscribed=true)
                    signals.trySend(Unit)
                    awaitCancellation()
                }
            } catch(e: CancellationException) { throw e
            } catch(e: Exception) { if(realtimeOnline) disconnectedAt=SystemClock.elapsedRealtime();realtimeOnline=false; realtimeStatus="ERROR"; if(com.whereweare.app.BuildConfig.DEBUG) Log.w("WhereWeAreRealtime","retry in 5s: ${e.javaClass.simpleName}");delay(5_000)
            } finally { connectionDiagnostics.realtime(false); withContext(NonCancellable) { runCatching { client.realtime.removeChannel(channel) } } }
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
    suspend fun remove(person: String)=mutate({ s -> s.copy(shares=s.shares.filter { it.owner!=person && it.viewer!=person },savedPeople=s.savedPeople-person) },{ s -> person !in s.savedPeople && s.shares.none { it.owner==person || it.viewer==person } }) { rpc("remove_connection",buildJsonObject { put("other_user_id",person) }) }
    suspend fun savePerson(person: String)=mutate({it.copy(savedPeople=it.savedPeople+person)},{person in it.savedPeople}) {rpc("save_group_person",buildJsonObject {put("person",person)})}
    suspend fun dismiss(id: String)=rpc("dismiss_request",buildJsonObject { put("request_id",id) })
    suspend fun createGroup(name: String,emoji: String) { require(validGroupName(name)); rpc("create_group",buildJsonObject { put("group_name",name.trim()); put("group_emoji",emoji) }) }
    suspend fun joinGroup(code: String) { check(serverRpc("join_group",buildJsonObject { put("code",code) }).data!="null") { "group_not_found" }; refresh() }
    suspend fun removeMember(group: String,member: String)=mutate({ s -> s.copy(members=s.members.filterNot { it.groupId==group && it.userId==member }) },{ s -> s.members.none { it.groupId==group && it.userId==member } }) { rpc("remove_group_member",buildJsonObject { put("gid",group); put("member",member) }) }
    suspend fun deleteGroup(group: String)=mutate({s -> s.copy(groups=s.groups.filterNot { it.id==group }) },{s -> s.groups.none {it.id==group} }) { rpc("delete_group",buildJsonObject { put("gid",group) }) }
    suspend fun renameGroup(group: String,name: String)=mutate({ s -> s.copy(groups=s.groups.map { if(it.id==group) it.copy(name=name) else it }) },{s -> s.groups.any {it.id==group && it.name==name} }) { rpc("rename_group",buildJsonObject { put("gid",group); put("group_name",name) }) }
    suspend fun editGroup(group: String,name: String,emoji: String)=mutate({s -> s.copy(
        groups=s.groups.map {if(it.id==group) it.copy(name=name.trim(),emoji=emoji) else it},
        groupRequests=s.groupRequests.map {if(it.group_id==group) it.copy(group_name=name.trim(),group_emoji=emoji) else it})},
        {s -> s.groups.any {it.id==group && it.name==name.trim() && it.emoji==emoji}}) {
        rpc("edit_group",buildJsonObject {put("gid",group);put("group_name",name.trim());put("group_emoji",emoji)})
    }
    suspend fun cancelGroupInvitation(id: String)=mutate({s -> s.copy(groupRequests=s.groupRequests.filterNot {it.id==id})},
        {s -> s.groupRequests.none {it.id==id && it.status=="pending"}}) {
        rpc("cancel_group_invitation",buildJsonObject {put("request_id",id)})
    }
    suspend fun groupSharing(group: String,enabled: Boolean)=mutate({s -> s.copy(members=s.members.map {if(it.groupId==group && it.userId==auth.userId) it.copy(sharingEnabled=enabled) else it}) },{s -> s.members.any {it.groupId==group && it.userId==auth.userId && it.sharingEnabled==enabled} }) { rpc("set_group_sharing",buildJsonObject { put("gid",group); put("enabled",enabled) }) }
    suspend fun inviteMember(group: String,person: String)=rpc("invite_group_member",buildJsonObject { put("gid",group); put("person",person) })
    suspend fun respondGroup(request: String,accept: Boolean)=mutate({s -> s.copy(groupRequests=s.groupRequests.map {if(it.id==request) it.copy(status=if(accept) "accepted" else "rejected") else it}) },{s -> s.groupRequests.none {it.id==request && it.status=="pending"} }) { rpc("respond_group_request",buildJsonObject { put("request_id",request); put("accept",accept) }) }
    suspend fun createMeeting(id: String,lat: Double,lon: Double,all: Boolean,people: Set<String>,groups: Set<String>,styleId: Int=1)=rpc("create_meeting_styled",buildJsonObject {
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
    suspend fun ownSharingStatus(): StatusDto = connectionDiagnostics.measure(write=false) {client.from("sharing_status").select { filter { eq("user_id",requireNotNull(auth.userId)) } }.decodeSingle<StatusDto>()}
    suspend fun sharingRevision(): Long = ownSharingStatus().revision
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
