package fr.jarvis.v3

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.jarvis.v3.model.CardKind
import fr.jarvis.v3.model.ContextCard
import kotlin.math.cos
import kotlin.math.sin

/** Small square icon button with a 48 dp touch target and an accessible label. */
@Composable
fun GlyphButton(label: String, tag: String, modifier: Modifier = Modifier, enabled: Boolean = true, size: Int = 48,
                tint: Color = Holo.ice, onClick: () -> Unit, glyph: DrawScope.(Color) -> Unit) {
    Box(modifier.size(size.dp).clip(CircleShape).clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
        .semantics { contentDescription = label }.testTag(tag), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(22.dp).alpha(if (enabled) 1f else 0.4f)) { glyph(tint) }
    }
}

@Composable
fun Pill(text: String, tag: String, enabled: Boolean = true, active: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(20.dp))
        .background(if (active) Holo.cyan.copy(alpha = 0.18f) else Holo.surface.copy(alpha = 0.6f))
        .border(1.dp, Holo.cyan.copy(alpha = if (!enabled) 0.15f else if (active) 0.8f else 0.4f), RoundedCornerShape(20.dp))
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).testTag(tag).padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center) {
        Text(text, style = Holo.small.copy(color = if (enabled) Holo.text else Holo.muted.copy(alpha = 0.6f)))
    }
}

/** Substitute illustration, clearly identified as such (no local image is authorised in V3). */
@Composable
private fun SubstituteIllustration(kind: CardKind, modifier: Modifier) {
    Canvas(modifier) {
        val c = Offset(size.width / 2, size.height / 2); val r = size.minDimension * 0.34f
        drawCircle(Brush.radialGradient(listOf(Holo.cyan.copy(alpha = 0.35f), Color.Transparent), c, r * 1.6f), r * 1.6f, c)
        when (kind) {
            CardKind.SYSTEM -> { drawArc(Holo.ice, 150f, 240f, false, Offset(c.x - r, c.y - r), androidx.compose.ui.geometry.Size(2 * r, 2 * r), style = Stroke(2f * density))
                drawLine(Holo.cyan, c, Offset(c.x + r * 0.7f * cos(-0.6f), c.y + r * 0.7f * sin(-0.6f)), strokeWidth = 2f * density) }
            CardKind.UNAVAILABLE -> { drawCircle(Holo.muted, r, c, style = Stroke(1.5f * density)); drawLine(Holo.muted, Offset(c.x - r * 0.7f, c.y + r * 0.7f), Offset(c.x + r * 0.7f, c.y - r * 0.7f), strokeWidth = 1.5f * density) }
            else -> { drawCircle(Holo.cyan.copy(alpha = 0.8f), r, c, style = Stroke(1.2f * density))
                for (k in 0 until 9) { val a = k * 0.7f; drawCircle(Holo.ice, 1.8f * density, Offset(c.x + r * 0.8f * cos(a) * cos(a * 1.3f), c.y + r * 0.8f * sin(a))) }
                drawOval(Holo.cyan.copy(alpha = 0.6f), Offset(c.x - r * 1.25f, c.y - r * 0.3f), androidx.compose.ui.geometry.Size(r * 2.5f, r * 0.6f), style = Stroke(1f * density)) }
        }
    }
}

/**
 * Card anatomy (§05): header (category, local provenance, pin, expand, close), visual, 2–4 line summary,
 * structured details, actions. Disabled actions say why. Empty states never invent content.
 */
@Composable
fun ContextCardView(card: ContextCard, expanded: Boolean, pinned: Boolean, onAction: (String) -> Unit, onPin: () -> Unit,
                    onExpand: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    var cite by remember(card.id) { mutableStateOf(false) }
    Row(modifier.testTag("card")) {
        // Visual link to the sphere core: a thin luminous connector on the inner edge.
        Box(Modifier.width(3.dp).fillMaxHeight().padding(vertical = 18.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Holo.cyan, Color.Transparent))))
        Column(Modifier.weight(1f).glass(active = true, opaque = true).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("FICHE · ${card.kind.category.uppercase()}", style = Holo.label, modifier = Modifier.weight(1f))
                GlyphButton(if (pinned) "Désépingler la fiche" else "Épingler la fiche", "card-pin", onClick = onPin) { HoloIcons.pin(this, it, pinned) }
                GlyphButton(if (expanded) "Réduire la fiche" else "Agrandir la fiche", "card-expand", onClick = onExpand) { HoloIcons.expand(this, it) }
                GlyphButton("Fermer la fiche", "card-close", onClick = onClose) { HoloIcons.close(this, it) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(78.dp)) {
                    SubstituteIllustration(card.kind, Modifier.size(64.dp))
                    Text(card.illustration, style = Holo.small.copy(fontSize = 9.sp), maxLines = 2)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(card.title, style = Holo.title, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("card-title"))
                    Text(card.provenance, style = Holo.small.copy(color = if (card.kind == CardKind.UNAVAILABLE) Holo.amber else Holo.ice), modifier = Modifier.testTag("card-provenance"))
                }
            }
            Spacer(Modifier.height(8.dp))
            val scroll = rememberScrollState()
            Column(Modifier.weight(1f, fill = false).verticalScroll(scroll)) {
                Text(card.summary, style = Holo.body, maxLines = if (expanded) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("card-summary"))
                if ((expanded || card.kind == CardKind.SYSTEM) && card.details.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    for ((k, v) in card.details) Row(Modifier.padding(vertical = 3.dp)) {
                        Text(k.uppercase(), style = Holo.label.copy(color = Holo.muted), modifier = Modifier.width(120.dp))
                        Text(v, style = if (card.kind == CardKind.SYSTEM) Holo.tabular else Holo.small.copy(color = Holo.text))
                    }
                }
                if (cite) Text("Source citée : ${card.provenance}. « ${CardText.quote(card)} »", style = Holo.small.copy(color = Holo.ice),
                    modifier = Modifier.padding(top = 8.dp).testTag("card-citation"))
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (a in card.actions) {
                    Pill(if (a.enabled) a.label else "${a.label} — ${a.reason ?: "indisponible"}", "card-action-${a.id}", enabled = a.enabled,
                        active = a.id == "cite" && cite) { if (a.id == "cite") cite = !cite else onAction(a.id) }
                }
            }
        }
    }
}

object CardText { fun quote(card: ContextCard) = fr.jarvis.v3.model.CardFactory.compact(card.summary, 160) }

/** Sliding host: the card surges from the edge (250–450 ms), never open by default. */
@Composable
fun ContextCardHost(card: ContextCard?, expanded: Boolean, pinned: Boolean, onAction: (String) -> Unit, onPin: () -> Unit,
                    onExpand: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf(card) }
    if (card != null) shown = card
    AnimatedVisibility(visible = card != null, modifier = modifier,
        enter = slideInHorizontally(tween(Holo.Motion.CARD)) { it / 2 } + fadeIn(tween(Holo.Motion.CARD)),
        exit = slideOutHorizontally(tween(Holo.Motion.NORMAL)) { it / 2 } + fadeOut(tween(Holo.Motion.NORMAL))) {
        shown?.let { ContextCardView(it, expanded, pinned, onAction, onPin, onExpand, onClose) }
    }
}
