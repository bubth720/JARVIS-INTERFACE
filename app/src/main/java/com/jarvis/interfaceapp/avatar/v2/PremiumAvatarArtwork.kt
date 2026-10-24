package com.jarvis.interfaceapp.avatar.v2

import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.jarvis.interfaceapp.avatar.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

/** Optional frame-clock extension; existing V1 renderer/controller contracts stay intact. */
interface FrameDrivenAvatarArtwork : AvatarArtwork {
    val pendingFrames: StateFlow<Boolean>
    fun advanceBy(deltaMillis: Double)
}
class LoadedPremiumPack(val manifest: PremiumAvatarManifest, val textures: Map<String, ImageBitmap>) {
    val decodedBytes: Long get() = manifest.resources.sumOf { it.width.toLong() * it.height * 4 }
}
object PremiumPackLoader {
    private val inspector = PngInspector { bytes ->
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) { "CORRUPT_PNG" }
        try {
            var transparent = false; var visible = false
            val row = IntArray(bitmap.width)
            for (y in 0 until bitmap.height) {
                bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
                for (pixel in row) { val a = pixel ushr 24; if (a < 255) transparent = true; if (a > 0) visible = true }
            }
            require(visible) { "EMPTY_IMAGE" }
            PngFacts(bitmap.width, bitmap.height, transparent)
        } finally { bitmap.recycle() }
    }
    fun validate(zip: PremiumZipSource): ValidatedPremiumPack = PremiumAssetValidator(inspector).validate(zip)
    fun validate(manifest: ByteArray, read: (String) -> ByteArray): ValidatedPremiumPack =
        PremiumAssetValidator(inspector).validate(PremiumManifestCodec.decode(manifest), read)
    /** Off-main once per load. No decoding in render, state changes or animation frames. */
    fun decode(pack: ValidatedPremiumPack): LoadedPremiumPack = LoadedPremiumPack(pack.manifest,
        pack.bytes.mapValues { (_, bytes) -> requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)).asImageBitmap() })
}

class PremiumAvatarArtwork(pack: LoadedPremiumPack) : FrameDrivenAvatarArtwork {
    private var pack: LoadedPremiumPack? = pack
    override val resourceId = pack.manifest.id
    private val machine = AvatarExpressionStateMachine(pack.manifest)
    private val pending = MutableStateFlow(false)
    override val pendingFrames: StateFlow<Boolean> = pending
    private var old = PremiumKey()
    private var blend by mutableFloatStateOf(1f)
    private var mouth by mutableFloatStateOf(0f)
    private var mouthTarget = 0f
    val current: PremiumKey get() = machine.current
    val transitioning: Boolean get() = blend < 1f
    fun dispatch(event: ExpressionEvent): ExpressionResult {
        if (pack == null) return ExpressionResult(false, current, "ARTWORK_RELEASED")
        // Serialize transitions: refusal keeps the exact previous blend, without a visual jump.
        if (transitioning) return ExpressionResult(false, current, "TRANSITION_IN_PROGRESS")
        val previous = current
        val result = machine.dispatch(event)
        if (result.accepted && previous != result.key) { old = previous; blend = 0f; pending.value = true }
        return result
    }
    fun simulateMouth(open: Boolean): Boolean {
        val frame = pack?.manifest?.resolve(current) ?: return false
        val supports = pack!!.manifest.layers.any { it.id in frame.layers && it.channel == LayerChannel.MOUTH_OPEN }
        if (!supports) return false
        mouthTarget = if (open) 1f else 0f
        pending.value = transitioning || mouth != mouthTarget
        return true
    }
    override fun advanceBy(deltaMillis: Double) {
        if (pack == null || !deltaMillis.isFinite() || deltaMillis <= 0) return
        blend = (blend + deltaMillis.coerceAtMost(100.0).toFloat() / 250f).coerceAtMost(1f)
        val step = deltaMillis.coerceAtMost(100.0).toFloat() / 150f
        mouth = if (mouth < mouthTarget) (mouth + step).coerceAtMost(mouthTarget) else (mouth - step).coerceAtLeast(mouthTarget)
        pending.value = transitioning || mouth != mouthTarget
    }
    override fun DrawScope.render(pose: AvatarPose) {
        val p = checkNotNull(pack) { "ARTWORK_RELEASED" }
        val t = blend * blend * (3 - 2 * blend)
        // Both poses use the same head pivot and input pose during the bounded crossfade.
        if (t < 1f) renderFrame(p, old, pose, 1 - t)
        renderFrame(p, current, pose, t)
    }
    private fun DrawScope.renderFrame(p: LoadedPremiumPack, key: PremiumKey, pose: AvatarPose, opacity: Float) {
        if (opacity <= 0) return
        val frame = requireNotNull(p.manifest.resolve(key))
        for (layer in p.manifest.layers) {
            if (layer.id !in frame.layers) continue
            val alpha = opacity * when (layer.channel) {
                LayerChannel.EYES_OPEN -> 1 - pose.eyelid.coerceIn(0f, 1f)
                LayerChannel.EYES_CLOSED -> pose.eyelid.coerceIn(0f, 1f)
                LayerChannel.MOUTH_NEUTRAL -> 1 - mouth
                LayerChannel.MOUTH_OPEN -> mouth
                else -> 1f
            }
            val tilt = when (layer.channel) {
                LayerChannel.STATIC, LayerChannel.BODY -> 0f
                LayerChannel.BACK_HAIR -> pose.headTilt * layer.depth
                else -> pose.headTilt
            }
            val dy = if (layer.channel == LayerChannel.STATIC) 0f else -2f * pose.breath
            withTransform({ translate(0f, dy); rotate(tilt, Offset(layer.pivot.x, layer.pivot.y)) }) {
                drawImage(p.textures.getValue(layer.resource), dstOffset = IntOffset(layer.x.roundToInt(), layer.y.roundToInt()),
                    dstSize = IntSize(layer.width.roundToInt().coerceAtLeast(1), layer.height.roundToInt().coerceAtLeast(1)), alpha = alpha)
            }
        }
    }
    override fun release() { pack = null; pending.value = false }
}
