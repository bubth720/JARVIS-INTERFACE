package com.jarvis.interfaceapp.avatar.review

import com.jarvis.interfaceapp.avatar.AvatarSession
import java.util.ArrayDeque

/** Review-only contract. No renderer, asset loader, document, microphone or LLM authority. */
interface AvatarSceneV1 : AvatarSession {
    fun loadProfile(profile: AvatarProfileV1?)
    fun setPose(pose: PoseV1)
    fun setExpression(expression: ExpressionV1)
    fun enqueueMotion(motion: MotionV1): Boolean
    fun setSpeechState(event: PlaybackEventV1): Boolean
    fun pause()
    fun resume()
    fun stop()
    fun tick(monotonicMillis: Long): SceneSnapshotV1
    fun diagnostics(): SceneSnapshotV1
}
enum class PoseV1 { IDLE, LISTENING, THINKING }
enum class ExpressionV1 { NEUTRAL, SMILE, SURPRISE, HESITATION, ERROR }
enum class MotionKindV1 { BLINK, BREATHE, GLANCE, NOD }
enum class PlaybackStateV1 { PREPARING, PCM_STARTED, PAUSED, STOPPED, ERROR }
enum class SceneStateV1 { NO_PROFILE, IDLE, LISTENING, THINKING, SPEAKING, PAUSED, HIDDEN, ERROR, RELEASED }
class AvatarProfileV1(val profileId: String, val packSha256: String) {
    init { require(profileId.matches(Regex("[a-z0-9_-]{1,48}")) && packSha256.matches(Regex("[0-9a-f]{64}"))) }
    override fun toString() = "AvatarProfileV1(redacted, reviewOnly)"
}
class MotionV1(val kind: MotionKindV1, val durationMillis: Long) {
    init { require(durationMillis in 100..5000) }
}
/** The epoch must be obtained from the active scene. A token/text event is never a playback event. */
class PlaybackEventV1(val epoch: Long, val sequence: Long, val state: PlaybackStateV1,
                      val playedPcmSamples: Long = 0) {
    init { require(epoch >= 0 && sequence > 0 && playedPcmSamples >= 0)
        require(state != PlaybackStateV1.PCM_STARTED || playedPcmSamples > 0) }
}
data class SceneSnapshotV1(val state: SceneStateV1, val pose: PoseV1, val expression: ExpressionV1,
    val mouthActive: Boolean, val epoch: Long, val queuedMotions: Int, val activeMotion: MotionKindV1?,
    val visible: Boolean, val paused: Boolean, val widthPx: Int, val heightPx: Int,
    val suggestedFrameIntervalMillis: Long?, val resourcesReleased: Boolean,
    val resourceState: String, val renderer: String = "NOT_IMPLEMENTED", val qualification: String = "JVM_REVIEW_ONLY",
    val visemes: String = "UNSUPPORTED")
/** Rendering resource hook for a future adapter. Unit fixtures are not real players/textures. */
interface SceneResourceV1 { fun pause(); fun close() }

