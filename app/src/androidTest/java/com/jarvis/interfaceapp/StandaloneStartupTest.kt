package com.jarvis.interfaceapp

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.Before

class StandaloneStartupTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Before fun home() {
        compose.activity.getSharedPreferences("ui-01", android.content.Context.MODE_PRIVATE).edit().putString("route", "Accueil").putBoolean("demo", false).putBoolean("secure", false).commit()
        compose.activityRule.scenario.recreate()
    }

    @Test fun launchesDashboardAndAvatarWithoutPartners() {
        compose.onNodeWithText("J A R V I S").assertIsDisplayed()
        compose.onNodeWithTag("dashboard").assertExists()
        compose.onNodeWithTag("nav-Avatar").performClick()
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
            compose.onNodeWithText("Services JARVIS : Non connecté").assertExists()
            compose.onNodeWithTag("close-settings").performScrollTo().performClick()
        }
    }
}
