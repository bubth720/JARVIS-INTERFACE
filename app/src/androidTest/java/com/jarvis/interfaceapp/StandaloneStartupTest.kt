package com.jarvis.interfaceapp

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

/** Run on an isolated Android test device. No external JARVIS partner is required. */
class StandaloneStartupTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun launchesDashboardAndAvatarWithoutPartners() {
        compose.onNodeWithText("J A R V I S").assertIsDisplayed()
        compose.onNodeWithTag("dashboard").assertExists()
        compose.onNodeWithText("Avatar", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("avatar-placeholder").assertExists()
    }
    @Test fun settingsReturnsToDashboard() {
        compose.onNodeWithTag("open-settings").performClick()
        compose.onNodeWithTag("close-settings").performScrollTo().performClick()
        compose.onNodeWithTag("dashboard").assertExists()
    }
    @Test fun recreationRestoresUsableInterface() {
        compose.onNodeWithTag("open-settings").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Paramètres graphiques").assertExists()
        compose.onNodeWithTag("close-settings").performScrollTo().performClick()
        compose.onNodeWithTag("dashboard").assertExists()
    }
    @Test fun allEightModulesOpenAndReturn() {
        repeat(8) { index ->
            compose.onNodeWithTag("module-$index").performScrollTo().performClick()
            compose.onNodeWithTag("module-screen").assertExists()
            compose.onNodeWithText("Non connecté").assertExists()
            compose.onNodeWithTag("close-settings").performScrollTo().performClick()
        }
    }
}
