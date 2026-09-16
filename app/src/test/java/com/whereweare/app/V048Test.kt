package com.whereweare.app

import android.app.Application
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.whereweare.app.data.*
import com.whereweare.app.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class V048Test {
    @Test fun preferencesSurviveStoreRecreationAndAreIndependent()=runTest {
        val context=RuntimeEnvironment.getApplication()
        val file=context.preferencesDataStoreFile("v048-${java.util.UUID.randomUUID()}")
        suspend fun session(block: suspend (PreferencesRepository)->Unit) {
            val job=SupervisorJob();val scope=CoroutineScope(Dispatchers.IO+job)
            val store=PreferenceDataStoreFactory.create(scope=scope,produceFile={file})
            try {block(PreferencesRepository(store,context))} finally {job.cancelAndJoin()}
        }
        session {p ->
            assertEquals(5,p.interval.first());assertTrue(p.hiddenPlaces("alice").first().isEmpty())
            p.interval(60);p.hidePlace("alice","home",true);p.avatarScale(.75f);p.placeIconScale(1.5f)
        }
        session {p ->
            assertEquals(60,p.interval.first());assertEquals(setOf("home"),p.hiddenPlaces("alice").first())
            assertTrue(p.hiddenPlaces("bob").first().isEmpty())
            assertEquals(.75f,p.avatarScale.first());assertEquals(1.5f,p.placeIconScale.first())
            p.hidePlace("alice","home",false);assertTrue(p.hiddenPlaces("alice").first().isEmpty())
        }
        file.delete()
    }
    @Test fun legacyBootstrapDoesNotOverrideNewDefault()=runTest {
        val context=RuntimeEnvironment.getApplication()
        val store=object: androidx.datastore.core.DataStore<Preferences> {
            override val data=MutableStateFlow<Preferences>(emptyPreferences())
            override suspend fun updateData(transform: suspend (Preferences)->Preferences)=transform(data.value).also {data.value=it}
        }
        val p=PreferencesRepository(store,context)
        p.bootstrap(kotlinx.serialization.json.Json.encodeToString(BootstrapConfig.serializer(),BootstrapConfig(16,"0.47",1,false,defaults=GlobalDefaults(gps_interval_seconds=60))))
        assertEquals(5,p.interval.first())
        p.interval(300);p.hidePlace("alice","home",true)
        assertEquals(300,p.interval.first())
    }
    @Test fun mapSelectionUsesManualCoordinatesWithoutDeviceLocation() {
        assertEquals(12.5 to -45.2,placePickerInitial(12.5,-45.2,null))
        assertEquals(41.9028 to 12.4964,placePickerInitial(Double.NaN,0.0,null))
        assertFalse(validPlace("Home","Infinity","0","100"))
        assertFalse(validPlace("Home","0","181","100"))
        assertTrue(validPlace("Home","0","180","50"))
    }
    @Test fun markerSizesRemainDistinct() {assertEquals(listOf(24f,36f,52f,72f),listOf(.75f,1f,1.25f,1.5f).map(::mapPlaceIconDp))}
}
