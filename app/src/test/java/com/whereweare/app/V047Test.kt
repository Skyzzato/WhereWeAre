package com.whereweare.app

import com.whereweare.app.data.*
import com.whereweare.app.domain.*
import com.whereweare.app.ui.*
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*
import java.io.IOException
import androidx.compose.ui.graphics.luminance

@OptIn(ExperimentalCoroutinesApi::class)
class V047Test {
    @Test fun coldStartWaitsForRestorationBeforeReading()=runTest {
        val restored=CompletableDeferred<Unit>()
        var reads=0
        val result=async { sessionRead({restored.await()},{fail()}) {++reads} }
        runCurrent();assertEquals(0,reads)
        restored.complete(Unit)
        assertEquals(1,result.await())
    }
    @Test fun expiredTokenRenewsOnceAndReturnsFreshRead()=runTest {
        val unauthorized=mock(RestException::class.java)
        `when`(unauthorized.statusCode).thenReturn(401)
        var renewals=0;var reads=0
        val value=sessionRead({},{renewals++}) {if(reads++==0) throw unauthorized else "fresh"}
        assertEquals("fresh",value);assertEquals(1,renewals);assertEquals(2,reads)
    }
    @Test fun permanentUnauthorizedDoesNotLoop()=runTest {
        val unauthorized=mock(RestException::class.java)
        `when`(unauthorized.statusCode).thenReturn(401)
        var renewals=0;var reads=0
        try {sessionRead({},{renewals++}) {reads++;throw unauthorized};fail()} catch(_: RestException) {}
        assertEquals(1,renewals);assertEquals(2,reads)
    }
    @Test fun offlineRecoveryAndRetryExhaustionAreBounded()=runTest {
        var reads=0
        assertEquals("ok",sessionRead({},{fail()}) {if(reads++<2) throw IOException() else "ok"})
        assertEquals(3,reads)
        reads=0
        try {sessionRead({},{fail()}) {reads++;throw IOException()};fail()} catch(_: IOException) {}
        assertEquals(3,reads)
    }
    @Test fun lifecycleCancellationNeverBecomesServerFailureOrRetry()=runTest {
        var reads=0
        val job=launch {sessionRead({},{fail()}) {reads++;awaitCancellation()}}
        runCurrent();job.cancelAndJoin();advanceUntilIdle()
        assertEquals(1,reads)
    }
    @Test fun sessionRestorationHasAnOverallDeadline()=runTest {
        try {sessionRead({awaitCancellation()},{fail()}) {fail()};fail()} catch(_: TimeoutCancellationException) {}
        assertEquals(30_000L,testScheduler.currentTime)
    }
    @Test fun permissionAndSessionFailuresAreNotServerFailures() {
        assertEquals("session",connectionFailure(SessionUnavailableException()))
        assertEquals("permission",connectionFailure(SecurityException()))
        assertEquals(R.string.location_disabled,syncFailureMessage(Snapshot(syncError="location")))
        assertEquals(R.string.sync_waiting,trackingFailureMessage(com.whereweare.app.service.TrackingState(starting=true,waiting=true)))
    }
    @Test fun avatarChoicesKeepTheirSavedIdentifiersAndHaveDistinctDiameters() {
        val choices=listOf(.75f,1f,1.25f,1.5f)
        assertEquals(listOf(24f,36f,52f,72f),choices.map {mapAvatarDp(it)})
        assertEquals(listOf(40.5f,60.75f,87.75f,121.5f),choices.map {mapAvatarDp(it,true)})
    }
    @Test fun semanticGreenHasUsableContrastOnEveryThemeActionContainer() {
        fun contrast(a: androidx.compose.ui.graphics.Color,b: androidx.compose.ui.graphics.Color): Float {
            val x=a.luminance(); val y=b.luminance()
            return (maxOf(x,y)+.05f)/(minOf(x,y)+.05f)
        }
        for(theme in listOf("default","ocean","sunset","lavender","graphite","dark")) {
            val colors=appColors(theme)
            assertTrue(theme,contrast(sharingActionColor(theme=="dark"),colors.primaryContainer)>=3f)
        }
    }
}
