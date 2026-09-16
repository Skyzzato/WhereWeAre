package com.whereweare.app

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.whereweare.app.data.*
import com.whereweare.app.service.SharingController
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.io.IOException
import java.time.Duration
import kotlin.coroutines.startCoroutine

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class SharingRecoveryTest {
    private class MemoryStore: DataStore<Preferences> {
        override val data=MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences)->Preferences): Preferences = transform(data.value).also {data.value=it}
    }

    private suspend fun TestScope.scenario(test: suspend TestScope.(SharingController,PreferencesRepository,SharingRepository,CoroutineScope,()->Unit)->Unit) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val service=CoroutineScope(SupervisorJob()+StandardTestDispatcher(testScheduler))
        try {
            val context=RuntimeEnvironment.getApplication()
            val prefs=PreferencesRepository(MemoryStore(),context)
            val repo=mock(SharingRepository::class.java)
            val auth=mock(AuthRepository::class.java)
            val location=mock(LocationRepository::class.java)
            val boot=mock(BootstrapRepository::class.java)
            `when`(auth.userId).thenReturn("owner")
            `when`(location.hasPermission()).thenReturn(true)
            `when`(location.enabled()).thenReturn(true)
            `when`(location.deviceStatus()).thenReturn(com.whereweare.app.domain.DeviceStatus(50,true))
            `when`(location.fixes(true,60)).thenReturn(emptyFlow())
            `when`(boot.state).thenReturn(MutableStateFlow(BootstrapState(BootstrapGate.READY)))
            `when`(repo.sharingRevision()).thenReturn(7L)
            `when`(repo.ownSharingStatus()).thenAnswer {
                val saved=runBlocking { prefs.trackingSession.first() }
                StatusDto("owner",true,8,saved?.sessionId)
            }
            val controller=SharingController(context,repo,auth,prefs,location,boot)
            runCurrent()
            test(controller,prefs,repo,service,{})
        } finally {
            service.cancel()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test fun failedStartDoesNotClaimOnAndRetryUsesPersistedRevision() = runTest {
        scenario { controller,prefs,repo,service,stop ->
            var available=false
            doAnswer { if(!available) throw IOException("offline"); Unit }.`when`(repo).sharing(eq(true),any(),any())
            controller.attach(service,stopService=stop)
            runCurrent()
            assertFalse(controller.state.value.active)
            assertTrue(controller.state.value.starting)
            assertTrue(controller.state.value.waiting)
            val saved=prefs.trackingSession.first()!!
            assertEquals(7L,saved.revision)
            available=true
            advanceTimeBy(3_000);runCurrent()
            assertTrue(controller.state.value.active)
            assertFalse(controller.state.value.starting)
            verify(repo,times(2)).sharing(true,saved.sessionId,7L)
            verify(repo,times(1)).sharingRevision()
        }
    }

    @Test fun nonTransientFailureStopsInsteadOfRetryingEveryThreeSeconds() = runTest {
        // This controller test verifies durable stop intent, not execution of the worker.
        // A delegate avoids starting real scheduler/SQLite threads in the rendering JVM.
        val scheduler=mock(androidx.work.impl.WorkManagerImpl::class.java)
        androidx.work.impl.WorkManagerImpl.setDelegate(scheduler)
        try {scenario { controller,prefs,repo,service,_ ->
            doAnswer {throw IllegalStateException("invalid server contract")}.`when`(repo).sharing(eq(true),any(),any())
            var stopped=false
            controller.attach(service,stopService={stopped=true})
            runCurrent()
            advanceTimeBy(60_000);runCurrent()
            verify(repo,times(1)).sharing(eq(true),any(),any())
            assertTrue(stopped)
            assertFalse(controller.state.value.active)
            assertFalse(controller.state.value.retrying)
            assertEquals("service",controller.state.value.failure)
            assertEquals("owner",prefs.pendingStop.first())
            val scheduled=mockingDetails(scheduler).invocations.single {it.method.name=="enqueueUniqueWork"}
            assertEquals("stop-owner",scheduled.arguments[0])
            assertEquals(androidx.work.ExistingWorkPolicy.APPEND_OR_REPLACE,scheduled.arguments[1])
        }} finally {androidx.work.impl.WorkManagerImpl.setDelegate(null)}
    }

    @Test fun transientFailureUsesBackoffWithoutClaimingAnActiveAttemptWhileWaiting() = runTest {
        scenario { controller,_,repo,service,stop ->
            doAnswer {throw IOException("offline")}.`when`(repo).sharing(eq(true),any(),any())
            controller.attach(service,stopService=stop)
            runCurrent()
            verify(repo,times(1)).sharing(eq(true),any(),any())
            assertFalse(controller.state.value.retrying)
            advanceTimeBy(1_999);runCurrent()
            verify(repo,times(1)).sharing(eq(true),any(),any())
            advanceTimeBy(1);runCurrent()
            verify(repo,times(2)).sharing(eq(true),any(),any())
            advanceTimeBy(3_999);runCurrent()
            verify(repo,times(2)).sharing(eq(true),any(),any())
            advanceTimeBy(1);runCurrent()
            verify(repo,times(3)).sharing(eq(true),any(),any())
        }
    }

    @Test fun stickyRecreationPreservesSessionAndDoesNotCallOldStopCallback() = runTest {
        scenario { controller,prefs,repo,service,_ ->
            var oldStops=0
            controller.attach(service,stopService={oldStops++})
            runCurrent()
            val original=prefs.trackingSession.first()!!
            service.cancel();runCurrent()
            assertEquals(original,prefs.trackingSession.first())
            assertEquals(0,oldStops)
            assertFalse(controller.state.value.active)
            val next=CoroutineScope(SupervisorJob()+StandardTestDispatcher(testScheduler))
            try {
                controller.attach(next,restarting=true,stopService={})
                runCurrent()
                assertTrue(controller.state.value.active)
                assertEquals(original,prefs.trackingSession.first())
                verify(repo,times(2)).sharing(true,original.sessionId,7L)
            } finally { next.cancel();runCurrent() }
        }
    }

    @Test fun remoteStopIsObservedWithoutAnyNewGpsFixAndNeverStopsReplacementSession() = runTest {
        scenario { controller,prefs,repo,service,_ ->
            var stops=0
            controller.attach(service,stopService={stops++})
            runCurrent()
            assertTrue(controller.state.value.active)
            `when`(repo.ownSharingStatus()).thenReturn(StatusDto("owner",true,9,"another-device"))
            ShadowSystemClock.advanceBy(Duration.ofSeconds(31))
            advanceTimeBy(31_000);runCurrent()
            assertFalse(controller.state.value.active)
            assertNull(prefs.trackingSession.first())
            assertNull(prefs.pendingStop.first())
            assertEquals(1,stops)
            verify(repo,never()).sharing(eq(false),any(),any())
        }
    }

    @Test fun networkLossPreservesConsentAndRecoveryDoesNotIssueAnotherStart() = runTest {
        scenario { controller,prefs,repo,service,stop ->
            controller.attach(service,stopService=stop);runCurrent()
            val saved=prefs.trackingSession.first()!!
            doAnswer { throw IOException("unreachable") }.`when`(repo).ownSharingStatus()
            ShadowSystemClock.advanceBy(Duration.ofSeconds(31))
            advanceTimeBy(31_000);runCurrent()
            assertTrue(controller.state.value.waiting)
            assertEquals(saved,prefs.trackingSession.first())
            doReturn(StatusDto("owner",true,8,saved.sessionId)).`when`(repo).ownSharingStatus()
            advanceTimeBy(3_000);runCurrent()
            assertTrue(controller.state.value.active)
            assertFalse(controller.state.value.waiting)
            verify(repo,times(1)).sharing(true,saved.sessionId,7L)
        }
    }

    @Test fun durableRecoveryStopIsScopedButExplicitStopRemainsAccountWide() = runTest {
        scenario { controller,prefs,repo,_,_ ->
            prefs.pendingStop("owner","old-session")
            assertTrue(controller.completePendingStop("owner"))
            verify(repo).sharing(false,"old-session")
            assertNull(prefs.pendingStop.first())
            assertNull(prefs.pendingStopSession.first())
            // Old persisted user-only stops keep their original semantics.
            prefs.pendingStop("owner")
            assertTrue(controller.completePendingStop("owner"))
            verify(repo).sharing(false,null)
            prefs.pendingStop("different-owner","foreign-session")
            assertFalse(controller.completePendingStop("different-owner"))
            verify(repo,never()).sharing(false,"foreign-session")
        }
    }

    @Test fun rejectedForegroundStartWithoutOwnedSessionCannotStopAnotherDevice() = runTest {
        scenario { controller,prefs,repo,_,_ ->
            controller.serviceStartFailed();runCurrent()
            assertFalse(controller.state.value.active)
            assertFalse(controller.state.value.starting)
            assertNull(prefs.pendingStop.first())
            verify(repo,never()).sharing(eq(false),any(),any())
        }
    }
    @Test fun reopeningAfterFailedReadClearsTheOldFailure() = runTest {
        scenario { controller,_,repo,_,_ ->
            doAnswer {throw IOException("offline")}.`when`(repo).ownSharingStatus()
            controller.resumeFromVisibleActivity()
            assertEquals("unreachable",controller.state.value.failure)
            doReturn(StatusDto("owner",false,4,null)).`when`(repo).ownSharingStatus()
            controller.resumeFromVisibleActivity()
            assertNull(controller.state.value.failure)
            assertFalse(controller.state.value.initializing)
            assertFalse(controller.state.value.active)
        }
    }
    @Test fun reopeningWithExplicitOffDoesNotEvenReadOrStartSharing() = runTest {
        scenario { controller,prefs,repo,_,_ ->
            prefs.sharingIntent("owner",false)
            controller.resumeFromVisibleActivity()
            verify(repo,never()).ownSharingStatus()
            assertFalse(controller.state.value.starting)
            assertFalse(controller.state.value.active)
        }
    }
    @Test fun simultaneousResumeRequestsShareOneReadAndLateFailureCannotUndoStop() = runTest {
        val scheduler=mock(androidx.work.impl.WorkManagerImpl::class.java)
        androidx.work.impl.WorkManagerImpl.setDelegate(scheduler)
        try {scenario { controller,prefs,repo,_,_ ->
            val response=CompletableDeferred<StatusDto>()
            doAnswer { invocation ->
                val continuation=invocation.rawArguments.last() as kotlin.coroutines.Continuation<StatusDto>
                val operation: suspend ()->StatusDto={response.await()}
                operation.startCoroutine(continuation)
                kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
            }.`when`(repo).ownSharingStatus()
            val first=launch {controller.resumeFromVisibleActivity()}
            runCurrent()
            controller.resumeFromVisibleActivity()
            verify(repo,times(1)).ownSharingStatus()
            controller.stop()
            response.completeExceptionally(IOException("old request"))
            first.join()
            assertEquals(false,prefs.sharingIntent("owner").first())
            assertNull(controller.state.value.failure)
            assertFalse(controller.state.value.active)
        }} finally {androidx.work.impl.WorkManagerImpl.setDelegate(null)}
    }

    @Test fun avatarPreferenceRoundTripsWithoutChangingChoiceIdentity()=runTest {
        scenario { _,prefs,_,_,_ ->
            for(choice in listOf(.75f,1f,1.25f,1.5f)) {
                prefs.avatarScale(choice)
                assertEquals(choice,prefs.avatarScale.first())
            }
        }
    }

}
