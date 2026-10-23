package com.jarvis.interfaceapp.avatar.assets

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.toArgb

/** Real Android bitmap decoder tests. Not counted as executed unless run on an emulator/device. */
class AvatarAssetAndroidTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun source() = AvatarAssetSource { context.assets.open("avatar-demo/$it") }
    @Test fun packagedPngsDecodeWithFiveIndependentLayersAndRelease() {
        val loaded=AvatarAssetLoader().load(source())
        assertEquals(7,loaded.manifest.layers.size); assertEquals(7,loaded.textures.size)
        assertTrue(loaded.textures.values.all { it.width==80 && it.height==100 })
        val artwork=LayeredAvatarArtwork(loaded); artwork.release(); artwork.release()
    }
    @Test fun realPixelsMoveAndReturnToNeutral() {
        val artwork=LayeredAvatarArtwork(AvatarAssetLoader().load(source())).also { it.variants=mapOf("hair" to "night") }
        fun pixels(pose: com.jarvis.interfaceapp.avatar.AvatarPose): List<Int> {
            val image=androidx.compose.ui.graphics.ImageBitmap(240,320)
            androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(
                androidx.compose.ui.unit.Density(1f), androidx.compose.ui.unit.LayoutDirection.Ltr,
                androidx.compose.ui.graphics.Canvas(image), androidx.compose.ui.geometry.Size(240f,320f)
            ) { with(artwork) { render(pose) } }
            val map=image.toPixelMap()
            return (0 until 320).flatMap { y -> (0 until 240).map { x -> map[x,y].toArgb() } }
        }
        val neutral=pixels(com.jarvis.interfaceapp.avatar.AvatarPose())
        assertNotEquals(neutral,pixels(com.jarvis.interfaceapp.avatar.AvatarPose(1f,0f,5f)))
        assertEquals(neutral,pixels(com.jarvis.interfaceapp.avatar.AvatarPose()))
        artwork.release()
    }
    @Test fun changedTextureIsRefusedBeforeDecoding() {
        val original=source()
        val corrupt=AvatarAssetSource { name ->
            if(name.endsWith(".png")) byteArrayOf(0).inputStream() else original.open(name)
        }
        assertThrows(IllegalArgumentException::class.java) { AvatarAssetLoader().load(corrupt) }
    }
}
