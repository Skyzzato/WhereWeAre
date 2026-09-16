package com.whereweare.app

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import com.whereweare.app.domain.*
import com.whereweare.app.ui.SosMapButton
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
class V044InteractionTest {
    @get:Rule val compose=createEmptyComposeRule()
    @Test fun sosOpensWithoutLoadedCapabilities() {
        var opened=false
        Robolectric.buildActivity(ComponentActivity::class.java).setup().use {controller ->
            controller.get().setContent {MaterialTheme {SosMapButton(Snapshot(syncFailed=true),emptyList(),openEditor={opened=true},openEvent={fail()})}}
            compose.onNodeWithText("SOS").assertIsDisplayed().performClick()
            assertTrue(opened)
        }
    }
    @Test fun existingSosOpensItsDetailInsteadOfPreparingDuplicate() {
        var selected:String?=null
        val snapshot=Snapshot(profile=UserProfile("self","Test","ABC-DEF"))
        val event=AppEvent("event","self","Test","sos","2026-09-16T00:00:00Z","2026-09-16T06:00:00Z",buildJsonObject {})
        Robolectric.buildActivity(ComponentActivity::class.java).setup().use {controller ->
            controller.get().setContent {MaterialTheme {SosMapButton(snapshot,listOf(event),openEditor={fail()},openEvent={selected=it})}}
            compose.onNodeWithText("SOS").performClick()
            assertEquals("event",selected)
        }
    }
}
