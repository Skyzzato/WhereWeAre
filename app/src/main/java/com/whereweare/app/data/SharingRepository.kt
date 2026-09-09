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

@Singleton class SharingRepository @Inject constructor(private val client: SupabaseClient, private val auth: AuthRepository) {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val refreshes=MutableSharedFlow<Unit>(extraBufferCapacity=1)
    private data class ClockAnchor(val server: Instant,val elapsed: Long)
    @Volatile private var clock=ClockAnchor(Instant.now(),SystemClock.elapsedRealtime())
    fun now(): Instant = clock.let { it.server.plusMillis(SystemClock.elapsedRealtime()-it.elapsed) }
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<Snapshot> = auth.session.map { auth.userId }.distinctUntilChanged().flatMapLatest { id ->
        if(id==null) flowOf(Snapshot(loading=false)) else observe(id)
    }.stateIn(scope,SharingStarted.WhileSubscribed(5_000,0),Snapshot())

    private fun observe(id: String): Flow<Snapshot> = channelFlow {
        var last=Snapshot()
        var realtimeOnline=false
        val generation=AtomicLong(0)
        val signals=Channel<Unit>(Channel.CONFLATED)
        launch { refreshes.collect { signals.trySend(Unit) } }
        launch {
            for(signal in signals) {
                try {
                    val readingGeneration=generation.get()
                    val requestStarted=SystemClock.elapsedRealtime()
                    val time=client.postgrest.rpc("server_time").decodeAs<String>()
                    // Conservatively include request latency: never extend the two-hour window.
                    clock=ClockAnchor(Instant.parse(time),requestStarted)
                    val profile=client.from("profiles").select().decodeSingle<ProfileDto>().domain()
                    val names=client.postgrest.rpc("contact_names").decodeList<NameDto>().associate { it.user_id to it.display_name }
                    val requests=client.from("share_requests").select().decodeList<RequestDto>().map { it.domain() }
                    val shares=client.from("location_shares").select().decodeList<ShareDto>().map { it.domain() }
                    val statuses=client.from("sharing_status").select().decodeList<StatusDto>().map { it.domain() }
                    val locations=client.from("latest_locations").select().decodeList<LocationDto>().map { it.domain() }
                    if(readingGeneration!=generation.get()) { signals.trySend(Unit); continue }
                    last=Snapshot(profile,names,requests,shares,statuses,locations,loading=false,offline=!realtimeOnline)
                    send(last)
                } catch(e: CancellationException) { throw e
                } catch(_: Exception) {
                    last=last.copy(loading=false,offline=true); send(last)
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
                    listOf("latest_locations","share_requests","location_shares","sharing_status").forEach { tableName ->
                        // DELETE events don't apply row RLS in Supabase. Expiry is handled locally;
                        // explicit stop/revoke always uses UPDATE and remains observable under RLS.
                        val changes=merge(
                            channel.postgresChangeFlow<PostgresAction.Insert>(schema="public") { table=tableName },
                            channel.postgresChangeFlow<PostgresAction.Update>(schema="public") { table=tableName }
                        )
                        launch {
                            changes.collect {
                                if(tableName=="location_shares" || tableName=="sharing_status") {
                                    generation.incrementAndGet()
                                    // Invalidate first: an older in-memory fix must not survive revocation.
                                    last=last.copy(locations=emptyList()); send(last)
                                }
                                signals.trySend(Unit)
                            }
                        }
                    }
                    launch { channel.status.collect { status ->
                        realtimeOnline=status==RealtimeChannel.Status.SUBSCRIBED
                        if(realtimeOnline) signals.trySend(Unit)
                        else { last=last.copy(offline=true); send(last) }
                    } }
                    launch { channel.systemFlow().collect { event ->
                        check(event.status!="error") { "realtime_unavailable" }
                    } }
                    channel.subscribe(blockUntilSubscribed=true)
                    signals.trySend(Unit)
                    awaitCancellation()
                }
            } catch(e: CancellationException) { throw e
            } catch(_: Exception) { realtimeOnline=false; last=last.copy(loading=false,offline=true); send(last); delay(5_000)
            } finally { withContext(NonCancellable) { runCatching { client.realtime.removeChannel(channel) } } }
        }
    }.flowOn(Dispatchers.IO.limitedParallelism(1))
    fun refresh() { refreshes.tryEmit(Unit) }
    suspend fun lookup(code: String): UserProfile? = client.postgrest.rpc("lookup_user_by_invite_code",buildJsonObject { put("code",normalizeInviteCode(code)) }).decodeList<LookupDto>().firstOrNull()?.domain()
    suspend fun sendRequest(code: String) {
        val result=client.postgrest.rpc("send_share_request",buildJsonObject { put("code",normalizeInviteCode(code)) }).data
        check(result!="null") { "not_found" }; refresh()
    }
    suspend fun respond(id: String,accept: Boolean) { client.postgrest.rpc("respond_to_share_request",buildJsonObject { put("request_id",id); put("accept",accept) }); refresh() }
    suspend fun cancel(id: String) { client.postgrest.rpc("cancel_share_request",buildJsonObject { put("request_id",id) }); refresh() }
    suspend fun permission(viewer: String,enabled: Boolean) { client.postgrest.rpc("set_location_share",buildJsonObject { put("viewer",viewer); put("enabled",enabled) }); refresh() }
    suspend fun sharingRevision(): Long = client.from("sharing_status").select { filter { eq("user_id",requireNotNull(auth.userId)) } }.decodeSingle<StatusDto>().revision
    suspend fun sharing(active: Boolean,session: String?=null,revision: Long?=null) { client.postgrest.rpc("set_sharing",buildJsonObject {
        put("active",active); put("session",session?.let(::JsonPrimitive)?:JsonNull)
        put("expected_revision",revision?.let(::JsonPrimitive)?:JsonNull)
    }); refresh() }
    suspend fun publish(session: String,location: UserLocation) {
        client.postgrest.rpc("publish_location",buildJsonObject {
            put("session",session); put("lat",location.latitude); put("lon",location.longitude); put("acc",location.accuracy)
            put("fix_at",location.recordedAt.toString())
            put("velocity",location.speed?.let(::JsonPrimitive)?:JsonNull); put("heading",location.bearing?.let(::JsonPrimitive)?:JsonNull)
        })
    }
    suspend fun rename(name: String) {
        require(validName(name))
        client.from("profiles").update(buildJsonObject { put("display_name",name.trim()) }) { filter { eq("id",requireNotNull(auth.userId)) } }
        refresh()
    }
}
