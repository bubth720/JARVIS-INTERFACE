package fr.jarvis.v3

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import fr.jarvis.m55d.CockpitState
import fr.jarvis.m55d.FrameStats
import fr.jarvis.m55d.KnowledgeSphere
import fr.jarvis.m55d.RenderMode
import fr.jarvis.m55d.RenderRegulator
import fr.jarvis.m55d.SphereRotation
import fr.jarvis.v3.model.HoloDetail
import fr.jarvis.v3.model.HoloState
import kotlinx.coroutines.delay
import kotlin.math.sin

/**
 * Visual prototype (§11 V3-02/03): every sphere state rendered WITHOUT the engine, clearly labelled
 * EXEMPLE (no real data, no answer). Not exported. Also the before/after bench used by the tests:
 * renderer "m55d" = V1 cockpit sphere, "v3" = holographic sphere, same box, same state, same profile.
 */
class StateGalleryActivity : ComponentActivity() {
    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val bench = intent.getBooleanExtra("bench", false)
        val renderer0 = intent.getStringExtra("renderer") ?: "v3"
        val state0 = runCatching { HoloState.valueOf(intent.getStringExtra("state") ?: "IDLE") }.getOrDefault(HoloState.IDLE)
        val mode0 = runCatching { RenderMode.valueOf(intent.getStringExtra("mode") ?: "BALANCED") }.getOrDefault(RenderMode.BALANCED)
        setContent { HoloTheme {
            var renderer by remember { mutableStateOf(renderer0) }
            var state by remember { mutableStateOf(state0) }
            var mode by remember { mutableStateOf(mode0) }
            var t by remember { mutableFloatStateOf(0f) }
            LaunchedEffect(Unit) { while (true) { delay(50); t += 0.05f } }
            val exampleLevel = if (state == HoloState.LISTENING || state == HoloState.SPEAKING) (0.45f + 0.35f * sin(t * 5f)).coerceIn(0f, 1f) else 0f
            Column(Modifier.fillMaxSize().background(Holo.background).semantics { testTagsAsResourceId = true }.safeDrawingPadding().padding(12.dp)) {
                if (!bench) {
                    Text("GALERIE DES ÉTATS — EXEMPLE", style = Holo.label)
                    Text("Démonstration visuelle sans moteur : aucune donnée réelle, aucune réponse. Niveaux audio simulés (exemple).", style = Holo.small)
                }
                Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                    Canvas(Modifier.fillMaxSize()) { hudBackdrop() }
                    if (renderer == "m55d") {
                        val rotation = remember { SphereRotation() }; val stats = remember { FrameStats() }
                        val regulator = remember(mode) { RenderRegulator(mode).also { it.auto = false } }
                        KnowledgeSphere(toCockpit(state), exampleLevel, mode, rotation, stats, regulator, { 0 }, Modifier.fillMaxSize())
                    } else {
                        val motion = remember { SphereMotion() }
                        HologramSphere(state, exampleLevel, if (state == HoloState.STREAMING) (t * 2).toInt() else 0,
                            if (bench) emptyList() else listOf("Exemple", "Démonstration"), HoloDetail.of(mode), animate = true, motion = motion,
                            onVsync = {}, onKeyword = {}, onCoreTap = {}, modifier = Modifier.fillMaxSize())
                    }
                }
                if (!bench) {
                    Text("ÉTAT : ${state.label.uppercase()} · PROFIL : ${modeLabel(mode).uppercase()} · RENDU : ${if (renderer == "v3") "V3" else "V1 (M55-D)"}", style = Holo.label)
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (s in HoloState.entries) Pill(s.label, "g-state-${s.name.lowercase()}", active = s == state) { state = s }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (m in RenderMode.entries) Pill(modeLabel(m), "g-mode-${m.name.lowercase()}", active = m == mode) { mode = m }
                        Pill("V3", "g-v3", active = renderer == "v3") { renderer = "v3" }
                        Pill("V1 (avant)", "g-m55d", active = renderer == "m55d") { renderer = "m55d" }
                    }
                }
            }
        } }
    }
    companion object {
        fun toCockpit(s: HoloState) = when (s) {
            HoloState.IDLE -> CockpitState.IDLE; HoloState.LISTENING -> CockpitState.LISTENING; HoloState.SPEAKING -> CockpitState.SPEAKING
            HoloState.ERROR -> CockpitState.ERROR; else -> CockpitState.THINKING
        }
    }
}
