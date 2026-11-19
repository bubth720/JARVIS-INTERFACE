package fr.jarvis.v3

import fr.jarvis.v3.model.HoloState

/** In-memory counters enabled by the instrumentation, independent of Compose snapshot state. */
internal object RenderDiagnostics {
    enum class Signal {
        SCENE_COMMIT, SPHERE_COMMIT, SPHERE_DRAW, WINDOW_DESCENDANT_INVALIDATION,
        VISUAL_TRANSITION, SPHERE_CLOCK_FRAME, SPHERE_CLOCK_START, SPHERE_CLOCK_STOP,
        DOCK_COMMIT, WAVEFORM_SHIFT, WAVEFORM_DRAW,
        CONVERSATION_COMMIT, AUTO_SCROLL_START, AUTO_SCROLL_STOP,
        GALLERY_TICK, GALLERY_START, GALLERY_STOP,
    }

    @Volatile
    var active: Counters? = null

    class Counters {
        private val counts = LongArray(Signal.entries.size)
        private val transitions = ArrayDeque<String>()
        private var lastState: HoloState? = null

        @Synchronized
        fun count(signal: Signal) {
            counts[signal.ordinal]++
        }

        @Synchronized
        fun state(state: HoloState) {
            if (state == lastState) return
            count(Signal.VISUAL_TRANSITION)
            lastState = state
            if (transitions.size == 32) transitions.removeFirst()
            transitions.addLast(state.name)
        }

        @Synchronized
        fun counts(): Map<String, Long> = Signal.entries.associate { it.name to counts[it.ordinal] }

        @Synchronized
        fun states(): List<String> = transitions.toList()
    }
}
