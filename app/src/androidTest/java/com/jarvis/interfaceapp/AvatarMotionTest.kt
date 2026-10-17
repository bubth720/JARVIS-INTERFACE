package com.jarvis.interfaceapp

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jarvis.interfaceapp.avatar.*
import com.jarvis.interfaceapp.ui.JarvisTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Explicit clock: verifies real frame advancement/drawing, not just an active-state label. */
@OptIn(ExperimentalTestApi::class)
class AvatarMotionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }
    private val owner = Owner()
    private val controller = AvatarAnimationController { 10_000 }
    private var hidden by mutableStateOf(false)
    private var shown by mutableStateOf(true)
    private var width by mutableStateOf(300)
    private fun host(artwork: AvatarArtwork = DemoAvatarArtwork(), actualLifecycle: Boolean = false) {
        compose.mainClock.autoAdvance = false
        if (!actualLifecycle) compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides if (actualLifecycle) compose.activity else owner) {
                JarvisTheme {
                    Box(Modifier.fillMaxSize().clipToBounds()) {
                        if (shown) AvatarDemo(Modifier.width(width.dp).offset(y = if (hidden) 2000.dp else 0.dp), controller, artwork)
                    }
                }
            }
        }
        advance(100)
    }
    private fun advance(ms: Long) { compose.mainClock.advanceTimeBy(ms); compose.waitForIdle() }
    private fun command(c: AvatarCommand) { compose.runOnIdle { controller.execute(c) } }
    private fun stop() { AvatarAnimationId.entries.forEach { command(AvatarCommand.Stop(it)) }; advance(500) }
    private fun pixels(): Int {
        val image = compose.onNodeWithTag("avatar-renderer").captureToImage().asAndroidBitmap()
        val values = IntArray(image.width * image.height)
        image.getPixels(values, 0, image.width, 0, 0, image.width, image.height)
        return values.contentHashCode()
    }
    @Test fun eachAnimationChangesRenderedPixelsAndStopsAtNeutral() {
        host(); stop(); val neutral = pixels()
        command(AvatarCommand.Play(AvatarAnimationId.IDLE)); advance(1000)
        compose.runOnIdle { assertTrue(controller.pose.breath > 0) }
        assertNotEquals(neutral, pixels()); stop(); assertEquals(neutral, pixels())
        command(AvatarCommand.Play(AvatarAnimationId.BLINK)); advance(80)
        compose.runOnIdle { assertTrue(controller.pose.eyelid > 0) }
        assertNotEquals(neutral, pixels()); advance(400); assertEquals(neutral, pixels())
        command(AvatarCommand.Play(AvatarAnimationId.HEAD_TILT)); advance(600)
        compose.runOnIdle { assertTrue(kotlin.math.abs(controller.pose.headTilt) > 0) }
        assertNotEquals(neutral, pixels()); advance(2200); assertEquals(neutral, pixels())
    }
    @Test fun clippedOffscreenSurfaceFreezesActualFrameCounterThenResumes() {
        host(); advance(500)
        compose.runOnIdle { assertTrue(controller.framesAdvanced > 0); hidden = true }
        advance(50) // Apply the layout change before measuring an actually clipped surface.
        compose.onNodeWithTag("avatar-renderer").assertIsNotDisplayed()
        compose.runOnIdle { assertFalse(controller.status.value.advancing) }
        val before = compose.runOnIdle { controller.framesAdvanced to controller.pose }
        advance(1500)
        compose.runOnIdle {
            assertFalse(controller.status.value.advancing)
            assertEquals(before, controller.framesAdvanced to controller.pose)
            hidden = false
        }
        advance(500)
        compose.runOnIdle { assertTrue(controller.framesAdvanced > before.first) }
    }
    @Test fun backgroundCancelsClockEvenWhenLifecycleFlowCollectionIsStopped() {
        host(); advance(500)
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.CREATED }
        compose.waitForIdle()
        val before = compose.runOnIdle { controller.framesAdvanced to controller.pose }
        advance(1500)
        compose.runOnIdle { assertEquals(before, controller.framesAdvanced to controller.pose) }
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        advance(500)
        compose.runOnIdle { assertTrue(controller.framesAdvanced > before.first) }
    }
    @Test fun realAndroidActivityStopAndResumeSuspendActualCalculations() {
        host(actualLifecycle = true); advance(500)
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        var count = 0L
        var frozen = AvatarPose()
        compose.runOnUiThread {
            assertFalse(controller.status.value.advancing)
            count = controller.framesAdvanced; frozen = controller.pose
        }
        compose.mainClock.advanceTimeBy(1500)
        compose.runOnUiThread { assertEquals(count, controller.framesAdvanced); assertEquals(frozen, controller.pose) }
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        advance(500)
        compose.runOnIdle { assertTrue(controller.framesAdvanced > count) }
    }
    @Test fun resizingKeepsTheSameControllerAndContinuesDrawing() {
        host(); advance(500)
        val before = compose.runOnIdle { width = 200; controller.pose }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(before, controller.pose); assertFalse(controller.status.value.released) }
        val count = compose.runOnIdle { controller.framesAdvanced }
        advance(500)
        compose.runOnIdle { assertTrue(controller.framesAdvanced > count) }
        compose.onNodeWithTag("avatar-renderer").assertWidthIsEqualTo(200.dp)
    }
    @Test fun disposalReleasesControllerAndStopsFramesPermanently() {
        host(); advance(500); compose.runOnIdle { shown = false }; advance(50)
        val count = compose.runOnIdle { controller.framesAdvanced }
        advance(1500)
        compose.runOnIdle {
            assertTrue(controller.status.value.released)
            assertFalse(controller.status.value.advancing); assertEquals(count, controller.framesAdvanced)
        }
    }
    @Test fun rendererFailureLatchesAStaticFallbackWithoutRepeatedExceptions() {
        var calls = 0
        var releases = 0
        host(object : AvatarArtwork {
            override val resourceId = "test.failure"
            override fun release() { releases++ }
            override fun androidx.compose.ui.graphics.drawscope.DrawScope.render(pose: AvatarPose) {
                calls++; throw IllegalStateException("Injected resource failure")
            }
        })
        val before = compose.runOnIdle { assertTrue(calls > 0); calls }
        advance(1500)
        compose.runOnIdle { assertEquals(before, calls); assertFalse(controller.status.value.advancing) }
        compose.onNodeWithTag("avatar-renderer").assertIsDisplayed()
        assertNotEquals(0, pixels())
        compose.runOnIdle { shown = false }; advance(50)
        compose.runOnIdle { assertEquals(1, releases) }
    }
}
