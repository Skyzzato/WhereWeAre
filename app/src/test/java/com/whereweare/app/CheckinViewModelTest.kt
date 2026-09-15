package com.whereweare.app

import android.app.Application
import androidx.lifecycle.ViewModelStore
import com.whereweare.app.data.*
import com.whereweare.app.domain.*
import com.whereweare.app.service.SharingController
import com.whereweare.app.ui.MapViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class CheckinViewModelTest {
    private suspend fun TestScope.scenario(test: suspend TestScope.(MapViewModel,SharingRepository,LocationRepository,SharingController)->Unit) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store=ViewModelStore()
        try {
            val repo=mock(SharingRepository::class.java);val auth=mock(AuthRepository::class.java)
            val prefs=mock(PreferencesRepository::class.java);val location=mock(LocationRepository::class.java)
            val controller=mock(SharingController::class.java);val network=mock(NetworkMonitor::class.java)
            val boot=mock(BootstrapRepository::class.java)
            `when`(auth.userId).thenReturn("sender")
            `when`(repo.state).thenReturn(MutableStateFlow(Snapshot()))
            `when`(controller.state).thenReturn(MutableStateFlow(com.whereweare.app.service.TrackingState()))
            `when`(controller.pendingStop).thenReturn(flowOf(null))
            `when`(prefs.hidden("sender")).thenReturn(flowOf(emptySet()))
            `when`(prefs.hiddenGroups("sender")).thenReturn(flowOf(emptySet()))
            `when`(prefs.highAccuracy).thenReturn(flowOf(true))
            `when`(prefs.interval).thenReturn(flowOf(60));`when`(prefs.threshold).thenReturn(flowOf(100))
            `when`(prefs.avatarScale).thenReturn(flowOf(1f));`when`(prefs.mapStyle).thenReturn(flowOf("standard"))
            `when`(location.permission()).thenReturn(LocationPermission.PRECISE)
            `when`(network.online).thenReturn(MutableStateFlow(true))
            `when`(boot.state).thenReturn(MutableStateFlow(BootstrapState(BootstrapGate.READY)))
            val vm=MapViewModel(controller,repo,location,prefs,auth,mock(AvatarRepository::class.java),network,boot,mock(MeetingFeedback::class.java))
            store.put("map",vm);test(vm,repo,location,controller)
        } finally {store.clear();runCurrent();Dispatchers.resetMain()}
    }
    @Test fun voluntarySnapshotDoesNotStartOrStopContinuousSharing()=runTest {
        scenario {vm,repo,location,controller ->
            val fix=UserLocation("sender",46.0,11.0,18.0,null,null,Instant.now())
            `when`(location.snapshot(true)).thenReturn(fix)
            vm.checkin("id","here","",setOf("person"),emptySet(),null) {};runCurrent()
            verify(repo).checkin("id","here","",fix,setOf("person"),emptySet(),null)
            verify(controller,never()).start();verify(controller,never()).requestStop()
        }
    }
    @Test fun unavailableFixNeverFabricatesOrPublishesASnapshot()=runTest {
        scenario {vm,repo,location,controller ->
            `when`(location.snapshot(true)).thenThrow(IllegalStateException("location_disabled"))
            clearInvocations(repo)
            vm.checkin("id","here","",setOf("person"),emptySet(),null) {};runCurrent()
            verify(controller,never()).start()
            org.junit.Assert.assertEquals(R.string.location_disabled,vm.operation.value.message)
            verifyNoInteractions(repo)
        }
    }
}
