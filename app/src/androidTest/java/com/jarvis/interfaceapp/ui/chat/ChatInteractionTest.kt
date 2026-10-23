package com.jarvis.interfaceapp.ui.chat

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.jarvis.interfaceapp.ui.JarvisTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Compiled device tests use synthetic public data only; execution is reported separately. */
class ChatInteractionTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun disconnectedScreenHasAccessibleInputButNeverOffersARealSend() {
        compose.setContent { JarvisTheme { ChatScreen(SessionUiState(),{false},{},{},{}) } }
        compose.onNodeWithTag("chat-input").performTextInput("Question publique synthétique")
        compose.onNodeWithTag("chat-send").assertIsNotEnabled()
        compose.onNodeWithTag("chat-connection").assertTextContains("NON CONNECTÉ",substring=true)
    }
    @Test fun demoFixtureIsVisibleAndComposerDoesNotClaimAnAiReply() {
        var sent=""
        val messages=listOf(ConversationMessage("fixture1","Texte public synthétique",true,false))
        compose.setContent { JarvisTheme { ChatScreen(SessionUiState(connection=PortConnection.DEMONSTRATION,messages=messages),{sent=it;true},{},{},{}) } }
        compose.onNodeWithTag("chat-input").performTextInput("Suivi public")
        compose.onNodeWithTag("chat-send").performScrollTo().performClick()
        compose.runOnIdle {assertEquals("Suivi public",sent)}
        compose.onNodeWithText("FIXTURE — PAS UNE RÉPONSE IA").assertExists()
    }
}
