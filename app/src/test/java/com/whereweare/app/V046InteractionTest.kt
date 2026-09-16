package com.whereweare.app

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import com.whereweare.app.domain.*
import com.whereweare.app.ui.*
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class V046InteractionTest {
    @get:Rule val compose=createEmptyComposeRule()
    private fun outcome(state: SosSendState,title: Int) {
        Robolectric.buildActivity(ComponentActivity::class.java).setup().use {controller ->
            Strings.configure(localizedActivityContext(controller.get(),"it"))
            controller.get().setContent {MaterialTheme {SosOutcomeCard(state,null,{},{})}}
            compose.onNodeWithText(Strings.text(title)).assertIsDisplayed()
            if(state==SosSendState.SENDING) compose.onNode(hasProgressBarRangeInfo(androidx.compose.ui.semantics.ProgressBarRangeInfo.Indeterminate)).assertIsDisplayed()
        }
    }
    @Test fun progressCard()=outcome(SosSendState.SENDING,R.string.sos_sending)
    @Test fun confirmedCard()=outcome(SosSendState.CONFIRMED,R.string.sos_registered_title)
    @Test fun failureCard()=outcome(SosSendState.FAILED,R.string.sos_failed)
    @Test fun uncertainCard()=outcome(SosSendState.UNKNOWN,R.string.sos_unconfirmed_title)
    @Test fun receivedSosCanBeDismissedWithSeparateConfirmation() {
        val event=AppEvent("sos","other","Sender","sos","2026-09-16T09:00:00Z","2026-09-17T09:00:00Z",buildJsonObject {})
        var deleted:String?=null
        Robolectric.buildActivity(ComponentActivity::class.java).setup().use {controller ->
            Strings.configure(localizedActivityContext(controller.get(),"it"))
            controller.get().setContent {MaterialTheme {CheckinInbox(listOf(event,event),Snapshot(),open={fail()},close={},remove={id,done -> deleted=id;done()})}}
            compose.onAllNodesWithContentDescription(Strings.text(R.string.update_remove)).assertCountEquals(1)
            compose.onNodeWithContentDescription(Strings.text(R.string.update_remove)).performClick()
            assertNull(deleted)
            compose.onNodeWithText(Strings.text(R.string.update_remove_sos)).assertIsDisplayed()
            compose.onNode(hasText(Strings.text(R.string.update_remove)) and hasClickAction()).performClick()
            assertEquals("sos",deleted)
        }
    }
}
