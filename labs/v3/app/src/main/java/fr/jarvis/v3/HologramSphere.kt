package fr.jarvis.v3

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import fr.jarvis.m55d.SphereModel
import fr.jarvis.v3.model.HoloDetail
import fr.jarvis.v3.model.HoloGeometry
import fr.jarvis.v3.model.HoloState
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Process-scoped rotation and evidence counters (survive rotation of the device). */
class SphereMotion {
    var yaw = 0.5f; var pitch = -0.22f; var vYaw = 0f; var vPitch = 0f
    var recentering = false
    /** Smoothed, gated audio level actually used for motion. */
    var level = 0f
    var sim = 0f
    var pulseAt = -10f
    /** Test evidence: frames actually drawn and drag events actually received. */
    @Volatile var drawnFrames = 0L
    @Volatile var dragEvents = 0
    internal val labelRects = ArrayList<Pair<String, Rect>>()
    internal var core = Offset.Zero
    internal var coreRadius = 0f
}

/**
 * Sphere A (§04): independent layers — back halo, projection pedestal, geometric shell, node network,
 * links, orbital rings, core and event accents. It only CONSUMES an observable state; it never commands
 * the engine. Frame rate is capped by [detail].fps; with [animate] false (background, reduced motion or
 * static mode) nothing is scheduled and a single still frame is drawn.
 */
