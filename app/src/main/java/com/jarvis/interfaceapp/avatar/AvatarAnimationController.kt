package com.jarvis.interfaceapp.avatar

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.random.Random

/** Wire identifiers are explicit: never persist enum ordinals. No IPC is implemented. */
enum class AvatarAnimationId(val id: String) { IDLE("ANIM_001"), BLINK("ANIM_002"), HEAD_TILT("ANIM_003") }
enum class AvatarState { IDLE, LISTENING, THINKING, SPEAKING }
enum class AvatarOperation { PLAY_ANIMATION, STOP_ANIMATION, SET_STATE, RESET_AVATAR }
sealed interface AvatarCommand {
    val operation: AvatarOperation
    data class Play(val animation: AvatarAnimationId) : AvatarCommand { override val operation = AvatarOperation.PLAY_ANIMATION }
    data class Stop(val animation: AvatarAnimationId) : AvatarCommand { override val operation = AvatarOperation.STOP_ANIMATION }
    data class SetState(val state: AvatarState) : AvatarCommand { override val operation = AvatarOperation.SET_STATE }
    data object Reset : AvatarCommand { override val operation = AvatarOperation.RESET_AVATAR }
}
data class AvatarPose(val breath: Float = 0f, val eyelid: Float = 0f, val headTilt: Float = 0f)
data class AvatarStatus(
    val state: AvatarState,
    val playing: Set<AvatarAnimationId>,
    val advancing: Boolean,
    val released: Boolean,
)

/** Single UI-thread owner; no timer/thread/file/Android dependency. Tick only while drawable.
 * Independent channels compose; retriggering an active pulse is idempotent.
 */
class AvatarAnimationController(private val blinkDelayMs: () -> Long = { Random.nextLong(2800, 6201) }) : AvatarSession {
    private data class Fade(val from: Float, var elapsed: Double = 0.0) {
        fun value() = if (elapsed >= 350.0) 0f else from * (1f - ease(elapsed / 350.0))
    }
    private var visible = false
    private var foreground = false
    private var width = 0
    private var height = 0
    private var released = false
    private var state = AvatarState.IDLE
    private var idle = true
    private var idleTime = 0.0
    private var idleEntry: Fade? = null
    private var idleStop: Fade? = null
    private var blink: Double? = null
    private var blinkStop: Fade? = null
    private var tilt: Double? = null
    private var tiltStop: Fade? = null
    private var tiltDirection = 1f
    private var automaticBlink = true
    private var nextBlink = delay()
    private var resetFrom: AvatarPose? = null
    private var resetTime = 0.0
    var pose = AvatarPose(); private set
    var framesAdvanced = 0L; private set
    private val mutableStatus = MutableStateFlow(snapshot())
    val status = mutableStatus.asStateFlow()

    private fun delay() = blinkDelayMs().coerceIn(1500, 10_000).toDouble()
    private fun work() = idle || idleStop != null || blink != null || blinkStop != null ||
        tilt != null || tiltStop != null || automaticBlink || resetFrom != null
    private fun snapshot() = AvatarStatus(state, buildSet {
        if (idle || idleStop != null) add(AvatarAnimationId.IDLE)
        if (blink != null || blinkStop != null) add(AvatarAnimationId.BLINK)
        if (tilt != null || tiltStop != null) add(AvatarAnimationId.HEAD_TILT)
    }, !released && visible && foreground && width > 0 && height > 0 && work(), released)
    private fun publish() { mutableStatus.value = snapshot() }
    override fun setVisible(visible: Boolean) { if (!released) { this.visible = visible; publish() } }
    fun setForeground(foreground: Boolean) { if (!released) { this.foreground = foreground; publish() } }
    override fun resize(widthPx: Int, heightPx: Int) {
        if (!released) { width = widthPx.coerceAtLeast(0); height = heightPx.coerceAtLeast(0); publish() }
    }
    override fun release() {
        if (released) return
        released = true; visible = false; foreground = false
        clear(); pose = AvatarPose(); publish()
    }
    private fun clear() {
        idle = false; idleEntry = null; idleStop = null; blink = null; blinkStop = null
        tilt = null; tiltStop = null; automaticBlink = false; resetFrom = null
    }
    fun execute(command: AvatarCommand): Boolean {
        if (released) return false
        if (command is AvatarCommand.Stop && resetFrom != null) {
            val current = pose; clear()
            idleStop = Fade(current.breath); blinkStop = Fade(current.eyelid); tiltStop = Fade(current.headTilt)
        }
        when (command) {
            is AvatarCommand.SetState -> state = command.state // Other states are contracts only.
            AvatarCommand.Reset -> {
                val current = pose; clear(); resetFrom = current; resetTime = 0.0; state = AvatarState.IDLE
            }
            is AvatarCommand.Play -> {
                if (resetFrom != null) return false
                when (command.animation) {
                    AvatarAnimationId.IDLE -> {
                        if (idle) return false
                        idle = true; idleStop = null; idleEntry = Fade(pose.breath)
                    }
                    AvatarAnimationId.BLINK -> {
                        if (blink != null || blinkStop != null) return false
                        blink = 0.0
                    }
                    AvatarAnimationId.HEAD_TILT -> {
                        if (tilt != null || tiltStop != null) return false
                        tiltDirection = -tiltDirection; tilt = 0.0
                    }
                }
            }
            is AvatarCommand.Stop -> when (command.animation) {
                AvatarAnimationId.IDLE -> { idle = false; idleEntry = null; idleStop = Fade(pose.breath) }
                AvatarAnimationId.BLINK -> { automaticBlink = false; blink = null; blinkStop = Fade(pose.eyelid) }
                AvatarAnimationId.HEAD_TILT -> { tilt = null; tiltStop = Fade(pose.headTilt) }
            }
        }
        publish(); return true
    }

