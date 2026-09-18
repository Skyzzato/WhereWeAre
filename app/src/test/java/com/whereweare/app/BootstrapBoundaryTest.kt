package com.whereweare.app

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import com.whereweare.app.data.*
import com.whereweare.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class BootstrapBoundaryTest {
    @get:Rule val compose=createEmptyComposeRule()

    @Test fun everyBlockingGateExcludesOnboardingAndNavigationAndPreservesRetry() {
        var gate by mutableStateOf(BootstrapGate.LOADING)
        var retries=0
        Robolectric.buildActivity(ComponentActivity::class.java).setup().use { controller ->
            Strings.configure(localizedActivityContext(controller.get(),"it"))
            controller.get().setContent { MaterialTheme {
                BootstrapBoundary(BootstrapState(gate),false,{retries++}) {
                    Text("onboarding/login/navigation")
                }
            } }
            for(block in BootstrapGate.entries.filter { it!=BootstrapGate.READY }) {
                compose.runOnIdle { gate=block }
                compose.onNodeWithText("onboarding/login/navigation").assertDoesNotExist()
                if(block!=BootstrapGate.LOADING) compose.onNodeWithText(Strings.text(R.string.ui_124)).performClick()
            }
            assertEquals(4,retries)
            compose.runOnIdle { gate=BootstrapGate.READY }
            compose.onNodeWithText("onboarding/login/navigation").assertIsDisplayed()
        }
    }

    @Test fun updateRemovesExistingContentAcrossAccountIntentAndActivityResumeChanges() {
        var gate by mutableStateOf(BootstrapGate.READY)
        var destination by mutableStateOf("map/accountA")
        var initializing by mutableStateOf(false)
        Robolectric.buildActivity(ComponentActivity::class.java).setup().use { controller ->
            Strings.configure(localizedActivityContext(controller.get(),"it"))
            controller.get().setContent { MaterialTheme {
                BootstrapBoundary(BootstrapState(gate),initializing,{}) {Text(destination)}
            } }
            compose.onNodeWithText("map/accountA").assertIsDisplayed()
            compose.runOnIdle { gate=BootstrapGate.UPDATE }
            compose.onNodeWithText("map/accountA").assertDoesNotExist()
            for(target in listOf("onboarding","login/accountB","deep-link","notification","settings")) {
                compose.runOnIdle { destination=target; initializing=!initializing }
                compose.onNodeWithText(target).assertDoesNotExist()
                compose.onNodeWithText(Strings.text(R.string.ui_120)).assertIsDisplayed()
            }
            controller.pause().stop().start().resume()
            compose.onNodeWithText(Strings.text(R.string.ui_120)).assertIsDisplayed()
            compose.onNodeWithText("settings").assertDoesNotExist()
        }
    }

    @Test fun minimum17BlocksEveryOlderCodeIncludingDuringMaintenance() {
        val config=BootstrapConfig(17,"0.48",17,false,api_version=3)
        assertEquals(BootstrapGate.READY,bootstrapGate(config,17))
        for(code in 1..16) assertEquals(BootstrapGate.UPDATE,bootstrapGate(config,code))
        assertEquals(BootstrapGate.UPDATE,bootstrapGate(config.copy(maintenance_mode=true),16))
    }
}