@Composable
fun HologramSphere(
    state: HoloState, level: Float, pulseKey: Int, keywords: List<String>, detail: HoloDetail, animate: Boolean,
    motion: SphereMotion, onVsync: (Float) -> Unit, fpsCap: Int = detail.fps, modifier: Modifier = Modifier,
) {
    val geo = remember(detail) { HoloGeometry(detail) }
    var time by remember { mutableFloatStateOf(motion.sim) }
    val target by rememberUpdatedState(level)
    val color by animateColorAsState(Holo.stateColor(state), tween(Holo.Motion.MENU), label = "stateColor")
    val thinking by animateFloatAsState(if (state == HoloState.THINKING || state == HoloState.STREAMING) 1f else 0f, tween(Holo.Motion.MENU), label = "thinking")
    val expansion = 1f   // V3-FIX-01: no size change while listening (the sphere stays still)
    val transcribing by animateFloatAsState(if (state == HoloState.TRANSCRIBING) 1f else 0f, tween(Holo.Motion.NORMAL), label = "transcribing")

    LaunchedEffect(pulseKey) { if (pulseKey > 0) motion.pulseAt = motion.sim }
    LaunchedEffect(animate, fpsCap) {
        if (!animate) { time = motion.sim + 1e-4f; return@LaunchedEffect }
        var last = 0L; var acc = 0f
        while (isActive) withFrameNanos { now ->
            if (last != 0L) {
                val dtMs = (now - last) / 1e6f; val dt = min(dtMs / 1000f, 0.1f)
                onVsync(dtMs)
                motion.sim += dt; acc += dt
                motion.yaw += 0.18f * dt   // automatic, slow; only runs while JARVIS answers (V3-FIX-01)
                motion.level = HoloGeometry.smoothLevel(motion.level, target, dt)
                if (acc >= 1f / fpsCap - 0.002f) { time = motion.sim; acc = 0f }
            }
            last = now
        }
    }

    val measurer = rememberTextMeasurer()
    val labelStyle = remember { TextStyle(color = Holo.text, fontSize = 12.sp, letterSpacing = 0.3.sp) }
    val labels = remember(keywords) { keywords.map { measurer.measure(it, labelStyle) } }

    Canvas(
        modifier
            .testTag("sphere")
            .semantics { contentDescription = "Sphère holographique de JARVIS (affichage, non interactive)"; stateDescription = state.label }
    ) {
        val t = time
        motion.drawnFrames++
        val lvl = motion.level
        val breath = HoloGeometry.breath(t)
        val cx = size.width / 2f; val cy = size.height * 0.46f
        val r = min(size.width, size.height) * 0.33f * expansion * (1f + 0.035f * lvl)
        val center = Offset(cx, cy)

        // 1. Back halo: contained, intensity follows breath, level and thinking.
        val haloAlpha = (0.16f + 0.08f * breath + 0.22f * lvl + 0.10f * thinking).coerceAtMost(0.55f)
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = haloAlpha), color.copy(alpha = haloAlpha * 0.25f), Color.Transparent), center, r * 1.85f), r * 1.85f, center)

        // 2. Projection pedestal (reference 2): flat rings below and a soft beam.
        val py = cy + r * 1.22f
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, color.copy(alpha = 0.07f)), startY = cy, endY = py), Offset(cx - r * 0.55f, cy), Size(r * 1.1f, py - cy))
        for ((k, wf) in listOf(1.55f, 1.05f, 0.55f).withIndex()) {
            val w = r * wf; val h = w * 0.17f
            drawOval(color.copy(alpha = 0.32f - 0.08f * k), Offset(cx - w, py - h), Size(2 * w, 2 * h), style = Stroke(1.2f * density))
        }
        drawOval(Brush.radialGradient(listOf(color.copy(alpha = 0.25f), Color.Transparent), Offset(cx, py), r * 0.6f), Offset(cx - r * 0.6f, py - r * 0.1f), Size(r * 1.2f, r * 0.2f))

        val proj = Array(geo.nodes.size) { i -> SphereModel.project(SphereModel.rotate(geo.nodes[i], motion.yaw, motion.pitch), r, cx, cy) }

        // 3. Geometric shell (back faint, front clearer).
        val back = Path(); val front = Path()
        for (line in geo.shell) {
            var prev: SphereModel.P2? = null; var first: SphereModel.P2? = null
            for (p3 in line) {
                val p = SphereModel.project(SphereModel.rotate(p3, motion.yaw, motion.pitch), r * 1.02f, cx, cy)
                if (first == null) first = p
                prev?.let { a -> val path = if ((a.depth + p.depth) > 0f) front else back; path.moveTo(a.x, a.y); path.lineTo(p.x, p.y) }
                prev = p
            }
            val a = prev; val b = first
            if (a != null && b != null) { val path = if ((a.depth + b.depth) > 0f) front else back; path.moveTo(a.x, a.y); path.lineTo(b.x, b.y) }
        }
        drawPath(back, Holo.blue.copy(alpha = 0.08f), style = Stroke(1f * density))
        drawPath(front, color.copy(alpha = 0.20f), style = Stroke(1f * density))

        // 4. Links: stable network; while thinking, alternate links fade in and out (re-organisation, no fake text).
        val lb = Path(); val lf = Path()
        for ((a, b) in geo.links) { val p = proj[a]; val q = proj[b]; val path = if (p.depth + q.depth > 0f) lf else lb; path.moveTo(p.x, p.y); path.lineTo(q.x, q.y) }
        drawPath(lb, Holo.blue.copy(alpha = 0.10f), style = Stroke(0.8f * density))
        drawPath(lf, color.copy(alpha = 0.30f + 0.15f * thinking), style = Stroke(1f * density))
        if (thinking > 0.01f) {
            for ((j, pair) in geo.altLinks.withIndex()) {
                if (j >= 70) break
                val a = thinking * max(0f, sin(t * 1.7f + j * 0.9f))
                if (a < 0.08f) continue
                val p = proj[pair.first]; val q = proj[pair.second]
                drawLine(Holo.violet.copy(alpha = 0.55f * a * (0.5f + 0.25f * (p.depth + q.depth + 2f) / 2f)), Offset(p.x, p.y), Offset(q.x, q.y), strokeWidth = 1.1f * density)
            }
        }

        // 5. Nodes: back first; rare gold accents; rare twinkle; streaming pulse wave from the core.
        val pulseAge = t - motion.pulseAt
        val wave = if (pulseAge in 0f..0.9f) pulseAge / 0.9f * r * 1.25f else -1f
        for (pass in 0..1) for (i in proj.indices) {
            val p = proj[i]; if ((p.depth >= 0f) != (pass == 1)) continue
            val f = (p.depth + 1f) / 2f
            val twinkle = max(0f, sin(t * 0.9f + i * 2.3f) - 0.985f) * 60f
            val boost = if (wave > 0f) max(0f, 1f - abs(hypot(p.x - cx, p.y - cy) - wave) / (14f * density)) else 0f
            val gold = i in geo.accents
            val base = if (gold) Holo.gold else if (pass == 1) Holo.ice else Holo.blue
            val rad = (1.0f + 1.7f * f) * density * p.scale * (if (gold) 1.35f else 1f) * (1f + 0.6f * boost)
            val alpha = ((if (pass == 1) 0.55f + 0.45f * f else 0.18f + 0.25f * f) + twinkle + boost).coerceAtMost(1f)
            if (detail.glow && pass == 1) drawCircle(base.copy(alpha = 0.10f + 0.20f * boost), rad * 3.4f, Offset(p.x, p.y))
            drawCircle(base.copy(alpha = alpha), rad, Offset(p.x, p.y))
        }
        if (wave > 0f) drawCircle(color.copy(alpha = 0.35f * (1f - pulseAge / 0.9f)), wave, center, style = Stroke(1.2f * density))

        // 6. Orbital rings, tilted, with a moving light segment; listening widens them with the real audio level.
        val tilts = floatArrayOf(-16f, 26f, 64f)
        for (k in 0 until detail.orbitals) {
            val rr = r * (1.22f + 0.12f * k) * (1f + 0.10f * lvl * (if (state == HoloState.LISTENING) 1f else 0.3f))
            val tl = Offset(cx - rr, cy - rr * 0.30f); val sz = Size(2 * rr, 2 * rr * 0.30f)
            rotate(tilts[k], center) {
                drawOval(color.copy(alpha = 0.20f), tl, sz, style = Stroke(1f * density))
                val start = (t * (32f + 14f * k) * (if (k % 2 == 0) 1f else -1f)) % 360f
                drawArc(color.copy(alpha = 0.80f), start, 42f, false, tl, sz, style = Stroke(2f * density, cap = StrokeCap.Round))
            }
        }

        // 7. Core: luminous heart + slowly turning hexagon; voice level modulates it (real PCM levels only).
        val coreR = r * 0.19f * (1f + 0.28f * lvl + 0.05f * breath)
        motion.core = center; motion.coreRadius = coreR
        drawCircle(Brush.radialGradient(listOf(Holo.ice.copy(alpha = 0.95f), color.copy(alpha = 0.60f), Color.Transparent), center, coreR * 2.1f), coreR * 2.1f, center)
        hexagon(center, coreR * 1.45f, -t * 0.25f, color.copy(alpha = 0.65f), 1.4f * density)
        hexagon(center, coreR * 0.80f, t * 0.40f, Holo.ice.copy(alpha = 0.55f), 1.1f * density)
        if (state == HoloState.SPEAKING) for (k in 1..3) {
            val rr = coreR * (1.6f + 0.55f * k) * (1f + 0.5f * lvl)
            drawCircle(Holo.ice.copy(alpha = (0.30f - 0.08f * k) * (0.35f + lvl)), rr, center, style = Stroke(1.2f * density))
        }
        // Transcription: honest indeterminate ring (no percentage).
        if (transcribing > 0.01f) {
            val rr = coreR * 2.6f
            drawArc(Holo.cyan.copy(alpha = 0.85f * transcribing), (t * 220f) % 360f, 70f + 40f * sin(t * 2f), false,
                Offset(cx - rr, cy - rr), Size(2 * rr, 2 * rr), style = Stroke(2.4f * density, cap = StrokeCap.Round))
        }

        // 8. Conversation keywords anchored on nodes, front-facing only, linked to their node.
        motion.labelRects.clear()
        val labelsFit = r >= 70f * density
        for ((i, layout) in labels.withIndex()) {
            if (!labelsFit) break
            val p = proj[geo.anchors[i % geo.anchors.size]]
            if (p.depth < -0.15f) continue
            val a = ((p.depth + 0.15f) / 1.15f).coerceIn(0f, 1f)
            val dx = p.x - cx; val dy = p.y - cy; val d = max(1f, hypot(dx, dy))
            val ax = p.x + dx / d * 26f * density; val ay = p.y + dy / d * 26f * density
            val w = layout.size.width + 16f * density; val h = layout.size.height + 8f * density
            val left = (if (dx >= 0) ax else ax - w).coerceIn(2f, size.width - w - 2f); val top = (ay - h / 2).coerceIn(2f, size.height - h - 2f)
            val box = Rect(left - 4f * density, top - 4f * density, left + w + 4f * density, top + h + 4f * density)
            if (motion.labelRects.any { it.second.overlaps(box) } || hypot(left + w / 2 - cx, top + h / 2 - cy) < r * 0.55f) continue   // no overlap, never on the core
            drawLine(color.copy(alpha = 0.55f * a), Offset(p.x, p.y), Offset(ax, ay), strokeWidth = 1f * density)
            drawCircle(Holo.ice.copy(alpha = a), 2.4f * density, Offset(p.x, p.y))
            drawRoundRect(Holo.surface.copy(alpha = 0.88f * a), Offset(left, top), Size(w, h), CornerRadius(h / 2))
            drawRoundRect(color.copy(alpha = 0.70f * a), Offset(left, top), Size(w, h), CornerRadius(h / 2), style = Stroke(1f * density))
            drawText(layout, topLeft = Offset(left + 8f * density, top + 4f * density), alpha = a)
            motion.labelRects += keywords[i] to box
        }
    }
}

private fun DrawScope.hexagon(c: Offset, radius: Float, angle: Float, color: Color, width: Float) {
    val p = Path()
    for (k in 0..6) {
        val a = angle + k * (PI.toFloat() / 3f)
        val x = c.x + radius * kotlin.math.cos(a); val y = c.y + radius * kotlin.math.sin(a)
        if (k == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    drawPath(p, color, style = Stroke(width))
}
