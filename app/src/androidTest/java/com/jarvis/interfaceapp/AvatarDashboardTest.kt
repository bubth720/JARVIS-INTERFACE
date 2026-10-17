package com.jarvis.interfaceapp

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertArrayEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class AvatarDashboardTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Before fun defaults() { compose.resetLayoutFixture() }
    private fun bytes() = File(compose.activity.filesDir, "datastore/dashboard.json").readBytes()
    @Test fun previewCommandsNeverChangeSavedDashboardLayouts() {
        val before = bytes()
        compose.onNodeWithTag("avatar-preview").performScrollTo().performClick()
        for (id in listOf("ANIM_001", "ANIM_002", "ANIM_003")) {
            compose.onNodeWithTag("avatar-play-$id").performScrollTo().performClick()
        }
        compose.onNodeWithTag("avatar-state-LISTENING").performScrollTo().performClick()
        compose.onNodeWithTag("avatar-reset").performScrollTo().performClick()
        compose.onNodeWithTag("avatar-close").performScrollTo().performClick()
        compose.onNodeWithTag("dashboard").assertExists()
        assertArrayEquals(before, bytes())
    }
    @Test fun activityRecreationRebuildsAvatarWithoutLosingLayout() {
        val before = bytes()
        compose.onNodeWithTag("avatar-renderer").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("avatar-renderer").assertIsDisplayed()
        compose.onNodeWithTag("avatar-preview").performScrollTo().performClick()
        compose.onNodeWithTag("avatar-preview-renderer").assertIsDisplayed()
        assertArrayEquals(before, bytes())
    }
}
