package com.whereweare.app

import android.app.Application
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.whereweare.app.data.PreferencesRepository
import com.whereweare.app.data.RequestDto
import com.whereweare.app.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class V041Test {
    @Test fun freshPreferencesUseRocket46AndSavedChoicesSurviveReopen() = runBlocking {
        val file=File.createTempFile("flare-v041", ".preferences_pb").apply {delete()}
        val context=RuntimeEnvironment.getApplication()
        suspend fun verify(saved: Int?) {
            val job=SupervisorJob()
            val store=PreferenceDataStoreFactory.create(scope=CoroutineScope(Dispatchers.IO+job),produceFile={file})
            val repository=PreferencesRepository(store,context)
            if(saved!=null) repository.flareStyle(saved)
            assertEquals(saved ?: 46,repository.flareStyle.first())
            job.cancelAndJoin()
        }
        verify(null)
        for(id in listOf(1,30,31,46,50)) {
            verify(id)
            val job=SupervisorJob()
            val store=PreferenceDataStoreFactory.create(scope=CoroutineScope(Dispatchers.IO+job),produceFile={file})
            assertEquals(id,PreferencesRepository(store,context).flareStyle.first())
            job.cancelAndJoin()
        }
        file.delete()
        assertEquals(46,FlareStyles.get(null).id)
        assertEquals(46,RocketStyles.all.single {it.id==46}.id)
    }
    @Test fun requestPurposeAndTimestampSurviveMappingWithLegacyCompatibility() {
        val json=Json {ignoreUnknownKeys=true}
        val request=json.decodeFromString<RequestDto>("""{"id":"r","sender_id":"a","receiver_id":"b","status":"accepted","purpose":"location","created_at":"2026-09-16T09:30:00Z","sender_hidden":false}""").domain()
        assertEquals("location",request.purpose)
        assertEquals("2026-09-16T09:30:00Z",request.createdAt)
        assertEquals("accepted",request.displayStatus(Instant.parse("2026-09-20T00:00:00Z")))
        assertEquals("connection",json.decodeFromString<RequestDto>("""{"id":"r","sender_id":"a","receiver_id":"b","status":"pending"}""").domain().purpose)
    }
    @Test fun missingGroupCreationDateDoesNotInventTimestampOrDropExpiry() {
        val group=Json.decodeFromString<com.whereweare.app.data.GroupDto>("""{"id":"g","name":"Group","emoji":"G","invite_code":"ABC-DEF","creator_id":"a","expires_at":"2026-09-17T18:00:00Z"}""").domain()
        assertNull(group.createdAt)
        assertFalse(group.active(Instant.parse("2026-09-17T18:00:00Z")))
    }
    @Test fun expiryNeverTurnsAcceptanceRejectionOrCancellationIntoExpired() {
        val created=Instant.parse("2026-09-16T09:30:00Z")
        val pending=ShareRequest("r","a","b","pending","location",created.toString())
        assertEquals("pending",pending.displayStatus(created.plusSeconds(86399)))
        assertEquals("expired",pending.displayStatus(created.plusSeconds(86400)))
        for(status in listOf("accepted","rejected","cancelled")) assertEquals(status,pending.copy(status=status).displayStatus(created.plusSeconds(90000)))
        assertEquals("pending",pending.copy(purpose="connection").displayStatus(created.plusSeconds(90000)))
        assertEquals("pending",pending.copy(createdAt=null).displayStatus(created.plusSeconds(90000)))
    }
}
