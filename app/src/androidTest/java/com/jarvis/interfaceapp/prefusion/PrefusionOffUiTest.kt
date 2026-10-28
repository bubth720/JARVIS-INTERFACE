package com.jarvis.interfaceapp.prefusion

import android.os.Build
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.jarvis.interfaceapp.MainActivity
import com.jarvis.interfaceapp.resetLayoutFixture
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Public emulator-only scene: no portrait pack, audio, model, personal URI or actual CORE. */
@OptIn(ExperimentalTestApi::class)
class PrefusionOffUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Before fun reset(){compose.resetLayoutFixture()}
    private fun conversation(){compose.onNodeWithTag("open-conversation").performScrollTo().performClick()}
    @Test fun offConversationSurvivesRecreationWithoutPermissionOrInventedReply() {
        conversation()
        compose.onNodeWithTag("chat-input").performTextInput("Public synthetic lot3")
        compose.onNodeWithTag("chat-send").assertIsNotEnabled()
        compose.onNodeWithTag("chat-connection").assertTextContains("NON CONNECTÉ",substring=true)
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("chat-connection").assertTextContains("NON CONNECTÉ",substring=true)
        compose.onNodeWithTag("chat-send").assertIsNotEnabled()
        compose.onNodeWithTag("chat-timeline").onChildren().assertCountEquals(0)
        compose.onNodeWithTag("avatar-renderer").assertDoesNotExist()
        capturePublicOffScene()
    }
    @Test fun absentTransportDiagnosticsExposeErrorAndNoExecutablePermission() {
        conversation();compose.onNodeWithTag("open-core-diagnostics").performScrollTo().performClick()
        compose.onNodeWithTag("core-real-status").assertTextEquals("CORE réel NON CONNECTÉ")
        compose.onNodeWithTag("core-protocol-status").assertTextContains("UNAVAILABLE",substring=true)
        compose.onNodeWithTag("core-protocol-run").assertDoesNotExist()
        compose.onNodeWithTag("core-protocol-close").performScrollTo().performClick()
        compose.onNodeWithTag("chat-send").assertIsNotEnabled()
    }
    private fun capturePublicOffScene() {
        if(InstrumentationRegistry.getArguments().getString("prefusionCapture")!="true")return
        check(Build.HARDWARE in setOf("ranchu","goldfish")) {"Captures only on clean test emulators"}
        compose.waitForIdle()
        val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
        File(compose.activity.filesDir,"prefusion-lot3-off.png").outputStream().use {
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
        }
    }
}
