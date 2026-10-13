package com.whereweare.app

import android.app.Application
import com.whereweare.app.data.*
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.realtime.*
import io.github.jan.supabase.realtime.websocket.*
import io.ktor.websocket.Frame
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

/** Runs the real repository and Realtime SDK against a deterministic socket. */
@OptIn(SupabaseInternal::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class RealtimeRecoveryTest {
    @Test fun serverChannelCloseReopensSubscriptionWithoutRestartingApp() = runBlocking {
        val socket=ServerSocket()
        val client=createSupabaseClient("https://example.supabase.co","test") {
            install(Realtime) {
                accessToken={null}
                websocketFactory=object: RealtimeWebsocketFactory {
                    override suspend fun create(url: String): RealtimeWebsocket=socket
                }
            }
        }
        val auth=mock(AuthRepository::class.java)
        `when`(auth.userId).thenReturn("realtime-test-user")
        `when`(auth.session).thenReturn(MutableStateFlow<SessionStatus>(SessionStatus.Initializing))
        val preferences=mock(PreferencesRepository::class.java)
        `when`(preferences.lastFix("realtime-test-user")).thenReturn(flowOf(null))
        val network=mock(NetworkMonitor::class.java)
        `when`(network.state).thenReturn(MutableStateFlow(NetworkConnection(true,NetworkTransport.WIFI)))
        `when`(network.online).thenReturn(MutableStateFlow(true))
        val repository=SharingRepository(client,auth,preferences,network)
        val collector=launch {repository.state.collect()}
        try {
            withTimeout(5_000) {repository.diagnostics.first {it.realtimeConnected==true}}
            assertEquals(1,socket.joins.value)
            socket.closeChannel()
            withTimeout(8_000) {socket.joins.first {it>=2}}
            withTimeout(5_000) {repository.diagnostics.first {it.realtimeConnected==true}}
            assertEquals("The old channel must be removed before its replacement",1,client.realtime.subscriptions.size)
            assertEquals("Each replacement must register all five INSERT/UPDATE pairs",10,socket.bindingCount)
        } finally {
            collector.cancelAndJoin()
            client.close()
        }
    }

    private class ServerSocket: RealtimeWebsocket {
        private val incoming=Channel<Frame>(Channel.UNLIMITED)
        private val refs=AtomicInteger()
        private var active=true
        private var topic=""
        private var joinRef: String?=null
        val joins=MutableStateFlow(0)
        var bindingCount=0
            private set
        override val hasIncomingMessages get()=active
        override fun makeRef()=refs.getAndIncrement().toString()
        override suspend fun receive(): Frame=incoming.receive()
        override suspend fun send(message: RealtimeMessage,vsn: RealtimeProtocolVersion) {
            if(message.event=="phx_join") {
                topic=message.topic
                joinRef=message.joinRef
                val bindings=message.payload.getValue("config").jsonObject.getValue("postgres_changes").jsonArray
                bindingCount=bindings.size
                val response=buildJsonObject {
                    put("status","ok")
                    putJsonObject("response") {
                        put("postgres_changes",JsonArray(bindings.mapIndexed {i,binding->
                            JsonObject(binding.jsonObject+("id" to JsonPrimitive(i+1)))
                        }))
                    }
                }
                incoming.send(frame("phx_reply",response,message.ref))
                joins.value++
            } else if(message.event=="phx_leave") {
                incoming.send(frame("phx_reply",buildJsonObject {put("status","ok")},message.ref))
            }
        }
        suspend fun closeChannel() {incoming.send(frame("phx_close",buildJsonObject {}))}
        private fun frame(event: String,payload: JsonObject,ref: String?=null)=Frame.Text(buildJsonArray {
            add(joinRef);add(ref);add(topic);add(event);add(payload)
        }.toString())
        override suspend fun send(data: ByteArray)=error("Unexpected binary broadcast")
        override suspend fun blockUntilDisconnect() {while(active) delay(10)}
        override fun disconnect() {active=false;incoming.close()}
    }
}
