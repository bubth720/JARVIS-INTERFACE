package com.jarvis.interfaceapp.avatar.v2

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.jarvis.interfaceapp.MainActivity
import com.jarvis.interfaceapp.resetLayoutFixture
import org.junit.Assert.assertArrayEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class AvatarV2DashboardTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Before fun defaults() { compose.resetLayoutFixture() }
    private fun bytes()=File(compose.activity.filesDir,"datastore/dashboard.json").readBytes()
    @Test fun productionEntryApplyAndActivityRecreationKeepLayoutBytesAndFourPanels() {
        val before=bytes()
        compose.onNodeWithTag("avatar-preview").performScrollTo().performClick()
        compose.onNodeWithTag("avatar-v2-open").performScrollTo().performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("avatar-v2-current").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("avatar-v2-confirm").performScrollTo().performClick()
        compose.onNodeWithTag("avatar-v2-apply").performScrollTo().performClick()
        compose.onNodeWithTag("dashboard").assertExists()
        compose.onNodeWithTag("avatar-renderer").assertIsDisplayed().assertContentDescriptionEquals("Athéna · portrait premium")
        assertArrayEquals(before,bytes())
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("avatar-renderer").assertIsDisplayed().assertContentDescriptionEquals("Athéna · portrait premium")
        compose.onNodeWithTag("panel-AVATAR").assertExists()
        compose.onNodeWithTag("panel-SYSTEM").assertExists()
        compose.onNodeWithTag("panel-APPLICATIONS").assertExists()
        compose.onNodeWithTag("panel-COMMANDS").assertExists()
        assertArrayEquals(before,bytes())
    }
}