/** Caller-driven deterministic logical controller. It starts no thread and loads no file. */
class DeterministicAvatarSceneV1(private val resources: SceneResourceV1, private val seed: Long = 1,
    private val eco: Boolean = true, private val reduceMotion: Boolean = false) : AvatarSceneV1 {
    private var profile: AvatarProfileV1? = null
    private var pose = PoseV1.IDLE
    private var expression = ExpressionV1.NEUTRAL
    private var visible = true
    private var paused = false
    private var released = false
    private var resourcesReleased = false
    private var cleanupFailed = false
    private var mouth = false
    private var epoch = 0L
    private var sequence = 0L
    private var width = 0
    private var height = 0
    private var last = -1L
    private var random = seed and 0x7fffffffL
    private var nextAutomatic = 0L
    private var active: MotionV1? = null
    private var activeUntil = 0L
    private val queue = ArrayDeque<MotionV1>()
    private fun live() = check(!released) { "AVATAR_RELEASED" }
    private fun clearMotion() { queue.clear();active=null;activeUntil=0 }
    private fun invalidatePlayback() { check(epoch < Long.MAX_VALUE);epoch++;sequence=0;mouth=false }
    private fun priorityBlocked() = profile == null || !visible || paused || expression == ExpressionV1.ERROR
    private fun delay(): Long { random=(1103515245L*random+12345L) and 0x7fffffffL;return (if(eco) 7000 else 4000)+(random%5001) }
    @Synchronized override fun loadProfile(profile: AvatarProfileV1?) {
        live();resources.pause();invalidatePlayback();clearMotion();this.profile=profile
        pose=PoseV1.IDLE;expression=ExpressionV1.NEUTRAL;nextAutomatic=(last.coerceAtLeast(0)+delay())
    }
    @Synchronized override fun setPose(pose: PoseV1) { live();this.pose=pose }
    @Synchronized override fun setExpression(expression: ExpressionV1) {
        live();this.expression=expression;nextAutomatic=last.coerceAtLeast(0)+delay()
        if(expression == ExpressionV1.ERROR) { invalidatePlayback();clearMotion();resources.pause() }
    }
    @Synchronized override fun enqueueMotion(motion: MotionV1): Boolean {
        live();if(priorityBlocked() || reduceMotion || queue.size >= 4 || (eco && motion.kind != MotionKindV1.BLINK))return false
        queue.addLast(motion);return true
    }
    @Synchronized override fun setSpeechState(event: PlaybackEventV1): Boolean {
        live();if(priorityBlocked() || event.epoch != epoch || event.sequence <= sequence)return false
        sequence=event.sequence
        when(event.state) {
            PlaybackStateV1.PCM_STARTED -> { mouth=true;clearMotion() }
            PlaybackStateV1.PREPARING, PlaybackStateV1.PAUSED -> mouth=false
            PlaybackStateV1.STOPPED -> { invalidatePlayback();clearMotion();pose=PoseV1.IDLE }
            PlaybackStateV1.ERROR -> { invalidatePlayback();clearMotion();expression=ExpressionV1.ERROR;resources.pause() }
        }
        return true
    }
    @Synchronized override fun setVisible(visible: Boolean) {
        live();if(this.visible == visible)return;this.visible=visible
        invalidatePlayback();clearMotion();nextAutomatic=last.coerceAtLeast(0)+delay()
        if(!visible)resources.pause()
    }
    @Synchronized override fun resize(widthPx: Int,heightPx: Int) {
        live();require(widthPx in 1..16384 && heightPx in 1..16384);width=widthPx;height=heightPx
    }
    @Synchronized override fun pause() { live();paused=true;invalidatePlayback();clearMotion();resources.pause() }
    @Synchronized override fun resume() { live();paused=false;nextAutomatic=last.coerceAtLeast(0)+delay() }
    @Synchronized override fun stop() { live();invalidatePlayback();clearMotion();pose=PoseV1.IDLE;nextAutomatic=last.coerceAtLeast(0)+delay();resources.pause() }
    @Synchronized override fun release() {
        if(released)return
        // Remain fail-closed if the adapter fails cleanup. Retry is an explicit future host decision.
        released=true;invalidatePlayback();clearMotion();profile=null;mouth=false
        try { resources.close();resourcesReleased=true } catch(failure: Throwable) { cleanupFailed=true;throw failure }
    }
    @Synchronized override fun tick(monotonicMillis: Long): SceneSnapshotV1 {
        live();require(monotonicMillis in 0..(Long.MAX_VALUE-12000) && monotonicMillis >= last);last=monotonicMillis
        if(priorityBlocked() || reduceMotion || mouth)clearMotion()
        else {
            if(active != null && monotonicMillis >= activeUntil)active=null
            if(active == null && queue.isNotEmpty()) {
                active=queue.removeFirst();activeUntil=Math.addExact(monotonicMillis,active!!.durationMillis)
            }else if(active == null && monotonicMillis >= nextAutomatic) {
                active=MotionV1(if(eco) MotionKindV1.BLINK else MotionKindV1.entries[(random%4).toInt()],250)
                activeUntil=Math.addExact(monotonicMillis,250);nextAutomatic=Math.addExact(monotonicMillis,delay())
            }
        }
        return diagnostics()
    }
    @Synchronized override fun diagnostics(): SceneSnapshotV1 {
        val state=when {
            released -> SceneStateV1.RELEASED
            !visible -> SceneStateV1.HIDDEN
            expression == ExpressionV1.ERROR -> SceneStateV1.ERROR
            paused -> SceneStateV1.PAUSED
            profile == null -> SceneStateV1.NO_PROFILE
            mouth -> SceneStateV1.SPEAKING
            pose == PoseV1.LISTENING -> SceneStateV1.LISTENING
            pose == PoseV1.THINKING -> SceneStateV1.THINKING
            else -> SceneStateV1.IDLE
        }
        return SceneSnapshotV1(state,pose,expression,mouth,epoch,queue.size,active?.kind,visible,paused,width,height,
            if(released || priorityBlocked() || reduceMotion)null else if(eco)250 else 50,resourcesReleased,
            if(cleanupFailed) "CLEANUP_FAILED" else if(resourcesReleased) "RELEASED" else "NOT_ATTESTED")
    }
}
