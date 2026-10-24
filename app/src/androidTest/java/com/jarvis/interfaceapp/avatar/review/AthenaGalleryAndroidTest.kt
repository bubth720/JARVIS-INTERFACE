package com.jarvis.interfaceapp.avatar.review

import android.os.Build
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.jarvis.interfaceapp.avatar.assets.ResourceLimit
import com.jarvis.interfaceapp.avatar.assets.readBounded
import com.jarvis.interfaceapp.avatar.v2.*
import com.jarvis.interfaceapp.ui.JarvisTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Public regression uses synthetic assets. Optional private review is emulator-only,
 * read from a deliberately provisioned file, never from APK resources or CI artifacts. */
class AthenaGalleryAndroidTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun galleryIsReviewOnlyAndUsesTheRealV2Validator() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val privateReview=InstrumentationRegistry.getArguments().getString("privateArtReview")=="true"
        val validated=if(privateReview) {
            require(Build.HARDWARE in setOf("ranchu","goldfish")) { "PRIVATE_REVIEW_EMULATOR_ONLY" }
            PremiumPackLoader.validate(PremiumZipSource(File(context.filesDir,"ava04-private-review.zip").inputStream()))
        } else PremiumPackLoader.validate(context.assets.open("avatar-v2-demo/manifest.json").use { it.readBytes() }) { file ->
            context.assets.open("avatar-demo/$file").use { it.readBounded(ResourceLimit.FILE_BYTES) }
        }
        val pack=PremiumPackLoader.decode(validated)
        if(privateReview) {
            assertEquals(12,pack.manifest.resources.size)
            assertEquals(PremiumState.entries.toSet(),pack.manifest.frames.map { it.key.state }.toSet())
            assertTrue(pack.manifest.frames.all { it.mode==RenderMode.IMAGE_REPLACEMENT })
        }
        var closed=false
        compose.setContent { JarvisTheme { AthenaGalleryScreen(pack) { closed=true } } }
        compose.onNodeWithTag("athena-review-gallery").assertIsDisplayed()
        compose.onNodeWithTag("athena-review-status").assertTextContains("ART_REVIEW_PENDING",substring=true)
        compose.onNodeWithText("Publier").assertDoesNotExist()
        if(privateReview) {
            val directory=File(context.filesDir,"ava04-review-captures").apply { mkdirs() }
            fun capture(label:String) {
                compose.waitForIdle()
                val screenshot=requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                File(directory,"$label-${screenshot.width}x${screenshot.height}.png").outputStream().use {
                    assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG,100,it))
                }
                screenshot.recycle()
            }
            capture("gallery")
            for(frame in pack.manifest.frames) {
                val label="${frame.key.state}-${frame.key.expression}-${frame.key.perspective}"
                compose.onNodeWithTag("athena-review-$label").performScrollTo().assertIsDisplayed()
                capture(label)
            }
        }
        compose.onNodeWithTag("athena-review-close").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(closed) }
    }
}
