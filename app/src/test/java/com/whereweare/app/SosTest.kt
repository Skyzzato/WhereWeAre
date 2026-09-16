package com.whereweare.app

import com.whereweare.app.data.*
import com.whereweare.app.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SosTest {
    @Test fun confirmedOnlyAfterRegistrationAck()=runTest {
        val ack=CompletableDeferred<Unit>()
        val states=mutableListOf<SosSendState>()
        val task=launch {dispatchSos({null},{ack.await()},states::add)}
        runCurrent()
        assertEquals(listOf(SosSendState.SENDING),states)
        ack.complete(Unit);task.join()
        assertEquals(listOf(SosSendState.SENDING,SosSendState.CONFIRMED),states)
    }
    @Test fun failedRegistrationNeverClaimsSuccessAndMissingGpsStillSends()=runTest {
        val states=mutableListOf<SosSendState>()
        dispatchSos({null},{throw java.io.IOException()},states::add)
        assertEquals(SosSendState.UNKNOWN,states.last())
        var registered=false
        dispatchSos({throw java.io.IOException()},{assertNull(it);registered=true},states::add)
        assertTrue(registered)
        assertEquals(SosSendState.CONFIRMED,states.last())
        assertNull(SosPayload("lost").location("event"))
        assertFalse(Snapshot().nearbySosAvailable)
    }
    @Test fun countdownWaitsFiveSecondsAndCancellationPreventsSend()=runTest {
        var sends=0
        val cancelled=launch {sosCountdown({}, {sends++})}
        advanceTimeBy(4999);runCurrent();assertEquals(0,sends)
        cancelled.cancelAndJoin();advanceTimeBy(1000);runCurrent();assertEquals(0,sends)
        val completed=launch {sosCountdown({}, {sends++})}
        advanceTimeBy(4999);runCurrent();assertEquals(0,sends)
        advanceTimeBy(1);runCurrent();completed.join();assertEquals(1,sends)
    }
}
