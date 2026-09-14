package com.whereweare.app

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.whereweare.app.data.MeetingFeedback
import com.whereweare.app.data.PreferencesRepository
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class MeetingFeedbackTest {
    @Test fun localCreationAndServerEchoOnlyAnimateOnceWhileRecipientStillAnimates() = runBlocking {
        val context=RuntimeEnvironment.getApplication()
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        val file=File(context.cacheDir,"${UUID.randomUUID()}.preferences_pb")
        try {
            val prefs=PreferencesRepository(PreferenceDataStoreFactory.create(scope=scope,produceFile={file}),context)
            val creator=MeetingFeedback(prefs)
            assertTrue(creator.created("creator","meeting"))
            assertEquals("meeting:created",creator.events.value.single().id)
            assertFalse(creator.created("creator","meeting"))
            creator.finish("creator","meeting:created")
            assertTrue(creator.events.value.isEmpty())
            val recreated=MeetingFeedback(prefs)
            assertFalse(recreated.created("creator","meeting"))
            assertTrue(recreated.created("recipient","meeting"))
            assertFalse(recreated.created("recipient","meeting"))
        } finally {scope.coroutineContext[Job]!!.cancelAndJoin();file.delete()}
    }
    @Test fun queuedMeetingsAreNotOverwrittenAndFinishingIsAccountScoped() = runBlocking {
        val context=RuntimeEnvironment.getApplication()
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        val file=File(context.cacheDir,"${UUID.randomUUID()}.preferences_pb")
        try {
            val feedback=MeetingFeedback(PreferencesRepository(PreferenceDataStoreFactory.create(scope=scope,produceFile={file}),context))
            feedback.created("a","one");feedback.created("a","two");feedback.created("b","one")
            feedback.finish("a","one:created")
            assertEquals(listOf("two:created","one:created"),feedback.events.value.map {it.id})
            feedback.clear();assertTrue(feedback.events.value.isEmpty())
        } finally {scope.coroutineContext[Job]!!.cancelAndJoin();file.delete()}
    }
}
