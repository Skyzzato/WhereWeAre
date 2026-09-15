package com.whereweare.app

import android.app.Application
import androidx.lifecycle.ViewModelStore
import com.whereweare.app.data.*
import com.whereweare.app.domain.Snapshot
import com.whereweare.app.service.SharingController
import com.whereweare.app.ui.PeopleViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class LocationRequestTest {
    private suspend fun TestScope.scenario(allowed: Boolean=true,test: suspend TestScope.(PeopleViewModel,SharingRepository,SharingController)->Unit) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store=ViewModelStore()
        try {
            val repo=mock(SharingRepository::class.java)
            val auth=mock(AuthRepository::class.java)
            val prefs=mock(PreferencesRepository::class.java)
            val location=mock(LocationRepository::class.java)
            val controller=mock(SharingController::class.java)
            val network=mock(NetworkMonitor::class.java)
            `when`(repo.state).thenReturn(MutableStateFlow(Snapshot()))
            `when`(auth.userId).thenReturn("owner")
            `when`(prefs.hidden("owner")).thenReturn(flowOf(emptySet()))
            `when`(network.online).thenReturn(MutableStateFlow(true))
            `when`(location.hasPermission()).thenReturn(allowed)
            `when`(location.enabled()).thenReturn(true)
            val vm=PeopleViewModel(repo,auth,prefs,mock(AvatarRepository::class.java),network,location,controller)
            store.put("people",vm)
            test(vm,repo,controller)
        } finally {store.clear();runCurrent();Dispatchers.resetMain()}
    }
    @Test fun acceptanceStartsExistingControllerOnlyAfterServerConsent()=runTest {
        scenario {vm,repo,controller ->
            `when`(repo.respondLocation("request",true)).thenReturn(true)
            vm.respondLocation("request",true);runCurrent()
            val order=inOrder(repo,controller)
            order.verify(repo).respondLocation("request",true)
            order.verify(controller).start()
        }
    }
    @Test fun rejectionNeverStartsTracking()=runTest {
        scenario {vm,repo,controller ->
            vm.respondLocation("request",false);runCurrent()
            verify(repo).respondLocation("request",false)
            verify(controller,never()).start()
        }
    }
    @Test fun deniedPermissionCannotAcceptOrStart()=runTest {
        scenario(false) {vm,repo,controller ->
            vm.respondLocation("request",true);runCurrent()
            verify(repo,never()).respondLocation("request",true)
            verify(controller,never()).start()
        }
    }
}
