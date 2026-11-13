package fr.jarvis.v3

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import fr.jarvis.v3.model.Destination
import fr.jarvis.v3.model.RadialGeometry
import kotlin.math.roundToInt

/** Anchor of the radial menu, placed in the conversation dock; reports its centre (root coordinates). */
@Composable
fun RadialAnchor(open: Boolean, onToggle: () -> Unit, onCenter: (Offset) -> Unit) {
    val glow by animateFloatAsState(if (open) 1f else 0f, tween(Holo.Motion.MENU), label = "anchorGlow")
    Box(Modifier.size(52.dp).onGloballyPositioned { c -> val p = c.positionInRoot(); onCenter(Offset(p.x + c.size.width / 2f, p.y + c.size.height / 2f)) }
        .clip(CircleShape).background(Brush.radialGradient(listOf(Holo.cyan.copy(alpha = 0.18f + 0.25f * glow), Holo.surface)))
        .border(1.dp, Holo.cyan.copy(alpha = 0.5f + 0.4f * glow), CircleShape)
        .clickable(role = Role.Button, onClickLabel = if (open) "Fermer le menu" else "Ouvrir le menu", onClick = onToggle)
        .semantics { contentDescription = "Menu de navigation"; stateDescription = if (open) "ouvert" else "fermé" }.testTag("radial"),
        contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(26.dp)) {
            val c = Offset(size.width / 2, size.height / 2); val r = size.minDimension * 0.45f
            val p = Path(); for (k in 0..6) { val a = (Math.PI / 3 * k + Math.PI / 6 + glow * Math.PI / 6).toFloat()
                val x = c.x + r * kotlin.math.cos(a); val y = c.y + r * kotlin.math.sin(a); if (k == 0) p.moveTo(x, y) else p.lineTo(x, y) }
            drawPath(p, Holo.cyan, style = Stroke(1.6f * density)); drawCircle(Holo.ice, r * 0.25f, c)
        }
    }
}

/**
 * Menu D (§06): ≤ 5 first-level actions deployed on an arc with slight inertia, dimmed background, halo on
 * the pressed choice, closes after navigation, Android back supported. Unavailable destinations stay
 * identified as such. With TalkBack (touch exploration) a plain vertical list replaces the arc.
 * The menu only PUBLISHES a navigation intent.
 */
@Composable
fun RadialMenuOverlay(open: Boolean, anchor: Offset, accessibleList: Boolean, onDismiss: () -> Unit, onSelect: (Destination) -> Unit) {
    val progress by animateFloatAsState(if (open) 1f else 0f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow), label = "radial")
    BackHandler(enabled = open, onBack = onDismiss)
    if (progress <= 0.001f && !open) return
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().alpha(progress).background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = "Fermer le menu", onClick = onDismiss)
            .testTag("radial-scrim"))
        val positions = remember { RadialGeometry.positions(Destination.entries.size, 172f) }
        Destination.entries.forEachIndexed { i, d ->
            val p = ((progress * 1.25f) - i * 0.06f).coerceIn(0f, 1.2f)
            val (dx, dy) = if (accessibleList) Pair(-8f, -(70f + 64f * (Destination.entries.size - 1 - i))) else positions[i]
            val item = 96.dp
            val ox = with(density) { anchor.x + (dx.dp.toPx() * p) - item.toPx() / 2 }
            val oy = with(density) { anchor.y + (dy.dp.toPx() * p) - item.toPx() / 2 }
            RadialItem(d, i, Modifier.offset { IntOffset(ox.roundToInt(), oy.roundToInt()) }.size(item).alpha(p.coerceIn(0f, 1f)).scale(0.6f + 0.4f * p.coerceIn(0f, 1f))) {
                onSelect(d)
            }
        }
    }
}

@Composable
private fun RadialItem(d: Destination, index: Int, modifier: Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val halo by animateFloatAsState(if (pressed) 1f else 0f, tween(Holo.Motion.FAST), label = "halo")
    val description = d.label + (if (d.available) "" else " — indisponible : ${d.unavailableReason}")
    Column(modifier.clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
        .semantics(mergeDescendants = true) { contentDescription = description }.testTag("dest-${d.name.lowercase()}"),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(48.dp).clip(CircleShape)
            .background(Brush.radialGradient(listOf(Holo.cyan.copy(alpha = 0.10f + 0.45f * halo), Holo.surface.copy(alpha = 0.95f))))
            .border(1.dp, (if (d.available) Holo.cyan else Holo.muted).copy(alpha = 0.55f + 0.45f * halo), CircleShape),
            contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(24.dp).alpha(if (d.available) 1f else 0.45f)) {
                HoloIcons.destination(this, if (d.available) Holo.ice else Holo.muted, index)
                // Unavailable destination: small amber badge (the card opened on tap explains why).
                if (!d.available) drawCircle(Holo.amber, 3.5f * density, androidx.compose.ui.geometry.Offset(size.width * 0.92f, size.height * 0.08f))
            }
        }
        Text(d.label, style = Holo.small.copy(color = if (d.available) Holo.text else Holo.muted), maxLines = 1,
            softWrap = false)
    }
}
