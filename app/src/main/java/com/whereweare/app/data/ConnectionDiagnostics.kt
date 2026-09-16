package com.whereweare.app.data

import android.os.SystemClock
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant

data class ConnectionDiagnosticState(
    val epoch: Long=0,
    val failure: String?=null,
    val failures: Map<String,String> = emptyMap(),
    val serverReachable: Boolean?=null,
    val sessionVerified: Boolean?=null,
    val lastRead: Instant?=null,
    val lastWrite: Instant?=null,
    val latencyMillis: Long?=null,
    val inFlight: Int=0,
    val realtimeConnected: Boolean?=null
)

/** Observes existing repository requests; it never starts its own network polling. */
class ConnectionDiagnostics(
    private val elapsed: ()->Long={SystemClock.elapsedRealtime()},
    private val now: ()->Instant={Instant.now()}
) {
    private val mutable=MutableStateFlow(ConnectionDiagnosticState())
    val state=mutable.asStateFlow()
    fun reset() { mutable.update {ConnectionDiagnosticState(epoch=it.epoch+1)} }
    fun realtime(connected: Boolean?) {mutable.update {it.copy(realtimeConnected=connected)}}
    suspend fun <T> measure(write: Boolean,operation: String="request",authenticated: Boolean=true,request: suspend ()->T): T {
        val epoch=mutable.value.epoch
        val started=elapsed()
        val correlation=java.util.UUID.randomUUID().toString().take(8)
        mutable.update {if(it.epoch==epoch) it.copy(inFlight=it.inFlight+1) else it}
        var outcome="ok"
        try {
            val result=request()
            mutable.update { if(it.epoch!=epoch) it else it.copy(failures=it.failures-operation,failure=(it.failures-operation).values.firstOrNull(),serverReachable=true,sessionVerified=if(authenticated) true else it.sessionVerified,
                lastRead=if(write) it.lastRead else now(),lastWrite=if(write) now() else it.lastWrite,
                latencyMillis=(elapsed()-started).coerceAtLeast(0)) }
            return result
        } catch(e: Exception) {
            outcome=if(e is CancellationException && e !is TimeoutCancellationException) "cancelled" else connectionFailure(e)
            if(e !is CancellationException || e is TimeoutCancellationException) {
                mutable.update { if(it.epoch!=epoch) it else it.copy(
                    failure=connectionFailure(e),failures=it.failures+(operation to connectionFailure(e)),
                    serverReachable=if(connectionFailure(e)=="unreachable") false else if(e is RestException || e is kotlinx.serialization.SerializationException) true else it.serverReachable,
                    sessionVerified=if(e is RestException && e.statusCode==401) false else it.sessionVerified,
                    latencyMillis=null) }
            }
            throw e
        } finally {
            if(com.whereweare.app.BuildConfig.DEBUG) runCatching {android.util.Log.d("WhereWeAreConnection","operation=$operation correlation=$correlation duration_ms=${elapsed()-started} category=$outcome")}
            mutable.update {if(it.epoch==epoch) it.copy(inFlight=(it.inFlight-1).coerceAtLeast(0)) else it}
        }
    }
}

fun connectionFailure(e: Exception): String = when {
    e is RestException && e.statusCode==401 -> "session"
    e is RestException && e.statusCode==403 -> "forbidden"
    e is RestException -> "service"
    e is kotlinx.serialization.SerializationException || e is java.time.format.DateTimeParseException -> "response"
    e is java.io.IOException || e is TimeoutCancellationException -> "unreachable"
    else -> "service"
}
fun retryableRead(e: Exception): Boolean = when(e) {
    is RestException -> e.statusCode==408 || e.statusCode==429 || e.statusCode>=500
    is TimeoutCancellationException, is java.io.IOException -> true
    else -> false
}
