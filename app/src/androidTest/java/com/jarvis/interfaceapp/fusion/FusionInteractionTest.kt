package com.jarvis.interfaceapp.fusion

import android.content.pm.ActivityInfo
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.jarvis.interfaceapp.MainActivity
import com.jarvis.interfaceapp.JarvisApplication
import com.jarvis.interfaceapp.resetLayoutFixture
import com.jarvis.interfaceapp.workspace.Workspace
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import java.io.File

@OptIn(ExperimentalTestApi::class)
class FusionInteractionTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val app get()=compose.activity.application as JarvisApplication
    @Before fun setup() {
        compose.resetLayoutFixture()
        app.workspace.change {Workspace()};runBlocking {withTimeout(30_000){app.workspace.awaitIdle()}}
        compose.onNodeWithTag("open-studio").performScrollTo().performClick()
        compose.onNodeWithTag("studio-tab-Fusion").performScrollTo().performClick()
    }
    private fun click(tag:String) {
        val node=compose.onNodeWithTag(tag)
        if(tag in setOf("fusion-confirm","fusion-cancel"))node.performClick() else node.performScrollTo().performClick()
    }
    private fun openPreview() {click("fusion-lab-toggle");click("fusion-source");click("fusion-preview")}
    private fun capture(name:String) {
        if(androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("fusionCapture")!="true")return
        require(android.os.Build.HARDWARE in setOf("ranchu","goldfish"))
        compose.onNodeWithTag("avatar-renderer").assertDoesNotExist()
        compose.onNodeWithTag(if(name=="disconnected") "fusion-real-status" else "fusion-lab-status").performScrollTo();compose.waitForIdle()
        val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
        File(compose.activity.filesDir,"fusion-$name.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun defaultShowsNoConnectionAndNoReadyClaims() {
        compose.onNodeWithTag("fusion-real-status").assertTextContains("NON CONNECTÉ",substring=true)
        for(id in CapabilityId.entries)compose.onNodeWithTag("fusion-capability-$id").performScrollTo().assertTextContains("NON_CONNECTÉ · UNKNOWN",substring=true)
        capture("disconnected")
    }
    @Test fun exactConfirmationAndUnknownAreClearlyLabOnly() {
        val file=File(compose.activity.filesDir,"datastore/dashboard.json");val before=file.readBytes()
        openPreview();compose.onNodeWithTag("fusion-total").performScrollTo().assertTextContains("0.30",substring=true)
        compose.onNodeWithContentDescription("Simuler un ACK perdu").performScrollTo().performClick()
        click("fusion-prepare");compose.onNodeWithTag("fusion-confirm").assertExists().performClick()
        compose.onNodeWithTag("fusion-lab-status").performScrollTo().assertTextContains("UNKNOWN",substring=true)
        compose.onNodeWithTag("fusion-prepare").performScrollTo().assertIsNotEnabled()
        assertArrayEquals(before,file.readBytes())
        capture("unknown")
    }
    @Test fun cancellationDoesNotCommitAndRotationKeepsDraft() {
        openPreview();click("fusion-prepare");click("fusion-cancel")
        compose.onNodeWithTag("fusion-receipt").assertDoesNotExist()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("fusion-total").performScrollTo().assertTextContains("0.30",substring=true)
        click("fusion-revoke");compose.onNodeWithTag("fusion-lab-status").performScrollTo().assertTextContains("REFUSED",substring=true)
    }
    @Test fun portraitLandscapeRemainsScrollableAndAccessible() {
        openPreview()
        compose.onNodeWithContentDescription("Simuler un ACK perdu").performScrollTo().assertExists()
        compose.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        compose.waitForIdle()
        compose.onNodeWithTag("fusion-total").performScrollTo().assertTextContains("0.30",substring=true)
        click("fusion-prepare");click("fusion-cancel")
        compose.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        compose.waitForIdle();capture("portrait")
    }
    @Test fun committingSimulationDoesNotWriteFilesOrConsent() {
        val file=File(compose.activity.filesDir,"datastore/workspace-v1.json");val w=file.takeIf {it.exists()}?.readBytes()
        openPreview();click("fusion-prepare");click("fusion-confirm")
        compose.onNodeWithTag("fusion-lab-status").performScrollTo().assertTextContains("zéro fichier écrit",substring=true)
        compose.onNodeWithTag("fusion-prepare").performScrollTo().assertIsNotEnabled()
        if(w==null)assertFalse(file.exists()) else assertArrayEquals(w,file.readBytes())
        click("fusion-lab-toggle");compose.onNodeWithTag("fusion-receipt").assertDoesNotExist()
    }
}
