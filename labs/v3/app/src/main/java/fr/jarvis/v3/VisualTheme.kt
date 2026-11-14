package fr.jarvis.v3

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.jarvis.v3.model.HoloState

/**
 * V3 design tokens (§02). Colours are the production hypotheses of the specification, to be tuned
 * visually: background #050B16, surface #0A1A2C, cyan #00D9FF, blue #168BFF, ice #B6EFFF,
 * gold #FFBE55 (rare), text #F1F8FF. Amber is the temporary error accent.
 */
object Holo {
    val background = Color(0xFF050B16)
    val surface = Color(0xFF0A1A2C)
    val cyan = Color(0xFF00D9FF)
    val blue = Color(0xFF168BFF)
    val ice = Color(0xFFB6EFFF)
    val gold = Color(0xFFFFBE55)
    val text = Color(0xFFF1F8FF)
    val muted = Color(0xFF9DB4CC)      // 7.9:1 on background
    val amber = Color(0xFFFFB347)
    val violet = Color(0xFF7FA8FF)

    /** Dominant light of each state; cyan at heart, amber only for errors, gold stays rare. */
    fun stateColor(s: HoloState): Color = when (s) {
        HoloState.IDLE -> cyan; HoloState.LISTENING -> ice; HoloState.TRANSCRIBING -> cyan
        HoloState.THINKING -> violet; HoloState.STREAMING -> cyan; HoloState.SPEAKING -> ice; HoloState.RESPONDING -> cyan; HoloState.ERROR -> amber
    }

    /** Motion budget (§02): transitions 180–320 ms, card opening 250–450 ms. Targets to test, not measured results. */
    object Motion { const val FAST = 180; const val NORMAL = 260; const val CARD = 380; const val MENU = 320 }

    /** Typography: condensed technical titles, tabular figures for measures, readable sans-serif body. */
    val title = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, letterSpacing = 0.6.sp, color = text)
    val body = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp, lineHeight = 21.sp, color = text)
    val small = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 16.sp, color = muted)
    /** Uppercase is reserved for small labels. */
    val label = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.4.sp, color = cyan)
    val tabular = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontFeatureSettings = "tnum", color = ice)
}

@Composable
fun HoloTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = darkColorScheme(primary = Holo.cyan, onPrimary = Holo.background, secondary = Holo.blue, background = Holo.background,
        surface = Holo.surface, onSurface = Holo.text, onBackground = Holo.text, error = Holo.amber),
    typography = Typography(bodyLarge = Holo.body, bodyMedium = Holo.body, labelSmall = Holo.label),
    content = content,
)

/** Smoked dark glass with a 1 dp light line; full frames are reserved to active panels (§02 Matériaux). */
fun Modifier.glass(active: Boolean = true, corner: Dp = 18.dp, opaque: Boolean = false): Modifier {
    val shape = RoundedCornerShape(corner)
    val a = if (opaque) 1f else 0.88f; val b = if (opaque) 1f else 0.94f
    return this.clip(shape)
        .background(Brush.verticalGradient(listOf(Holo.surface.copy(alpha = a), Holo.background.copy(alpha = b))))
        .border(1.dp, Brush.linearGradient(listOf(Holo.cyan.copy(alpha = if (active) 0.60f else 0.22f), Holo.blue.copy(alpha = 0.10f),
            Holo.cyan.copy(alpha = if (active) 0.34f else 0.12f))), shape)
}

