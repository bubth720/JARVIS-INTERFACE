package com.jarvis.interfaceapp.avatar.assets

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.jarvis.interfaceapp.avatar.*
import kotlin.math.roundToInt

class LayeredAvatarArtwork(asset: LayeredAvatarAsset) : AvatarArtwork {
    private var asset: LayeredAvatarAsset? = asset
    override val resourceId = asset.manifest.id
    var variants: Map<String, String> = asset.manifest.layers.groupBy { it.group }.mapValues { it.value.first().variant }
    var showPivots = false
    override fun DrawScope.render(pose: AvatarPose) {
        val current = checkNotNull(asset) { "ARTWORK_RELEASED" }
        drawRect(Color(0xFF101C2C))
        for (layer in current.manifest.layers) {
            if (variants[layer.group]?.let { it != layer.variant } == true) continue
            val transform = PoseTransform.forLayer(layer, pose)
            withTransform({ translate(0f, transform.dy); rotate(transform.rotation, Offset(layer.pivot.x, layer.pivot.y)) }) {
                val mask = layer.mask ?: listOf(0f, 0f, 240f, 320f)
                val eyelid = if (layer.id == "eyes") pose.eyelid.coerceIn(0f, 1f) else 0f
                val mid = (mask[1] + mask[3]) / 2f
                val top = mask[1] + (mid - mask[1]) * eyelid
                val bottom = mask[3] - (mask[3] - mid) * eyelid
                clipRect(mask[0], maxOf(mask[1], minOf(top, bottom)), mask[2], minOf(mask[3], maxOf(top, bottom))) {
                    drawImage(current.textures.getValue(layer.file), dstOffset = IntOffset(layer.x.roundToInt(), layer.y.roundToInt()),
                        dstSize = IntSize(layer.width.roundToInt().coerceAtLeast(1), layer.height.roundToInt().coerceAtLeast(1)))
                    if (showPivots) drawCircle(Color.Cyan, 2f, Offset(layer.pivot.x, layer.pivot.y))
                }
            }
        }
    }
    /** Drop this host's bounded texture set; never recycle a bitmap still referenced by Compose. */
    override fun release() { asset = null }
}
