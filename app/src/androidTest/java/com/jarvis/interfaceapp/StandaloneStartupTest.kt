package com.jarvis.interfaceapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Run on a device without other JARVIS apps, with airplane mode enabled. */
class StandaloneStartupTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Before fun resetFixture() { compose.resetLayoutFixture() }

    @Test fun launchesDashboardAndAvatarWithoutPartners() {
        compose.onNodeWithText("JARVIS INTERFACE").assertIsDisplayed()
        compose.onNodeWithTag("dashboard").assertExists()
        compose.onNodeWithTag("avatar-placeholder").assertExists()
        compose.onNodeWithTag("local-status").assertExists()
    }
    @Test fun settingsReturnsToDashboard() {
        compose.onNodeWithTag("open-settings").performClick()
        compose.onNodeWithTag("close-settings").assertIsDisplayed().performClick()
        compose.onNodeWithTag("dashboard").assertExists()
    }
    @Test fun recreationRestoresUsableInterface() {
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("JARVIS INTERFACE").assertIsDisplayed()
        compose.onNodeWithTag("open-settings").assertIsDisplayed()
    }
}