    /** Large frame gaps are capped; hidden/background time is never caught up on resume. */
    fun advanceBy(deltaMs: Double): Boolean {
        if (!mutableStatus.value.advancing || !deltaMs.isFinite() || deltaMs <= 0) return false
        val dt = minOf(deltaMs, 100.0)
        framesAdvanced++
        resetFrom?.let { from ->
            resetTime += dt
            val amount = 1f - ease(resetTime / 350.0)
            pose = AvatarPose(from.breath * amount, from.eyelid * amount, from.headTilt * amount)
            if (resetTime >= 350) {
                resetFrom = null; pose = AvatarPose(); idle = true; idleTime = 0.0
                automaticBlink = true; nextBlink = delay()
            }
            publish(); return true
        }
        fun fade(f: Fade?): Float? {
            if (f == null) return null
            f.elapsed += dt
            return f.value()
        }
        val breathing = if (idle) {
            idleTime = (idleTime + dt) % 4200.0
            val target = ((1.0 - cos(2 * PI * idleTime / 4200.0)) * 0.5).toFloat()
            idleEntry?.let { entry ->
                entry.elapsed += dt
                val mix = ease(entry.elapsed / 350.0)
                if (entry.elapsed >= 350) idleEntry = null
                entry.from * (1f - mix) + target * mix
            } ?: target
        } else fade(idleStop) ?: 0f
        if (idleStop?.elapsed?.let { it >= 350 } == true) idleStop = null
        if (automaticBlink && blink == null && blinkStop == null) {
            nextBlink -= dt
            if (nextBlink <= 0) blink = 0.0
        }
        val closure = blink?.let {
            val time = it + dt; blink = time
            when {
                time < 100 -> ease(time / 100)
                time < 130 -> 1f
                time < 260 -> 1f - ease((time - 130) / 130)
                else -> { blink = null; nextBlink = delay(); 0f }
            }
        } ?: fade(blinkStop) ?: 0f
        if (blinkStop?.elapsed?.let { it >= 350 } == true) blinkStop = null
        val angle = tilt?.let {
            val time = it + dt; tilt = time
            when {
                time < 650 -> 5.5f * tiltDirection * ease(time / 650)
                time < 850 -> 5.5f * tiltDirection
                time < 2200 -> 5.5f * tiltDirection * (1f - ease((time - 850) / 1350))
                else -> { tilt = null; 0f }
            }
        } ?: fade(tiltStop) ?: 0f
        if (tiltStop?.elapsed?.let { it >= 350 } == true) tiltStop = null
        pose = AvatarPose(breathing, closure, angle)
        publish(); return true
    }
    companion object {
        private fun ease(value: Double): Float {
            val t = value.coerceIn(0.0, 1.0).toFloat()
            return t * t * (3f - 2f * t)
        }
    }
}
