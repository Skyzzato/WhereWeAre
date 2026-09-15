package com.whereweare.app

import android.app.Application
import com.google.android.gms.location.FusedLocationProviderClient
import com.whereweare.app.data.AuthRepository
import com.whereweare.app.data.LocationRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class LocationDiagnosticsTest {
    @Test fun absentPermissionProducesUnavailableSatellitesWithoutStartingLocation() = runBlocking {
        val fused=mock(FusedLocationProviderClient::class.java)
        val repository=LocationRepository(RuntimeEnvironment.getApplication(),fused,mock(AuthRepository::class.java))
        assertNull(repository.details.value)
        assertNull(repository.satellites().first())
        verifyNoInteractions(fused)
    }
}
