package com.jarvis.interfaceapp.workspace

import android.content.ClipboardManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.jarvis.interfaceapp.MainActivity
import com.jarvis.interfaceapp.JarvisApplication
import com.jarvis.interfaceapp.resetLayoutFixture
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*

class LocalConversationActionsTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Before fun defaults() {
        compose.resetLayoutFixture()
        val app=compose.activity.application as JarvisApplication
        app.workspace.change {Workspace()}
        runBlocking {withTimeout(30_000){app.workspace.awaitIdle()}}
    }
    @Test fun copyDetailsAndRetryStayInExplicitSimulation() {
        compose.onNodeWithTag("open-conversation").performScrollTo().performClick()
        compose.onNodeWithTag("chat-demo").performScrollTo().performClick()
        compose.onNodeWithTag("chat-input").performTextInput("Essai public local")
        compose.onNodeWithTag("chat-send").performClick()
        compose.waitUntil(10000) {compose.onAllNodesWithText("FIXTURE — PAS UNE RÉPONSE IA").fetchSemanticsNodes().isNotEmpty()}
        compose.onAllNodesWithText("Copier").onFirst().performScrollTo().performClick()
        compose.runOnIdle {
            val clipboard=compose.activity.getSystemService(ClipboardManager::class.java)
            assertEquals("Essai public local",clipboard.primaryClip!!.getItemAt(0).text.toString())
        }
        compose.onAllNodesWithText("Détails / sources").onFirst().performScrollTo().performClick()
        compose.onNodeWithText("Détails du message").assertExists()
        compose.onNodeWithText("Fermer").performClick()
        compose.onAllNodesWithText("Relancer en simulation").onFirst().performScrollTo().performClick()
        val viewModel=androidx.lifecycle.ViewModelProvider(compose.activity)[com.jarvis.interfaceapp.ui.chat.ChatViewModel::class.java]
        compose.waitUntil(10000) {viewModel.state.value.messages.count {!it.user}==2}
        compose.onAllNodesWithText("FIXTURE — PAS UNE RÉPONSE IA").onLast().assertExists()
        compose.onNodeWithTag("chat-connection").performScrollTo().assertTextContains("aucun CORE ou LLM connecté",substring=true)
    }

    @Test fun optedInArchiveCompletesWhileActivityIsStopped() {
        val app=compose.activity.application as JarvisApplication
        app.workspace.change {it.copy(keepHistory=true)}
        runBlocking {withTimeout(30000){app.workspace.awaitIdle()}}
        compose.onNodeWithTag("open-conversation").performScrollTo().performClick()
        compose.onNodeWithTag("chat-demo").performScrollTo().performClick()
        compose.onNodeWithTag("chat-input").performTextInput("Essai public arrière-plan")
        compose.onNodeWithTag("chat-send").performClick()
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        runBlocking {withTimeout(30000) {
            while(app.workspace.state.value.value.history.size<2)delay(50)
            app.workspace.awaitIdle()
        }}
        assertTrue(app.workspace.state.value.durable)
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        assertEquals(2,app.workspace.state.value.value.history.size)
        assertTrue(app.workspace.state.value.value.history.all {it.simulation})
    }
}
