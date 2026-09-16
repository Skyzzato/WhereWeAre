package com.whereweare.app

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import com.whereweare.app.domain.*
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
class V048InteractionTest {
    @get:Rule val compose=createEmptyComposeRule()
    @Test fun editPlaceKeepsIdentityAndCanRemoveIconWithoutAGroup() {
        val place=SavedPlace("home",0,"Casa",45.0,11.0,100,"🏠")
        var saved: SavedPlace?=null
        Robolectric.buildActivity(ComponentActivity::class.java).setup().use {controller ->
            Strings.configure(localizedActivityContext(controller.get(),"it"))
            controller.get().setContent {MaterialTheme {PlaceEditor(listOf(place),0,place,null,OperationState(),"standard",{}) {saved=it}}}
            compose.onNodeWithText("Modifica luogo").assertIsDisplayed()
            compose.onNodeWithText("Modifica gruppo").assertDoesNotExist()
            compose.onNodeWithText(Strings.text(R.string.group_remove_icon)).performScrollTo().performClick()
            compose.onNodeWithText(Strings.text(R.string.save)).performClick()
            assertEquals(place.copy(emoji=""),saved)
        }
    }
    @Test fun radiusHelpPreservesDraftAndMapEntryExistsWithoutGps() {
        val place=SavedPlace("home",0,"Casa",45.0,11.0,100,"🏠")
        var saved: SavedPlace?=null
        Robolectric.buildActivity(ComponentActivity::class.java).setup().use {controller ->
            Strings.configure(localizedActivityContext(controller.get(),"it"))
            controller.get().setContent {MaterialTheme {PlaceEditor(listOf(place),0,place,null,OperationState(),"standard",{}) {saved=it}}}
            compose.onNodeWithText("Scegli sulla mappa").performScrollTo().assertIsEnabled()
            compose.onNodeWithContentDescription(Strings.text(R.string.place_radius_info_title)).performScrollTo().performClick()
            compose.onNodeWithText(Strings.text(R.string.place_radius_info)).assertIsDisplayed()
            assertNull(saved)
            compose.onAllNodesWithText(Strings.text(R.string.close)).onLast().performClick()
            compose.onNodeWithText(Strings.text(R.string.save)).performClick()
            assertEquals(place,saved)
        }
    }
}
