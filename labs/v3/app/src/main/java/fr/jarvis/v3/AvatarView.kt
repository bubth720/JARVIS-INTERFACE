package fr.jarvis.v3

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import fr.jarvis.v3.model.AvatarExpression
import fr.jarvis.v3.model.HoloState

/**
 * 2D persona avatar (Command Center V2 §2/§5): an ORIGINAL holographic line portrait drawn procedurally — no image
 * file, no personal picture, no copied character. Expressions follow JARVIS' real state. Same rule as the sphere
 * (owner decision 14/11): still at rest, while listening and thinking; only the mouth follows the voice level while
 * JARVIS answers. Nothing here runs on a timer: it is redrawn only when the state or the voice level changes.
 */
@Composable
fun AvatarView(state: HoloState, voiceLevel: Float, size: Dp, modifier: Modifier = Modifier) {
    val expression = AvatarExpression.of(state)
    val mouth = if (expression == "parle") (0.15f + 0.85f * voiceLevel.coerceIn(0f, 1f)) else 0f
    Canvas(modifier.size(size).testTag("avatar").semantics { contentDescription = "Avatar d'Athéna, expression $expression" }) {
        drawPortrait(expression, mouth)
    }
}

private fun DrawScope.drawPortrait(expression: String, mouth: Float) {
    val w = size.width; val h = size.height
    val line = Stroke(width = w * 0.022f, cap = StrokeCap.Round)
    val c = Holo.cyan; val soft = Holo.ice.copy(alpha = 0.85f); val dim = Holo.cyan.copy(alpha = 0.35f)
    // Halo ring (static).
    drawCircle(dim, w * 0.47f, Offset(w / 2, h / 2), style = Stroke(w * 0.012f))
    // Shoulders and neck.
    val bust = Path().apply { moveTo(w * 0.16f, h * 0.98f); quadraticBezierTo(w * 0.22f, h * 0.74f, w * 0.42f, h * 0.70f)
        lineTo(w * 0.44f, h * 0.62f); moveTo(w * 0.56f, h * 0.62f); lineTo(w * 0.58f, h * 0.70f)
        quadraticBezierTo(w * 0.78f, h * 0.74f, w * 0.84f, h * 0.98f) }
    drawPath(bust, dim, style = line)
    // Face (oval) and hair: long side strands and a parting — a female figure, as the owner described Athéna.
    drawOval(soft, Offset(w * 0.33f, h * 0.18f), Size(w * 0.34f, h * 0.46f), style = line)
    val hair = Path().apply { moveTo(w * 0.50f, h * 0.14f); cubicTo(w * 0.24f, h * 0.10f, w * 0.20f, h * 0.40f, w * 0.26f, h * 0.70f)
        moveTo(w * 0.50f, h * 0.14f); cubicTo(w * 0.76f, h * 0.10f, w * 0.80f, h * 0.40f, w * 0.74f, h * 0.70f)
        moveTo(w * 0.50f, h * 0.14f); quadraticBezierTo(w * 0.42f, h * 0.22f, w * 0.34f, h * 0.30f) }
    drawPath(hair, c, style = line)
    // Eyes: open ovals; pensive = looking up; attentive = slightly wider; préoccupée = lowered brows.
    val eyeY = h * (if (expression == "pensive") 0.355f else 0.375f)
    val eyeH = h * (if (expression == "attentive") 0.045f else 0.032f)
    for (x in listOf(0.42f, 0.58f)) {
        drawOval(c, Offset(w * x - w * 0.035f, eyeY - eyeH / 2), Size(w * 0.07f, eyeH), style = Stroke(w * 0.016f))
        drawCircle(c, w * 0.011f, Offset(w * x, eyeY - if (expression == "pensive") eyeH * 0.2f else 0f))
    }
    val browTilt = if (expression == "préoccupée") 0.012f else -0.006f
    drawLine(soft, Offset(w * 0.38f, h * 0.325f - h * browTilt), Offset(w * 0.46f, h * 0.315f), strokeWidth = w * 0.014f, cap = StrokeCap.Round)
    drawLine(soft, Offset(w * 0.54f, h * 0.315f), Offset(w * 0.62f, h * 0.325f - h * browTilt), strokeWidth = w * 0.014f, cap = StrokeCap.Round)
    // Mouth: a soft smile at rest (slightly mischievous), opening with the real voice level while speaking.
    if (mouth > 0f) drawOval(c, Offset(w * 0.455f, h * 0.505f), Size(w * 0.09f, h * (0.012f + 0.05f * mouth)), style = Stroke(w * 0.016f))
    else {
        val smile = Path().apply { moveTo(w * 0.45f, h * 0.51f)
            quadraticBezierTo(w * 0.51f, h * (if (expression == "préoccupée") 0.50f else 0.535f), w * 0.56f, h * 0.505f) }
        drawPath(smile, c, style = Stroke(w * 0.016f, cap = StrokeCap.Round))
    }
}

/** Shared colour for the avatar zone frame. */
val AvatarFrame: Color get() = Holo.cyan.copy(alpha = 0.4f)
