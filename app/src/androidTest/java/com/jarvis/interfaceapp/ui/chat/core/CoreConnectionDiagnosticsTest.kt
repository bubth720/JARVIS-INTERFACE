package com.jarvis.interfaceapp.ui.chat.core

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.jarvis.interfaceapp.MainActivity
import com.jarvis.interfaceapp.resetLayoutFixture
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class CoreConnectionDiagnosticsTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Before fun defaults() { compose.resetLayoutFixture() }
    private fun open() {
        compose.onNodeWithTag("open-conversation").performScrollTo().performClick()
        compose.onNodeWithTag("open-core-diagnostics").performScrollTo().performClick()
    }
    @Test fun defaultNeverOffersARealConnectionOrMutatesLayouts() {
        val file=File(compose.activity.filesDir,"datastore/dashboard.json");val before=file.readBytes()
        open();compose.onNodeWithTag("core-real-status").assertTextEquals("CORE réel NON CONNECTÉ")
        compose.onNodeWithTag("core-protocol-status").assertTextContains("UNAVAILABLE",substring=true)
        compose.onNodeWithTag("core-protocol-run").assertDoesNotExist()
        compose.onNodeWithTag("core-protocol-close").performScrollTo().performClick()
        compose.onNodeWithTag("chat-connection").assertTextContains("NON CONNECTÉ",substring=true)
        assertArrayEquals(before,file.readBytes())
    }
    @Test fun voluntaryProtocolLabKeepsFixtureOutsideTheChatThread() {
        open();compose.onNodeWithTag("core-protocol-lab").performScrollTo().performClick()
        compose.onNodeWithTag("core-protocol-run").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("core-protocol-text").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("CONTENU DE TEST · PAS UNE RÉPONSE IA").assertExists()
        compose.onNodeWithTag("core-real-status").assertTextEquals("CORE réel NON CONNECTÉ")
        compose.onNodeWithTag("core-protocol-close").performScrollTo().performClick()
        compose.onNodeWithTag("chat-timeline").onChildren().assertCountEquals(0)
        compose.onNodeWithTag("chat-connection").assertTextContains("NON CONNECTÉ",substring=true)
    }
}
