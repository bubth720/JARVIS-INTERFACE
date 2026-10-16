package com.jarvis.interfaceapp

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.jarvis.interfaceapp.domain.*
import com.jarvis.interfaceapp.storage.LayoutCodec
import com.jarvis.interfaceapp.ui.SavePhase
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class StorageInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Before fun resetFixture() { compose.resetLayoutFixture() }
    private val controller get() = (compose.activity.application as JarvisApplication).dashboard
    private fun awaitWrites() {
        runBlocking { withTimeout(30_000) { controller.awaitIdle() } }
        compose.waitForIdle()
        assertEquals(SavePhase.SAVED, controller.state.value.storage.phase)
    }
    private fun bytes() = File(compose.activity.filesDir, "datastore/dashboard.json").readBytes()
    private fun configureBoth() {
        controller.dispatch(HomeAction.ToggleEditing)
        controller.dispatch(HomeAction.Place(DashboardProfile.COMPACT, PanelId.AVATAR, 0, 1, 3, 3))
        controller.dispatch(HomeAction.Place(DashboardProfile.WIDE, PanelId.AVATAR, 1, 1, 5, 3))
        controller.dispatch(HomeAction.ToggleLock(DashboardProfile.COMPACT, PanelId.AVATAR))
        controller.dispatch(HomeAction.ToggleLock(DashboardProfile.WIDE, PanelId.AVATAR))
        awaitWrites()
    }
    @Test fun resetRequiresConfirmationAndCancelPreservesDiskBytes() {
        configureBoth()
        val before = bytes()
        compose.onNodeWithTag("open-settings").performClick()
        compose.onNodeWithTag("reset-layouts").performClick()
        compose.onNodeWithTag("cancel-reset").assertIsDisplayed().performClick()
        awaitWrites()
        assertArrayEquals(before, bytes())
        assertNotEquals(defaultLayouts(), LayoutCodec.decode(bytes()).layouts)
    }
    @Test fun confirmedResetPersistsBothProfilesAndUnlocksAllPanels() {
        configureBoth()
        compose.onNodeWithTag("open-settings").performClick()
        compose.onNodeWithTag("reset-layouts").performClick()
        compose.onNodeWithTag("confirm-reset").performClick()
        awaitWrites()
        assertEquals(defaultLayouts(), controller.state.value.home.layouts)
        assertEquals(defaultLayouts(), LayoutCodec.decode(bytes()).layouts)
        compose.onNodeWithText("Disposition enregistrée.").assertIsDisplayed()
    }
    @Test fun activityRecreationRetainsSavedGeometryAndLocks() {
        configureBoth()
        val expected = controller.state.value.home.layouts
        compose.activityRule.scenario.recreate()
        awaitWrites()
        assertEquals(expected, controller.state.value.home.layouts)
        assertEquals(expected, LayoutCodec.decode(bytes()).layouts)
    }
    @Test fun rejectedPlacementDoesNotChangeTheStoredDocument() {
        val before = bytes()
        controller.dispatch(HomeAction.ToggleEditing)
        controller.dispatch(HomeAction.Place(DashboardProfile.COMPACT, PanelId.AVATAR, 3, 0, 3, 4))
        awaitWrites()
        assertEquals(PlacementError.OVERLAP, controller.state.value.home.placementError)
        assertArrayEquals(before, bytes())
    }
    @Test fun navigationAndEditingAreNotWrittenToDisk() {
        val before = bytes()
        controller.dispatch(HomeAction.ToggleEditing)
        controller.dispatch(HomeAction.OpenSettings)
        controller.dispatch(HomeAction.CloseSettings)
        awaitWrites()
        assertArrayEquals(before, bytes())
    }
    @Test fun backgroundThenResumeKeepsSavedLayoutsAndLocks() {
        configureBoth()
        val expected = controller.state.value.home.layouts
        val stored = bytes()
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        awaitWrites()
        assertEquals(expected, controller.state.value.home.layouts)
        assertArrayEquals(stored, bytes())
        compose.onNodeWithTag("dashboard").assertIsDisplayed()
    }
}
