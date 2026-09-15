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
}
