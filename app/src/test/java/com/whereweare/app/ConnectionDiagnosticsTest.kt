package com.whereweare.app

import com.whereweare.app.data.ConnectionDiagnostics
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*
import java.io.IOException
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionDiagnosticsTest {
    private val base=Instant.parse("2026-09-15T00:00:00Z")
    private fun TestScope.metrics()=ConnectionDiagnostics({testScheduler.currentTime},{base.plusMillis(testScheduler.currentTime)})

    @Test fun onlyAcknowledgedRequestsUpdateReadAndWriteTimes() = runTest {
        val metrics=metrics()
        assertNull(metrics.state.value.serverReachable)
        metrics.measure(false) {delay(40)}
        assertEquals(base.plusMillis(40),metrics.state.value.lastRead)
        assertNull(metrics.state.value.lastWrite)
        assertEquals(40L,metrics.state.value.latencyMillis)
        metrics.measure(true) {delay(60)}
        assertEquals(base.plusMillis(100),metrics.state.value.lastWrite)
        assertEquals(base.plusMillis(40),metrics.state.value.lastRead)
        assertEquals(true,metrics.state.value.sessionVerified)
        assertEquals(0,metrics.state.value.inFlight)
    }

    @Test fun failureDoesNotInventWriteConfirmationOrExposeErrorDetails() = runTest {
        val metrics=metrics()
        try {metrics.measure(true) {throw IOException("sensitive-token-and-endpoint")};fail()}
        catch(_: IOException) {}
        assertNull(metrics.state.value.lastWrite)
        assertEquals(false,metrics.state.value.serverReachable)
        assertNull(metrics.state.value.sessionVerified)
        assertFalse(metrics.state.value.toString().contains("sensitive"))
        assertEquals(0,metrics.state.value.inFlight)
    }

    @Test fun timeoutIsFailureButLifecycleCancellationIsNotServerFailure() = runTest {
        val metrics=metrics()
        try {withTimeout(10) {metrics.measure(false) {delay(100)}};fail()}
        catch(_: TimeoutCancellationException) {}
        assertEquals(false,metrics.state.value.serverReachable)
        metrics.measure(false) {Unit}
        val lastRead=metrics.state.value.lastRead
        val job=launch {metrics.measure(true) {awaitCancellation()}}
        runCurrent()
        assertEquals(1,metrics.state.value.inFlight)
        job.cancelAndJoin()
        assertEquals(true,metrics.state.value.serverReachable)
        assertEquals(lastRead,metrics.state.value.lastRead)
        assertNull(metrics.state.value.lastWrite)
        assertEquals(0,metrics.state.value.inFlight)
    }

    @Test fun responseFromPreviousAccountCannotRepopulateResetDiagnostics() = runTest {
        val metrics=metrics()
        val finish=CompletableDeferred<Unit>()
        val job=launch {metrics.measure(true) {finish.await()}}
        runCurrent()
        assertEquals(1,metrics.state.value.inFlight)
        metrics.reset()
        finish.complete(Unit);job.join()
        assertNull(metrics.state.value.lastWrite)
        assertNull(metrics.state.value.serverReachable)
        assertEquals(0,metrics.state.value.inFlight)
    }

    @Test fun authenticationRejectionStillProvesServerReachability() = runTest {
        val metrics=metrics()
        val rejected=mock(RestException::class.java)
        `when`(rejected.statusCode).thenReturn(401)
        try {metrics.measure(false) {throw rejected};fail()}
        catch(_: RestException) {}
        assertEquals(true,metrics.state.value.serverReachable)
        assertEquals(false,metrics.state.value.sessionVerified)
        assertNull(metrics.state.value.lastRead)
    }

    @Test fun realtimeDisconnectionDoesNotTurnSuccessfulRestIntoOffline() = runTest {
        val metrics=metrics()
        metrics.measure(false) {Unit}
        metrics.realtime(false)
        assertEquals(true,metrics.state.value.serverReachable)
        assertEquals(false,metrics.state.value.realtimeConnected)
        assertNotNull(metrics.state.value.lastRead)
    }
    @Test fun lateFailureCannotOverwriteNewerSuccessForSameOperation()=runTest {
        val metrics=metrics()
        val finish=CompletableDeferred<Unit>()
        val old=launch {try {metrics.measure(false,"own_sharing_status") {finish.await();throw IOException()}} catch(_: IOException) {}}
        runCurrent()
        metrics.measure(false,"own_sharing_status") {Unit}
        finish.complete(Unit);old.join()
        assertNull(metrics.state.value.failure)
        assertEquals(true,metrics.state.value.serverReachable)
        assertEquals(0,metrics.state.value.inFlight)
    }

}
