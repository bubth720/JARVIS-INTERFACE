package fr.jarvis.m55d

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.*

/**
 * Process-scoped cockpit state (survives rotation and activity recreation). In this laboratory the
 * only event source is DEMO, shown as such on screen: it animates states, never produces an answer.
 * M55-E replaces it with the CORE/VOICE adapter through [accept], same contract.
 */
class CockpitViewModel(val thermal: () -> Int, private val clock: () -> Long = { SystemClock.elapsedRealtime() }) {
    private val holder = CockpitStateHolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var demo: Job? = null
    val rotation = SphereRotation()
    val stats = FrameStats()
    val regulator = RenderRegulator()
    var atlas: Map<CockpitState, ImageBitmap>? = null
    var state by mutableStateOf(CockpitState.IDLE); private set
    var level by mutableFloatStateOf(0f); private set
    var sourceLabel by mutableStateOf("aucune (état neutre)"); private set
    private val session = "lab-" + System.nanoTime()

    fun accept(e: CockpitEvent) {
        if (!holder.accept(e)) return
        state = holder.stateAt(clock()); level = e.audioLevel
        sourceLabel = when (e.source) { EventSource.DEMO -> "DÉMO (événements fictifs, aucune réponse)"; EventSource.CORE -> "CORE"; EventSource.VOICE -> "VOICE" }
    }
    var autoMode by mutableStateOf(true); private set
    fun forceMode(m: RenderMode) { regulator.auto = false; autoMode = false; regulator.force(m, clock()) }
    fun automatic() { regulator.auto = true; autoMode = true }
    fun stop() { demo?.cancel(); accept(CockpitEvent(session, CockpitState.IDLE, EventSource.DEMO, atMillis = clock())) }
    fun demoSubmit() {
        demo?.cancel()
        demo = scope.launch {
            accept(CockpitEvent(session, CockpitState.THINKING, EventSource.DEMO, atMillis = clock())); delay(1500)
            repeat(30) { i -> accept(CockpitEvent(session, CockpitState.SPEAKING, EventSource.DEMO, ((i % 6) / 5f), atMillis = clock())); delay(100) }
            accept(CockpitEvent(session, CockpitState.IDLE, EventSource.DEMO, atMillis = clock()))
        }
    }
    /** Periodic neutral fallback when no event arrives (absence of events = safe neutral state). */
    fun tick() { val s = holder.stateAt(clock()); if (s != state) state = s }
}
