package fr.jarvis.m55d

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min

/** Dark futuristic palette with readable contrast (text >= 4.5:1 on background). */
object Palette {
    val background = Color(0xFF05080F); val panel = Color(0xFF0E1522); val text = Color(0xFFE6EEF8)
    val muted = Color(0xFF9FB0C4); val accent = Color(0xFF3FD0FF)
    fun of(state: CockpitState) = when (state) {
        CockpitState.IDLE -> Color(0xFF3FD0FF); CockpitState.LISTENING -> Color(0xFF4CF0A0)
        CockpitState.THINKING -> Color(0xFFB48CFF); CockpitState.SPEAKING -> Color(0xFFFFC857)
        CockpitState.ERROR -> Color(0xFFFF5A6E)
    }
    fun label(state: CockpitState) = when (state) {
        CockpitState.IDLE -> "En veille"; CockpitState.LISTENING -> "Écoute"; CockpitState.THINKING -> "Réflexion"
        CockpitState.SPEAKING -> "Parole"; CockpitState.ERROR -> "Erreur"
    }
}

/** Shared, test-observable rotation state of the sphere (radians). */
class SphereRotation {
    var yaw by mutableFloatStateOf(0.4f); var pitch by mutableFloatStateOf(-0.25f); var vYaw = 0f; var vPitch = 0f
    /** Evidence for tests: drag events actually received, and the sphere bounds on screen (px). */
    @Volatile var dragEvents = 0
    @Volatile var screenBounds: androidx.compose.ui.geometry.Rect? = null
}

@Composable
fun KnowledgeSphere(state: CockpitState, level: Float, mode: RenderMode, rotation: SphereRotation, stats: FrameStats,
                    regulator: RenderRegulator, thermal: () -> Int, modifier: Modifier = Modifier) {
    val points = remember(mode) { SphereModel.fibonacci(mode.points) }
    var pulse by remember { mutableFloatStateOf(0f) }
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) withFrameNanos { now ->
            if (last != 0L) {
                val dtMs = (now - last) / 1e6f; val dt = dtMs / 1000f
                stats.add(dtMs); regulator.onFrame(dtMs, now / 1_000_000, thermal())
                time += dt
                rotation.yaw += rotation.vYaw * dt + (if (rotation.vYaw == 0f) 0.15f * dt else 0f)
                rotation.pitch = (rotation.pitch + rotation.vPitch * dt).coerceIn(-1.2f, 1.2f)
                rotation.vYaw = SphereModel.decay(rotation.vYaw, dt); rotation.vPitch = SphereModel.decay(rotation.vPitch, dt)
                pulse = (pulse - dt * 1.5f).coerceAtLeast(0f)
            }
            last = now
        }
    }
    val color = Palette.of(state)
    Canvas(modifier
        .testTag("sphere")
        .onGloballyPositioned { c ->
            val topLeft = c.localToScreen(Offset.Zero)
            rotation.screenBounds = androidx.compose.ui.geometry.Rect(topLeft, androidx.compose.ui.geometry.Size(c.size.width.toFloat(), c.size.height.toFloat()))
        }
        .semantics { contentDescription = "Sphère de connaissances 3D, faites glisser pour la tourner"; stateDescription = Palette.label(state) }
        .pointerInput(Unit) {
            detectDragGestures(onDrag = { change, drag ->
                change.consume(); rotation.dragEvents++
                rotation.yaw += drag.x * 0.008f; rotation.pitch = (rotation.pitch - drag.y * 0.008f).coerceIn(-1.2f, 1.2f)
                rotation.vYaw = drag.x * 0.5f; rotation.vPitch = -drag.y * 0.5f
            })
        }
        .pointerInput(Unit) { detectTapGestures { pulse = 1f } }
    ) {
        val r = min(size.width, size.height) * 0.38f
        val c = Offset(size.width / 2, size.height / 2)
        val breathe = when (state) {
            CockpitState.LISTENING, CockpitState.SPEAKING -> 1f + 0.06f * level
            CockpitState.THINKING -> 1f + 0.03f * kotlin.math.sin(time * 6f)
            else -> 1f + 0.015f * kotlin.math.sin(time * 1.5f)
        } + 0.08f * pulse
        if (mode.glow) drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.22f), Color.Transparent), c, r * 1.6f), r * 1.6f, c)
        val projected = points.map { SphereModel.project(SphereModel.rotate(it, rotation.yaw, rotation.pitch), r * breathe, c.x, c.y) }.sortedBy { it.depth }
        for (p in projected) {
            val a = ((p.depth + 1f) / 2f * 0.85f + 0.15f).coerceIn(0f, 1f)
            drawCircle(color.copy(alpha = a), radius = (1.2f + 1.8f * p.scale) * if (mode == RenderMode.ECONOMY) 1.3f else 1f, center = Offset(p.x, p.y))
        }
        drawCircle(color.copy(alpha = 0.35f), r * breathe, c, style = Stroke(1.5f))
        if (state == CockpitState.ERROR) drawCircle(Palette.of(CockpitState.ERROR), r * 1.08f, c, style = Stroke(4f))
        for (i in 0 until mode.particles) {
            val ang = time * 0.4f + i * 0.37f; val rr = r * (1.25f + 0.1f * kotlin.math.sin(time + i))
            drawCircle(color.copy(alpha = 0.35f), 1.6f, Offset(c.x + rr * kotlin.math.cos(ang), c.y + rr * kotlin.math.sin(ang) * 0.4f))
        }
    }
}

