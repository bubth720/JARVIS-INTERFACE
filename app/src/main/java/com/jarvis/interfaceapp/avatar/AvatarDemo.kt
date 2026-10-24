package com.jarvis.interfaceapp.avatar

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.min
import kotlinx.coroutines.launch

/** Exactly one frame loop per host, cancelled when clipped, paused, static or disposed. */
@Composable
fun AvatarDemo(
    modifier: Modifier = Modifier,
    controller: AvatarAnimationController = remember { AvatarAnimationController() },
    artwork: AvatarArtwork = remember { DemoAvatarArtwork() },
    showAssetButton: Boolean = true,
) {
    var v2Preview by remember { mutableStateOf(false) }
    val packRepository = remember { com.jarvis.interfaceapp.avatar.v2.AvatarPackRepository() }
    val loadedPacks = remember { mutableMapOf<String, com.jarvis.interfaceapp.avatar.v2.LoadedPremiumPack>() }
    var selectedArtwork by remember { mutableStateOf<AvatarArtwork?>(null) }
    val effectiveArtwork = selectedArtwork ?: artwork
    val frameArtwork = effectiveArtwork as? com.jarvis.interfaceapp.avatar.v2.FrameDrivenAvatarArtwork
    val noPendingFrames = remember { kotlinx.coroutines.flow.MutableStateFlow(false) }
    val artworkPending by (frameArtwork?.pendingFrames ?: noPendingFrames).collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var panelVisible by remember { mutableStateOf(false) }
    var previewVisible by remember { mutableStateOf(false) }
    var panelSize by remember { mutableStateOf(IntSize.Zero) }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    var preview by remember { mutableStateOf(false) }
    var assetsPreview by remember { mutableStateOf(false) }
    var failed by remember(effectiveArtwork) { mutableStateOf(false) }
    var lifecycleGeneration by remember { mutableIntStateOf(0) }
    val pose = remember(controller) { mutableStateOf(controller.pose) }
    val status by controller.status.collectAsStateWithLifecycle()
    DisposableEffect(lifecycle, controller) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            controller.setForeground(resumed)
            lifecycleGeneration++ // Restart frame origin even if pause/resume recompositions coalesce.
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); controller.setForeground(false) }
    }
    DisposableEffect(controller) { onDispose { controller.release() } }
    DisposableEffect(effectiveArtwork) { onDispose { effectiveArtwork.release() } }
    SideEffect {
        controller.setForeground(resumed)
        controller.setVisible(!assetsPreview && !v2Preview && !failed && if (preview) previewVisible else panelVisible)
        val size = if (preview) previewSize else panelSize
        controller.resize(size.width, size.height)
    }
    // Do not rely only on a lifecycle-aware Flow collector: it stops collecting
    // below STARTED, so local lifecycle/visibility gates must cancel the clock too.
    val viewport = if (preview) previewSize else panelSize
    val active = resumed && !assetsPreview && !v2Preview && !failed && (if (preview) previewVisible else panelVisible) &&
        viewport.width > 0 && viewport.height > 0 && (status.advancing || artworkPending)
    LaunchedEffect(controller, effectiveArtwork, active, lifecycleGeneration) {
        if (active) {
            var previous = 0L
            var pending = 0.0
            var elapsed = 0.0
            while (true) {
                // Infinite-animation policy lets legacy Compose tests idle; new motion tests
                // advance their clock explicitly. Production uses the display frame clock.
                withInfiniteAnimationFrameNanos { now ->
                    if (previous != 0L) {
                        val delta = ((now - previous) / 1_000_000.0).coerceIn(0.0, 100.0)
                        pending += delta; elapsed += delta
                    }
                    previous = now
                    if (pending >= 1000.0 / 30.0) {
                        controller.advanceBy(elapsed)
                        frameArtwork?.advanceBy(elapsed)
                        pose.value = controller.pose
                        pending %= (1000.0 / 30.0)
                        elapsed = 0.0
                    }
                }
            }
        }
    }
    fun command(command: AvatarCommand) {
        controller.execute(command)
        pose.value = controller.pose
    }
    val frozenPanel = remember(preview) { controller.pose }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AvatarSurface(pose = { if (preview) frozenPanel else pose.value }, artwork = effectiveArtwork,
            failed = failed, onFailure = { failed = true },
            modifier = Modifier.fillMaxWidth().height(112.dp).testTag("avatar-renderer")
                .semantics { stateDescription = if (status.advancing && !preview) "Animé" else "Suspendu" },
            onViewport = { visible, size -> panelVisible = visible; panelSize = size })
        OutlinedButton(onClick = { preview = true }, modifier = Modifier.fillMaxWidth().testTag("avatar-preview")) {
            Text("Démo 2.5D", style = MaterialTheme.typography.labelSmall)
        }
    }
    if (v2Preview) Dialog(onDismissRequest = { v2Preview = false }) {
        Surface(shape = MaterialTheme.shapes.large) {
            com.jarvis.interfaceapp.avatar.v2.AvatarV2Preview(packRepository, loadedPacks,
                onApply = { pack -> selectedArtwork = pack?.let { com.jarvis.interfaceapp.avatar.v2.PremiumAvatarArtwork(it) } },
                onClose = { v2Preview = false })
        }
    }
    if (assetsPreview) Dialog(onDismissRequest = { assetsPreview = false }) {
        Surface(shape = MaterialTheme.shapes.large) {
            com.jarvis.interfaceapp.avatar.assets.AvatarPreviewPanel { assetsPreview = false }
        }
    }
    val scope = rememberCoroutineScope()
    val previewScroll = rememberScrollState()
    if (preview) Dialog(onDismissRequest = { preview = false; previewVisible = false }) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().heightIn(max = 600.dp).padding(16.dp)
                .verticalScroll(previewScroll), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Avatar 2.5D · prototype", style = MaterialTheme.typography.titleMedium)
                Text("Personnage provisoire original. Buste, paupières et tête indépendants.",
                    style = MaterialTheme.typography.bodySmall)
                AvatarSurface(pose = { pose.value }, artwork = effectiveArtwork, failed = failed,
                    onFailure = { failed = true },
                    modifier = Modifier.fillMaxWidth().height(240.dp).testTag("avatar-preview-renderer"),
                    onViewport = { visible, size -> previewVisible = visible; previewSize = size })
                Text(if (failed) "Rendu indisponible · secours statique" else "État local : ${status.state}",
                    modifier = Modifier.testTag("avatar-engine-status"))
                if (showAssetButton) OutlinedButton(onClick = { preview = false; assetsPreview = true },
                    modifier = Modifier.fillMaxWidth().testTag("avatar-assets")) { Text("Ressources et calques locaux") }
                if (showAssetButton) OutlinedButton(onClick = { preview = false; v2Preview = true },
                    modifier = Modifier.fillMaxWidth().testTag("avatar-v2-open")) { Text("Athéna V2 · packs et comparaison") }
                for (animation in AvatarAnimationId.entries) {
                    OutlinedButton(onClick = {
                        command(AvatarCommand.Play(animation))
                        scope.launch { previewScroll.animateScrollTo(0) }
                    },
                        modifier = Modifier.fillMaxWidth().testTag("avatar-play-${animation.id}")) {
                        Text("${animation.id} · ${animation.name}")
                    }
                }
                OutlinedButton(onClick = {
                    AvatarAnimationId.entries.forEach { command(AvatarCommand.Stop(it)) }
                    scope.launch { previewScroll.animateScrollTo(0) }
                }, modifier = Modifier.fillMaxWidth().testTag("avatar-stop")) { Text("Arrêter les animations") }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (state in AvatarState.entries) {
                        TextButton(onClick = { command(AvatarCommand.SetState(state)) },
                            modifier = Modifier.weight(1f).testTag("avatar-state-${state.name}")) {
                            Text(state.name, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Text("LISTENING / THINKING / SPEAKING : contrats, sans voix ni lip-sync.",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = {
                    failed = false; command(AvatarCommand.Reset)
                    scope.launch { previewScroll.animateScrollTo(0) }
                },
                    modifier = Modifier.fillMaxWidth().testTag("avatar-reset")) { Text("Réinitialiser l’avatar") }
                Button(onClick = { preview = false; previewVisible = false },
                    modifier = Modifier.fillMaxWidth().testTag("avatar-close")) { Text("Fermer") }
            }
        }
    }
}

@Composable
private fun AvatarSurface(
    pose: () -> AvatarPose,
    artwork: AvatarArtwork,
    failed: Boolean,
    onFailure: () -> Unit,
    modifier: Modifier,
    onViewport: (Boolean, IntSize) -> Unit,
) {
    Canvas(modifier.onGloballyPositioned {
        val bounds = it.boundsInWindow()
        onViewport(it.isAttached && bounds.width > 1f && bounds.height > 1f, it.size)
    }.semantics { contentDescription = "Avatar féminin adulte original · démonstration 2.5D" }) {
        drawRect(Color(0xFF101C2C))
        val scale = min(size.width / 240f, size.height / 320f)
        if (scale > 0) withTransform({
            translate((size.width - 240f * scale) / 2, (size.height - 320f * scale) / 2)
            scale(scale, scale, Offset.Zero)
        }) {
            clipRect(0f, 0f, 240f, 320f) {
                if (failed) drawStaticAvatarFallback()
                else try { with(artwork) { render(pose()) } }
                catch (_: Exception) { onFailure(); drawStaticAvatarFallback() }
            }
        }
    }
}
