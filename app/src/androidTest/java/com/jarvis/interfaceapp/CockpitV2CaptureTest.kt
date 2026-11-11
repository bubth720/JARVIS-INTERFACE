package com.jarvis.interfaceapp

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class CockpitV2CaptureTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    @Before fun prepare() { ui.resetLayoutFixture() }
    private fun click(tag: String) { ui.reachCockpit(tag).performClick() }
    private fun rotate(portrait: Boolean) {
        ui.runOnIdle { ui.activity.requestedOrientation = if (portrait) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        ui.waitUntil(15000) { ui.activity.resources.configuration.orientation == if (portrait) Configuration.ORIENTATION_PORTRAIT else Configuration.ORIENTATION_LANDSCAPE }
        ui.waitForIdle()
    }
    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        ui.onNodeWithTag("cockpit-v2").assertIsDisplayed()
        // PixelCopy of the rendered Compose root synchronizes with its frame.
        val bitmap = ui.onRoot().captureToImage().asAndroidBitmap()
        val directory = File(ui.activity.getExternalFilesDir(null), "mission008").apply { mkdirs() }
        File(directory, name).outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
    @Test fun rotationKeepsDraftAndSphereWhileCapturingRealPortraitAndLandscape() {
        rotate(true); click("open-settings"); click("open-cockpit-v2")
        ui.onNodeWithTag("v2-knowledge-card").assertDoesNotExist()
        ui.onNodeWithTag("v2-sphere-art").assertExists()
        capture("portrait.png")
        ui.onNodeWithTag("v2-draft").performTextInput("ROTATION TEST ÉPHÉMÈRE")
        ui.onNodeWithTag("v2-draft").performImeAction()
        rotate(false)
        ui.onNodeWithTag("v2-draft").assertTextContains("ROTATION TEST ÉPHÉMÈRE")
        click("v2-return"); click("open-settings"); click("open-cockpit-v2")
        assertEquals("", ui.onNodeWithTag("v2-draft").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text)
        click("v2-demo-open")
        ui.onNodeWithTag("v2-knowledge-card").assertExists(); ui.onNodeWithTag("v2-sphere-art").assertExists()
        capture("landscape.png")
        click("v2-demo-close"); rotate(true)
        ui.onNodeWithTag("v2-knowledge-card").assertDoesNotExist(); assertEquals("", ui.onNodeWithTag("v2-draft").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text)
    }
}
