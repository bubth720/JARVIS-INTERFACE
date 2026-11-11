package com.jarvis.interfaceapp.cockpitv2

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import kotlin.math.min

internal val CockpitCyan = Color(0xFF76E9F6)
internal val CockpitViolet = Color(0xFFB6A0FF)
internal val CockpitInk = Color(0xFF070D1C)

/** Static adaptation of AVA-04 AvatarArtwork.kt::drawStaticAvatarFallback.
 * Source branch/commit is pinned in docs/mission-008/reuse-inventory.json.
 * Only the normalized fallback geometry is reused: no AvatarPose, engine or clock.
 */
@Composable
fun StaticAvatarPreview(modifier: Modifier = Modifier) {
    Canvas(modifier.clipToBounds().testTag("v2-avatar-art").clearAndSetSemantics {
        contentDescription = "Avatar féminin : visuel provisoire statique, aucun moteur chargé."
    }) {
        val factor = min(size.width / 240f, size.height / 320f)
        withTransform({
            translate((size.width - 240f * factor) / 2f, (size.height - 320f * factor) / 2f)
            scale(factor, factor, Offset.Zero)
        }) {
            drawRect(Color(0xFF101C2C), size = Size(240f, 320f))
            drawCircle(CockpitCyan.copy(alpha = .08f), 118f, Offset(120f, 125f))
            drawOval(Color(0xFF302326), Offset(65f, 24f), Size(110f, 210f))
            drawOval(Color(0xFF263B5A), Offset(30f, 210f), Size(180f, 180f))
            drawOval(Color(0xFFE5B69D), Offset(83f, 61f), Size(74f, 115f))
            drawLine(Color(0xFF302326), Offset(96f, 116f), Offset(107f, 116f), 2f)
            drawLine(Color(0xFF302326), Offset(133f, 116f), Offset(144f, 116f), 2f)
            drawLine(CockpitCyan.copy(alpha = .6f), Offset(72f, 267f), Offset(168f, 267f), 2f)
        }
    }
}

/** Replaceable 2D slot; deliberately static. Not a 3D engine or rotation simulation. */
@Composable
fun StaticKnowledgeSphere(modifier: Modifier = Modifier) {
    Canvas(modifier.testTag("v2-sphere-art").clearAndSetSemantics {
        contentDescription = "Sphère de connaissances : illustration statique 2D provisoire, sans rotation 3D."
    }) {
        val radius = min(size.width, size.height) * .40f
        val c = center
        drawCircle(Brush.radialGradient(listOf(CockpitCyan.copy(alpha = .14f), Color.Transparent), c, radius * 1.24f), radius * 1.24f, c)
        drawCircle(CockpitCyan.copy(alpha = .8f), radius, c, style = Stroke(1.5f))
        listOf(.28f, .58f, .84f).forEach { fraction ->
            drawOval(CockpitCyan.copy(alpha = .32f), Offset(c.x - radius * fraction, c.y - radius), Size(radius * fraction * 2, radius * 2), style = Stroke(1f))
            drawOval(CockpitCyan.copy(alpha = .32f), Offset(c.x - radius, c.y - radius * fraction), Size(radius * 2, radius * fraction * 2), style = Stroke(1f))
        }
        val points = listOf(Offset(-.55f, -.48f), Offset(.52f, -.38f), Offset(-.72f, .28f), Offset(.26f, .58f), Offset(.05f, -.12f))
        points.forEachIndexed { index, p ->
            val at = c + p * radius
            drawLine(CockpitViolet.copy(alpha = .45f), c + points[(index + 1) % points.size] * radius, at, 1f)
            drawCircle(CockpitCyan, radius * .025f, at)
        }
        drawOval(CockpitViolet.copy(alpha = .4f), Offset(c.x - radius * 1.18f, c.y + radius * .95f), Size(radius * 2.36f, radius * .22f), style = Stroke(1.2f))
    }
}