/** Line icons drawn in the holographic style (no icon font dependency). */
object HoloIcons {
    private fun DrawScope.s(w: Float = 1.8f) = Stroke(width = w * density, cap = StrokeCap.Round)
    fun mic(d: DrawScope, c: Color) = with(d) {
        val w = size.width; val h = size.height
        drawRoundRect(c, topLeft = Offset(w * 0.38f, h * 0.18f), size = androidx.compose.ui.geometry.Size(w * 0.24f, h * 0.42f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.12f), style = s())
        drawArc(c, 0f, 180f, false, topLeft = Offset(w * 0.28f, h * 0.30f), size = androidx.compose.ui.geometry.Size(w * 0.44f, h * 0.40f), style = s())
        drawLine(c, Offset(w / 2, h * 0.70f), Offset(w / 2, h * 0.82f), strokeWidth = 1.8f * density)
    }
    fun send(d: DrawScope, c: Color) = with(d) {
        val w = size.width; val h = size.height
        val p = androidx.compose.ui.graphics.Path().apply { moveTo(w * 0.22f, h * 0.24f); lineTo(w * 0.80f, h * 0.5f); lineTo(w * 0.22f, h * 0.76f); lineTo(w * 0.34f, h * 0.5f); close() }
        drawPath(p, c, style = s())
    }
    fun close(d: DrawScope, c: Color) = with(d) {
        val w = size.width; val h = size.height
        drawLine(c, Offset(w * 0.3f, h * 0.3f), Offset(w * 0.7f, h * 0.7f), strokeWidth = 1.8f * density, cap = StrokeCap.Round)
        drawLine(c, Offset(w * 0.7f, h * 0.3f), Offset(w * 0.3f, h * 0.7f), strokeWidth = 1.8f * density, cap = StrokeCap.Round)
    }
    fun pin(d: DrawScope, c: Color, filled: Boolean) = with(d) {
        val w = size.width; val h = size.height
        drawCircle(c, radius = w * 0.14f, center = Offset(w / 2, h * 0.36f), style = if (filled) androidx.compose.ui.graphics.drawscope.Fill else s())
        drawLine(c, Offset(w / 2, h * 0.50f), Offset(w / 2, h * 0.78f), strokeWidth = 1.8f * density, cap = StrokeCap.Round)
    }
    fun expand(d: DrawScope, c: Color) = with(d) {
        val w = size.width; val h = size.height
        drawLine(c, Offset(w * 0.3f, h * 0.7f), Offset(w * 0.7f, h * 0.3f), strokeWidth = 1.8f * density, cap = StrokeCap.Round)
        drawLine(c, Offset(w * 0.5f, h * 0.3f), Offset(w * 0.7f, h * 0.3f), strokeWidth = 1.8f * density, cap = StrokeCap.Round)
        drawLine(c, Offset(w * 0.7f, h * 0.3f), Offset(w * 0.7f, h * 0.5f), strokeWidth = 1.8f * density, cap = StrokeCap.Round)
    }
    /** Destination glyphs of the radial menu. */
    fun destination(d: DrawScope, c: Color, index: Int) = with(d) {
        val w = size.width; val h = size.height; val m = Offset(w / 2, h / 2)
        when (index) {
            0 -> { drawCircle(c, w * 0.30f, m, style = s()); drawCircle(c, w * 0.08f, m) }                                  // cockpit
            1 -> { drawRect(c, Offset(w * 0.22f, h * 0.26f), androidx.compose.ui.geometry.Size(w * 0.26f, h * 0.48f), style = s())   // knowledge (open book)
                   drawRect(c, Offset(w * 0.52f, h * 0.26f), androidx.compose.ui.geometry.Size(w * 0.26f, h * 0.48f), style = s()) }
            2 -> { val p = androidx.compose.ui.graphics.Path().apply { moveTo(w * 0.2f, h * 0.3f); lineTo(w * 0.42f, h * 0.3f); lineTo(w * 0.48f, h * 0.38f)
                       lineTo(w * 0.8f, h * 0.38f); lineTo(w * 0.8f, h * 0.72f); lineTo(w * 0.2f, h * 0.72f); close() }; drawPath(p, c, style = s()) } // documents
            3 -> { drawCircle(c, w * 0.18f, m, style = s()); for (k in 0 until 8) { val a = k * Math.PI / 4
                       drawLine(c, Offset(m.x + (w * 0.24f * kotlin.math.cos(a)).toFloat(), m.y + (h * 0.24f * kotlin.math.sin(a)).toFloat()),
                           Offset(m.x + (w * 0.32f * kotlin.math.cos(a)).toFloat(), m.y + (h * 0.32f * kotlin.math.sin(a)).toFloat()), strokeWidth = 1.8f * density) } } // services
            else -> { for (k in 0 until 3) { val y = h * (0.32f + 0.18f * k); drawLine(c, Offset(w * 0.22f, y), Offset(w * 0.78f, y), strokeWidth = 1.6f * density)
                       drawCircle(c, w * 0.06f, Offset(w * (0.36f + 0.14f * k), y)) } }                                    // settings
        }
    }
}

/** Static HUD vocabulary of reference 1 (precision ticks and frame corners), drawn once: no fake gauges. */
fun DrawScope.hudBackdrop() {
    drawRect(Brush.radialGradient(listOf(Color(0xFF0B2240), Holo.background), center = Offset(size.width / 2, size.height * 0.30f), radius = size.maxDimension * 0.75f))
    val line = Holo.cyan.copy(alpha = 0.05f); val step = 32.dp.toPx()
    var x = 0f; while (x < size.width) { drawLine(line, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f); x += step }
    var y = 0f; while (y < size.height) { drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f); y += step }
    val c = Holo.cyan.copy(alpha = 0.30f); val l = 18.dp.toPx(); val m = 8.dp.toPx(); val sw = 1.2f * density
    for ((cx, cy, sx, sy) in listOf(Quad(m, m, 1f, 1f), Quad(size.width - m, m, -1f, 1f), Quad(m, size.height - m, 1f, -1f), Quad(size.width - m, size.height - m, -1f, -1f))) {
        drawLine(c, Offset(cx, cy), Offset(cx + l * sx, cy), strokeWidth = sw); drawLine(c, Offset(cx, cy), Offset(cx, cy + l * sy), strokeWidth = sw)
    }
    val t = Holo.cyan.copy(alpha = 0.16f); var k = 0
    while (k * 12.dp.toPx() < size.width * 0.5f) { val px = size.width * 0.25f + k * 12.dp.toPx(); drawLine(t, Offset(px, m), Offset(px, m + (if (k % 5 == 0) 7 else 4).dp.toPx()), strokeWidth = 1f); k++ }
}
private data class Quad(val a: Float, val b: Float, val c: Float, val d: Float)

/**
 * Test-observable record of UI texts that do not fit (V3-FIX-01: no truncated label or button). Interface texts
 * report here through onTextLayout; tests assert the set stays empty.
 */
object TextFit {
    val overflowing: MutableSet<String> = java.util.Collections.synchronizedSet(LinkedHashSet())
    fun report(tag: String, overflow: Boolean) { if (overflow) overflowing += tag else overflowing -= tag }
}

@Composable
fun HoloBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) = Box(modifier) { content() }
