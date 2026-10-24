package com.jarvis.interfaceapp.avatar.v2

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.*
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import com.jarvis.interfaceapp.avatar.*
import com.jarvis.interfaceapp.avatar.assets.ResourceLimit
import com.jarvis.interfaceapp.avatar.assets.readBounded
import com.jarvis.interfaceapp.ui.JarvisTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class PremiumAvatarAndroidTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun validated(): ValidatedPremiumPack = PremiumPackLoader.validate(
        context.assets.open("avatar-v2-demo/manifest.json").use { it.readBytes() }) { file ->
        context.assets.open("avatar-demo/$file").use { it.readBounded(ResourceLimit.FILE_BYTES) }
    }
    private fun pack(): LoadedPremiumPack = PremiumPackLoader.decode(validated())
    private fun twoFrames(): LoadedPremiumPack = pack().let { p ->
        // Test-only layer combination, deliberately not called a premium expression.
        p.let { LoadedPremiumPack(it.manifest.copy(frames=it.manifest.frames + it.manifest.frames.single().copy(
            key=PremiumKey(PremiumState.REACTION), layers=listOf("body"))),it.textures) }
    }
    private fun pixels(art:AvatarArtwork,pose:AvatarPose=AvatarPose()):Int {
        val image=ImageBitmap(240,320)
        CanvasDrawScope().draw(Density(1f),LayoutDirection.Ltr,Canvas(image),androidx.compose.ui.geometry.Size(240f,320f)) {
            with(art) { render(pose) }
        }
        val m=image.toPixelMap();return (0 until 320).flatMap { y -> (0 until 240).map { x -> m[x,y].toArgb() } }.hashCode()
    }
    @Test fun realPngDecodeHasBoundedResidencyAndNoFalseArtReadiness() {
        val p=pack();assertEquals(5,p.textures.size);assertEquals(160000L,p.decodedBytes)
        assertTrue(p.textures.values.all { it.width==80 && it.height==100 });assertFalse(p.manifest.completeCandidate)
    }
    @Test fun liveLayerTransformsChangePixelsAndReturnExactlyToNeutral() {
        val art=PremiumAvatarArtwork(pack());val a=pixels(art)
        assertNotEquals(a,pixels(art,AvatarPose(1f,0f,5f)));assertEquals(a,pixels(art));art.release()
        assertThrows(Exception::class.java) { pixels(art) }
    }
    @Test fun actualPixelCrossfadeIsContinuousRefusesInterruptionsAndResets() {
        val art=PremiumAvatarArtwork(twoFrames());val a=pixels(art)
        assertTrue(art.dispatch(ExpressionEvent.State(PremiumState.REACTION)).accepted)
        assertEquals(a,pixels(art));art.advanceBy(100.0);val halfway=pixels(art);assertNotEquals(a,halfway)
        assertFalse(art.dispatch(ExpressionEvent.Reset).accepted);assertEquals(halfway,pixels(art))
        repeat(2) { art.advanceBy(100.0) };val b=pixels(art);assertNotEquals(halfway,b)
        assertTrue(art.dispatch(ExpressionEvent.Reset).accepted);repeat(3) { art.advanceBy(100.0) };assertEquals(a,pixels(art))
    }
    @Test fun missingArtCannotChangeStateOrSimulatePhonemes() {
        val art=PremiumAvatarArtwork(pack());val a=pixels(art)
        assertFalse(art.dispatch(ExpressionEvent.State(PremiumState.SPEAKING)).accepted)
        assertFalse(art.simulateMouth(true));assertEquals(a,pixels(art));assertEquals(PremiumKey(),art.current)
    }
    @Test fun realDecoderRejectsOpaqueAndCorruptResourcesEvenWhenHashMatches() {
        val bitmap=android.graphics.Bitmap.createBitmap(80,100,android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.BLUE)
        val out=java.io.ByteArrayOutputStream();bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);bitmap.recycle()
        val b=out.toByteArray();val text=context.assets.open("avatar-v2-demo/manifest.json").bufferedReader().use { it.readText() }
        val original=PremiumManifestCodec.decode(text.toByteArray()).resources.first()
        val changed=text.replace(original.sha256,PremiumAssetValidator.sha256(b))
        assertThrows(Exception::class.java) { PremiumPackLoader.validate(changed.toByteArray()) { file ->
            if(file==original.file) b else context.assets.open("avatar-demo/$file").use { it.readBytes() }
        } }
    }
    @Test fun articulatedEyesAndSimulatedMouthUseSeparateLayersAndReturnToNeutral() {
        // Tiny colored RGBA test textures, never production portraits or claimed art assets.
        val source=pack().manifest
        val channels=listOf(LayerChannel.HEAD,LayerChannel.EYES_OPEN,LayerChannel.EYES_CLOSED,
            LayerChannel.MOUTH_NEUTRAL,LayerChannel.MOUTH_OPEN)
        val textures=channels.mapIndexed { index, channel ->
            val bitmap=android.graphics.Bitmap.createBitmap(16,16,android.graphics.Bitmap.Config.ARGB_8888)
            val canvas=android.graphics.Canvas(bitmap)
            val paint=android.graphics.Paint().apply { color=listOf(android.graphics.Color.BLUE,android.graphics.Color.WHITE,
                android.graphics.Color.CYAN,android.graphics.Color.RED,android.graphics.Color.GREEN)[index] }
            canvas.drawRect(2f,2f,14f,14f,paint)
            channel.name to bitmap.asImageBitmap()
        }.toMap()
        val layers=channels.mapIndexed { index,c -> source.layers.first().copy(id=c.name,resource=c.name,order=index,
            x=if(c.name.startsWith("MOUTH")) 110f else 80f,y=if(c.name.startsWith("MOUTH")) 140f else 80f,
            width=40f,height=20f,channel=c,depth=1f) }
        val manifest=source.copy(layers=layers,frames=listOf(PremiumFrame(PremiumKey(),layers.map { it.id },RenderMode.LAYERS)))
        val art=PremiumAvatarArtwork(LoadedPremiumPack(manifest,textures));val neutral=pixels(art)
        assertNotEquals(neutral,pixels(art,AvatarPose(0f,1f,0f)));assertEquals(neutral,pixels(art))
        assertTrue(art.simulateMouth(true));repeat(2) { art.advanceBy(100.0) };assertNotEquals(neutral,pixels(art))
        assertTrue(art.simulateMouth(false));repeat(2) { art.advanceBy(100.0) };assertEquals(neutral,pixels(art))
        assertFalse(art.pendingFrames.value);art.release()
    }
    private val controller=AvatarAnimationController { 10_000 }
    private var hidden by mutableStateOf(false)
    private fun host(art:PremiumAvatarArtwork) {
        compose.mainClock.autoAdvance=false
        compose.setContent { JarvisTheme {
            Box(Modifier.fillMaxSize().clipToBounds()) { AvatarDemo(Modifier.width(280.dp).offset(y=if(hidden) 2000.dp else 0.dp),controller,art,showAssetButton=false) }
        } }
        advance(100)
        compose.runOnIdle { AvatarAnimationId.entries.forEach { controller.execute(AvatarCommand.Stop(it)) } }
        advance(500)
    }
    private fun advance(ms:Long) { compose.mainClock.advanceTimeBy(ms);compose.waitForIdle() }
    @Test fun pendingFadeGetsExistingClockEvenWhenAllIdleAnimationsStopped() {
        val art=PremiumAvatarArtwork(twoFrames());host(art)
        compose.runOnIdle { assertFalse(controller.status.value.advancing);assertTrue(art.dispatch(ExpressionEvent.State(PremiumState.REACTION)).accepted) }
        advance(400);compose.runOnIdle { assertFalse(art.transitioning);assertFalse(art.pendingFrames.value) }
    }
    @Test fun pendingFadeIsSuspendedOffscreenAndResumes() {
        val art=PremiumAvatarArtwork(twoFrames());host(art)
        compose.runOnIdle { hidden=true };advance(50)
        compose.onNodeWithTag("avatar-renderer").assertIsNotDisplayed()
        compose.runOnIdle { art.dispatch(ExpressionEvent.State(PremiumState.REACTION)) }
        advance(2000);compose.runOnIdle { assertTrue(art.transitioning) }
        compose.runOnIdle { hidden=false };advance(400);compose.runOnIdle { assertFalse(art.transitioning) }
    }
    @Test fun pendingFadeIsSuspendedWithActualActivityInBackground() {
        val art=PremiumAvatarArtwork(twoFrames());host(art)
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.runOnUiThread { art.dispatch(ExpressionEvent.State(PremiumState.REACTION)) }
        compose.mainClock.advanceTimeBy(2000)
        compose.runOnUiThread { assertTrue(art.transitioning) }
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED);advance(400)
        compose.runOnIdle { assertFalse(art.transitioning) }
    }
}
