package com.jarvis.interfaceapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.jarvis.interfaceapp.domain.*
import com.jarvis.interfaceapp.avatar.AvatarDemo
import kotlin.math.roundToInt

private data class Preview(val original: DashboardPanel, val resize: Boolean, val dx: Int, val dy: Int) {
    fun candidate() = if (resize) original.copy(width = original.width + dx, height = original.height + dy)
        else original.copy(x = original.x + dx, y = original.y + dy)
}

@Composable
fun DashboardGrid(state: HomeState, dispatch: (HomeAction) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.testTag("dashboard")) {
        val profile = if (maxWidth >= 700.dp) DashboardProfile.WIDE else DashboardProfile.COMPACT
        val grid = profile.grid
        val panels = state.layouts.getValue(profile)
        val cellWidth = maxWidth / grid.columns
        // A bounded scrollable canvas preserves usable targets in short windows / large text.
        val cellHeight = maxOf(56.dp, maxHeight / grid.rows)
        val density = LocalDensity.current
        val cellWidthPx = with(density) { cellWidth.toPx() }
        val cellHeightPx = with(density) { cellHeight.toPx() }
        var preview by remember(profile, state.editing) { mutableStateOf<Preview?>(null) }
        val scroll = rememberScrollState()
        Box(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Box(Modifier.fillMaxWidth().height(cellHeight * grid.rows).clipToBounds()
                .background(MaterialTheme.colorScheme.background)) {
                if (state.editing) {
                    val lineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                    Canvas(Modifier.matchParentSize()) {
                        for (column in 0..grid.columns) drawLine(lineColor,
                            Offset(column * cellWidthPx, 0f), Offset(column * cellWidthPx, size.height))
                        for (row in 0..grid.rows) drawLine(lineColor,
                            Offset(0f, row * cellHeightPx), Offset(size.width, row * cellHeightPx))
                    }
                }
                for (panel in panels) {
                    key(panel.id) {
                        val position = "${panel.x},${panel.y} ${panel.width}x${panel.height}"
                        PanelCard(panel, state.editing,
                            Modifier.offset {
                                IntOffset((panel.x * cellWidthPx).roundToInt(), (panel.y * cellHeightPx).roundToInt())
                            }.width(cellWidth * panel.width).height(cellHeight * panel.height)
                                .padding(4.dp).testTag("panel-${panel.id}")
                                .semantics { stateDescription = position },
                            onLock = { dispatch(HomeAction.ToggleLock(profile, panel.id)) },
                            handle = { resize ->
                                fun commit(candidate: DashboardPanel) {
                                    dispatch(HomeAction.Place(profile, panel.id, candidate.x, candidate.y,
                                        candidate.width, candidate.height))
                                }
                                val actions = if (resize) listOf(
                                    CustomAccessibilityAction("Élargir") { commit(panel.copy(width = panel.width + 1)); true },
                                    CustomAccessibilityAction("Réduire la largeur") { commit(panel.copy(width = panel.width - 1)); true },
                                    CustomAccessibilityAction("Agrandir en hauteur") { commit(panel.copy(height = panel.height + 1)); true },
                                    CustomAccessibilityAction("Réduire la hauteur") { commit(panel.copy(height = panel.height - 1)); true },
                                ) else listOf(
                                    CustomAccessibilityAction("Déplacer à gauche") { commit(panel.copy(x = panel.x - 1)); true },
                                    CustomAccessibilityAction("Déplacer à droite") { commit(panel.copy(x = panel.x + 1)); true },
                                    CustomAccessibilityAction("Déplacer en haut") { commit(panel.copy(y = panel.y - 1)); true },
                                    CustomAccessibilityAction("Déplacer en bas") { commit(panel.copy(y = panel.y + 1)); true },
                                )
                                EditHandle(
                                    label = if (resize) "Taille" else "Déplacer",
                                    tag = "${if (resize) "resize" else "move"}-${panel.id}",
                                    enabled = !panel.locked,
                                    actions = actions,
                                    gestureKey = profile to (cellWidthPx to cellHeightPx),
                                    onPreview = { delta ->
                                        preview = Preview(panel, resize, (delta.x / cellWidthPx).roundToInt(),
                                            (delta.y / cellHeightPx).roundToInt())
                                    },
                                    onCommit = { preview?.let { commit(it.candidate()) }; preview = null },
                                    onCancel = { preview = null },
                                )
                            })
                    }
                }
                preview?.let { proposal ->
                    val candidate = proposal.candidate()
                    val valid = placementError(grid, panels, candidate) == null
                    // Preview only; actual panels always remain in their last valid position.
                    Surface(Modifier.offset {
                        IntOffset((candidate.x * cellWidthPx).roundToInt(), (candidate.y * cellHeightPx).roundToInt())
                    }.width(cellWidth * candidate.width.coerceAtLeast(1))
                        .height(cellHeight * candidate.height.coerceAtLeast(1)).padding(4.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        border = BorderStroke(3.dp, if (valid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)) {}
                }
            }
        }
    }
}

@Composable
private fun PanelCard(
    panel: DashboardPanel,
    editing: Boolean,
    modifier: Modifier,
    onLock: () -> Unit,
    handle: @Composable RowScope.(Boolean) -> Unit,
) {
    val title = when (panel.id) {
        PanelId.AVATAR -> "Avatar"
        PanelId.SYSTEM -> "État système"
        PanelId.APPLICATIONS -> "Applications JARVIS"
        PanelId.COMMANDS -> "Commandes"
    }
    val body = when (panel.id) {
        PanelId.AVATAR -> "Emplacement réservé.\nMP4 et/ou animation 2D à venir."
        PanelId.SYSTEM -> "État local : opérationnel\nInterface autonome • hors ligne\nAucun service JARVIS requis."
        PanelId.APPLICATIONS -> "Accès aux applications à venir.\nAucune application externe requise ni démarrée."
        PanelId.COMMANDS -> "Commandes à venir.\nAucune action externe disponible."
    }
    val contentTag = when (panel.id) {
        PanelId.AVATAR -> "avatar-placeholder"
        PanelId.SYSTEM -> "local-status"
        else -> "content-${panel.id}"
    }
    Surface(modifier, shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Column(Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                if (editing) IconButton(onClick = onLock, modifier = Modifier.testTag("lock-${panel.id}")
                    .semantics { contentDescription = "${if (panel.locked) "Déverrouiller" else "Verrouiller"} $title" }) {
                    Text(if (panel.locked) "●" else "○")
                }
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).testTag(contentTag)) {
                if (panel.id == PanelId.AVATAR) AvatarDemo(Modifier.fillMaxWidth())
                else Text(body, style = MaterialTheme.typography.bodySmall)
                if (editing) Text("${panel.x},${panel.y} • ${panel.width}×${panel.height}\nMinimum ${panel.minWidth}×${panel.minHeight}",
                    style = MaterialTheme.typography.labelSmall)
            }
            if (editing) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                handle(false)
                handle(true)
            }
        }
    }
}

@Composable
private fun RowScope.EditHandle(
    label: String,
    tag: String,
    enabled: Boolean,
    actions: List<CustomAccessibilityAction>,
    gestureKey: Any,
    onPreview: (Offset) -> Unit,
    onCommit: () -> Unit,
    onCancel: () -> Unit,
) {
    val currentPreview by rememberUpdatedState(onPreview)
    val currentCommit by rememberUpdatedState(onCommit)
    val currentCancel by rememberUpdatedState(onCancel)
    val gestures = if (enabled) Modifier.pointerInput(gestureKey) {
        var delta = Offset.Zero
        detectDragGestures(
            onDragStart = { delta = Offset.Zero; currentPreview(delta) },
            onDragEnd = { currentCommit() }, onDragCancel = { currentCancel() },
            onDrag = { change, amount -> change.consume(); delta += amount; currentPreview(delta) },
        )
    } else Modifier
    Box(Modifier.weight(1f).heightIn(min = 48.dp).then(gestures).testTag(tag)
        .semantics {
            contentDescription = label
            if (enabled) customActions = actions else disabled()
        }, contentAlignment = Alignment.Center) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
    }
}