/**
 * 2.5D avatar: layered vector bust by default (no private image). [atlas] may supply owner-imported
 * PNG frames per state (loaded from app-private storage only, never bundled or published).
 */
@Composable
fun Avatar25D(state: CockpitState, level: Float, atlas: Map<CockpitState, ImageBitmap>? = null, modifier: Modifier = Modifier) {
    val color = Palette.of(state)
    val blink by rememberInfiniteTransition(label = "blink").animateFloat(1f, 0.1f,
        infiniteRepeatable(keyframes { durationMillis = 3600; 1f at 3300; 0.1f at 3400; 1f at 3500 }), label = "b")
    Canvas(modifier.testTag("avatar").semantics { contentDescription = "Avatar, expression : ${Palette.label(state)}" }) {
        val img = atlas?.get(state)
        if (img != null) { drawImage(img, dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt())); return@Canvas }
        val w = size.width; val h = size.height; val cx = w / 2
        drawOval(Palette.panel, Offset(cx - w * 0.30f, h * 0.62f), androidx.compose.ui.geometry.Size(w * 0.60f, h * 0.45f)) // shoulders (back layer)
        drawCircle(Color(0xFF1B2638), w * 0.22f, Offset(cx, h * 0.42f)) // head
        drawCircle(color.copy(alpha = 0.55f), w * 0.225f, Offset(cx, h * 0.42f), style = Stroke(2f))
        val eyeY = h * 0.40f; val eyeH = (h * 0.025f) * blink
        for (dx in listOf(-0.08f, 0.08f)) drawOval(color, Offset(cx + w * dx - w * 0.03f, eyeY - eyeH / 2), androidx.compose.ui.geometry.Size(w * 0.06f, eyeH.coerceAtLeast(1f)))
        val mouthOpen = when (state) { CockpitState.SPEAKING -> h * (0.01f + 0.04f * level); CockpitState.ERROR -> h * 0.005f; else -> h * 0.008f }
        drawOval(color.copy(alpha = 0.9f), Offset(cx - w * 0.06f, h * 0.50f), androidx.compose.ui.geometry.Size(w * 0.12f, mouthOpen))
        if (state == CockpitState.THINKING) for (i in 0..2) drawCircle(color, w * 0.012f, Offset(cx + w * (0.25f + i * 0.05f), h * (0.25f - i * 0.04f)))
    }
}

@Composable
fun CockpitScreen(vm: CockpitViewModel) {
    val state = vm.state; val mode = vm.regulator.mode
    Column(Modifier.fillMaxSize().background(Palette.background).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("JARVIS · Cockpit M55-D", color = Palette.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("HORS LIGNE", color = Palette.of(CockpitState.LISTENING), fontSize = 12.sp,
                modifier = Modifier.testTag("offline").semantics { contentDescription = "Fonctionnement hors ligne, aucune connexion réseau" })
        }
        Text("Source : ${vm.sourceLabel}", color = Palette.muted, fontSize = 12.sp, modifier = Modifier.testTag("source"))
        Row(Modifier.weight(1f).fillMaxWidth()) {
            KnowledgeSphere(state, vm.level, mode, vm.rotation, vm.stats, vm.regulator, vm.thermal, Modifier.weight(0.62f).fillMaxHeight())
            Avatar25D(state, vm.level, vm.atlas, Modifier.weight(0.38f).fillMaxHeight())
        }
        Text("État : ${Palette.label(state)}", color = Palette.text, modifier = Modifier.testTag("state"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = vm.autoMode, onClick = { vm.automatic() }, modifier = Modifier.testTag("mode-AUTO"), label = { Text("Auto") })
            RenderMode.entries.forEach { m ->
                FilterChip(selected = !vm.autoMode && m == mode, onClick = { vm.forceMode(m) }, modifier = Modifier.testTag("mode-${m.name}"),
                    label = { Text(when (m) { RenderMode.CINEMATIC -> "Cinématique"; RenderMode.BALANCED -> "Équilibré"; RenderMode.ECONOMY -> "Économie" }) })
            }
        }
        var text by remember { mutableStateOf("") }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.testTag("mic")) { Text("Micro (indisponible ici)") }
            OutlinedTextField(text, { text = it }, Modifier.weight(1f).testTag("input"), singleLine = true, label = { Text("Message") })
            Button(onClick = { vm.demoSubmit(); text = "" }, enabled = text.isNotBlank(), modifier = Modifier.testTag("send")) { Text("Envoyer") }
            Button(onClick = { vm.stop() }, modifier = Modifier.testTag("stop")) { Text("Arrêt") }
        }
    }
}
