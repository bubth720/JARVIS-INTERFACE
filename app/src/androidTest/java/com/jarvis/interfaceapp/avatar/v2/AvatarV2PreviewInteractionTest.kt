package com.jarvis.interfaceapp.avatar.v2

import androidx.activity.ComponentActivity
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.jarvis.interfaceapp.ui.JarvisTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class AvatarV2PreviewInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val repository=AvatarPackRepository()
    private val packs=mutableMapOf<String,LoadedPremiumPack>()
    private var applied:LoadedPremiumPack?=null
    private var closed=false
    private fun host() {
        // Scroll semantics animate: keep this UI clock automatic. Motion invariants
        // use a separately controlled clock in PremiumAvatarAndroidTest.
        compose.mainClock.autoAdvance=true
        compose.setContent { JarvisTheme { AvatarV2Preview(repository,packs,{applied=it},{closed=true}) } }
        compose.waitUntil(30_000) { packs.isNotEmpty() }
        compose.mainClock.advanceTimeBy(100);compose.waitForIdle()
    }
    private fun frame() { compose.mainClock.advanceTimeBy(50);compose.waitForIdle() }
    @Test fun explicitConfirmationRequiredAndApplicationIsSessionOnly() {
        host()
        compose.onNodeWithTag("avatar-v2-art-status").assertTextEquals("PARTIAL · ART_ASSETS_MISSING")
        compose.onNodeWithTag("avatar-v2-apply").performScrollTo().assertIsNotEnabled()
        assertNull(repository.selectedId);assertNull(applied)
        compose.onNodeWithTag("avatar-v2-confirm").performScrollTo().performClick();frame()
        compose.onNodeWithTag("avatar-v2-apply").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertNotNull(applied);assertTrue(closed);assertEquals("jarvis.synthetic.v2.demo",repository.selectedId) }
        assertNull(AvatarPackRepository().selectedId)
    }
    @Test fun unsupportedStateShowsExplicitErrorAndDoesNotChangeRenderedState() {
        host()
        compose.onNodeWithTag("avatar-v2-state-SPEAKING").performScrollTo().performClick();frame()
        compose.onNodeWithTag("avatar-v2-issue").assertTextContains("ART_ASSET_UNAVAILABLE",substring=true)
        compose.onNodeWithTag("avatar-v2-current").assertTextContains("IDLE",substring=true)
        compose.onNodeWithTag("avatar-v2-mouth").performScrollTo().assertIsNotEnabled()
    }
    @Test fun comparisonRecreatesReleasedRendererAndCapturesActualComposeDemo() {
        host()
        compose.onNodeWithTag("avatar-v2-compare-v1").performScrollTo().performClick();frame()
        compose.onNodeWithText("V1 · secours vectoriel existant").assertExists()
        compose.onNodeWithTag("avatar-v2-compare-v2").performScrollTo().performClick();frame()
        compose.onNodeWithTag("avatar-renderer").performScrollTo().assertIsDisplayed()
        compose.mainClock.advanceTimeBy(100)
        val image=compose.onNodeWithTag("avatar-renderer").captureToImage().asAndroidBitmap()
        val out=java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),"ava03-demo-compose.png")
        out.outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        assertTrue(out.length()>0)
    }
}
