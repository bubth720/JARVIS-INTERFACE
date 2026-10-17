package com.jarvis.interfaceapp.avatar

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class AvatarAnimationControllerTest {
    private fun controller() = AvatarAnimationController { 10_000 }.apply {
        setVisible(true); setForeground(true); resize(240, 320)
    }
    private fun advance(c: AvatarAnimationController, ms: Int) {
        var remaining = ms
        while (remaining > 0) { val step = minOf(50, remaining); c.advanceBy(step.toDouble()); remaining -= step }
    }
    private fun stop(c: AvatarAnimationController) { AvatarAnimationId.entries.forEach { c.execute(AvatarCommand.Stop(it)) } }
    @Test fun stableIdsAndOperationsAreExplicit() {
        assertEquals(listOf("ANIM_001", "ANIM_002", "ANIM_003"), AvatarAnimationId.entries.map { it.id })
        assertEquals(AvatarOperation.PLAY_ANIMATION, AvatarCommand.Play(AvatarAnimationId.IDLE).operation)
        assertEquals(AvatarOperation.STOP_ANIMATION, AvatarCommand.Stop(AvatarAnimationId.IDLE).operation)
        assertEquals(AvatarOperation.SET_STATE, AvatarCommand.SetState(AvatarState.IDLE).operation)
        assertEquals(AvatarOperation.RESET_AVATAR, AvatarCommand.Reset.operation)
    }
    @Test fun allHostGatesAreRequiredBeforeAnyCalculation() {
        val c = AvatarAnimationController()
        assertFalse(c.advanceBy(50.0))
        c.setVisible(true); assertFalse(c.advanceBy(50.0))
        c.setForeground(true); assertFalse(c.advanceBy(50.0))
        c.resize(240, 320); assertTrue(c.advanceBy(50.0))
        assertEquals(1L, c.framesAdvanced)
    }
    @Test fun idleBreathesAndReturnsPeriodicallyToNeutral() {
        val c = controller(); advance(c, 2100); assertEquals(1f, c.pose.breath, 0.0001f)
        advance(c, 2100); assertEquals(0f, c.pose.breath, 0.0001f)
        advance(c, 2100); assertEquals(1f, c.pose.breath, 0.0001f)
    }
    @Test fun blinkClosesProgressivelyHoldsAndOpensNaturally() {
        val c = controller(); c.execute(AvatarCommand.Play(AvatarAnimationId.BLINK))
        advance(c, 50); assertEquals(0.5f, c.pose.eyelid, 0.0001f)
        advance(c, 50); assertEquals(1f, c.pose.eyelid, 0.0001f)
        advance(c, 30); assertEquals(1f, c.pose.eyelid, 0.0001f)
        advance(c, 65); assertEquals(0.5f, c.pose.eyelid, 0.0001f)
        advance(c, 65); assertEquals(0f, c.pose.eyelid, 0.0001f)
        assertFalse(AvatarAnimationId.BLINK in c.status.value.playing)
    }
    @Test fun tiltInterpolatesAndReturnsWithoutJumping() {
        val c = controller(); c.execute(AvatarCommand.Play(AvatarAnimationId.HEAD_TILT))
        advance(c, 325); assertEquals(2.75f, abs(c.pose.headTilt), 0.0001f)
        advance(c, 325); assertEquals(5.5f, abs(c.pose.headTilt), 0.0001f)
        advance(c, 1540); assertTrue(abs(c.pose.headTilt) < 0.01f)
        advance(c, 10); assertEquals(0f, c.pose.headTilt, 0f)
    }
    @Test fun independentChannelsCanCombineAndRemainBounded() {
        val c = controller(); c.execute(AvatarCommand.Play(AvatarAnimationId.BLINK)); c.execute(AvatarCommand.Play(AvatarAnimationId.HEAD_TILT))
        advance(c, 100)
        assertTrue(c.pose.breath > 0); assertEquals(1f, c.pose.eyelid, 0f); assertTrue(abs(c.pose.headTilt) > 0)
        repeat(500) { c.advanceBy(50.0); assertTrue(c.pose.breath in 0f..1f); assertTrue(c.pose.eyelid in 0f..1f); assertTrue(abs(c.pose.headTilt) <= 5.5f) }
    }
    @Test fun retriggeringAnActiveAnimationDoesNotResetItsPose() {
        val c = controller(); c.execute(AvatarCommand.Play(AvatarAnimationId.HEAD_TILT)); advance(c, 300)
        val before = c.pose
        assertFalse(c.execute(AvatarCommand.Play(AvatarAnimationId.HEAD_TILT)))
        assertFalse(c.execute(AvatarCommand.Play(AvatarAnimationId.IDLE)))
        assertEquals(before, c.pose)
    }
    @Test fun stopAllFadesToNeutralThenRequiresNoFrames() {
        val c = controller(); c.execute(AvatarCommand.Play(AvatarAnimationId.HEAD_TILT)); c.execute(AvatarCommand.Play(AvatarAnimationId.BLINK)); advance(c, 100)
        val before = c.pose; stop(c); assertEquals(before, c.pose)
        advance(c, 150); assertTrue(abs(c.pose.headTilt) < abs(before.headTilt)); assertTrue(c.pose.eyelid in 0f..before.eyelid)
        advance(c, 200); assertEquals(AvatarPose(), c.pose); assertFalse(c.status.value.advancing)
        assertFalse(c.advanceBy(100.0))
    }
    @Test fun restartingIdleMidFadeIsContinuous() {
        val c = controller(); advance(c, 1500); c.execute(AvatarCommand.Stop(AvatarAnimationId.IDLE)); advance(c, 100)
        val before = c.pose; c.execute(AvatarCommand.Play(AvatarAnimationId.IDLE)); assertEquals(before, c.pose)
        c.advanceBy(1.0); assertEquals(before.breath, c.pose.breath, 0.001f)
    }
    @Test fun resetBlendsToNeutralAndRestartsLocalIdle() {
        val c = controller(); c.execute(AvatarCommand.Play(AvatarAnimationId.HEAD_TILT)); advance(c, 650)
        c.execute(AvatarCommand.SetState(AvatarState.SPEAKING)); val before = c.pose
        c.execute(AvatarCommand.Reset); assertEquals(before, c.pose); assertEquals(AvatarState.IDLE, c.status.value.state)
        advance(c, 350); assertEquals(AvatarPose(), c.pose)
        advance(c, 200); assertTrue(c.pose.breath > 0)
    }
    @Test fun stopDuringResetCancelsAutomaticRestart() {
        val c = controller(); advance(c, 1000); c.execute(AvatarCommand.Reset); advance(c, 100); stop(c)
        advance(c, 350); assertEquals(AvatarPose(), c.pose); assertFalse(c.status.value.advancing)
    }
    @Test fun offscreenAndBackgroundFreezePoseAndClockIndependently() {
        val c = controller(); advance(c, 500); val pose = c.pose; val frames = c.framesAdvanced
        c.setVisible(false); advance(c, 5000); assertEquals(pose, c.pose); assertEquals(frames, c.framesAdvanced)
        c.setVisible(true); c.setForeground(false); advance(c, 5000); assertEquals(pose, c.pose)
        c.setForeground(true); assertEquals(pose, c.pose); advance(c, 50); assertNotEquals(pose, c.pose)
    }
    @Test fun resizeKeepsPoseAndZeroSizeSuspendsCalculation() {
        val c = controller(); advance(c, 500); val before = c.pose
        c.resize(480, 640); assertEquals(before, c.pose)
        c.resize(-1, 0); assertFalse(c.advanceBy(50.0)); assertEquals(before, c.pose)
        c.resize(240, 320); assertTrue(c.advanceBy(50.0))
    }
    @Test fun releaseIsTerminalIdempotentAndDoesNotLeakWork() {
        val c = controller(); advance(c, 500); c.release(); c.release()
        c.setVisible(true); c.setForeground(true); c.resize(240, 320)
        assertTrue(c.status.value.released); assertFalse(c.status.value.advancing)
        assertFalse(c.advanceBy(100.0)); assertFalse(c.execute(AvatarCommand.Reset)); assertEquals(AvatarPose(), c.pose)
    }
    @Test fun invalidOrStalledFrameTimesCannotCorruptOrJumpThePose() {
        val c = controller(); val other = controller()
        listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0, 0.0).forEach { assertFalse(c.advanceBy(it)) }
        c.advanceBy(999_999.0); other.advanceBy(100.0); assertEquals(other.pose, c.pose)
    }
    @Test fun variableAutomaticBlinkIntervalsStopWhenRequested() {
        var calls = 0
        val c = AvatarAnimationController { calls++; if (calls == 1) 1500 else 3000 }.apply {
            setVisible(true); setForeground(true); resize(240, 320)
        }
        advance(c, 1450); assertEquals(0f, c.pose.eyelid, 0f)
        advance(c, 50); assertTrue(c.pose.eyelid > 0)
        advance(c, 250); assertEquals(2, calls)
        advance(c, 2900); assertEquals(0f, c.pose.eyelid, 0f)
        advance(c, 100); assertTrue(c.pose.eyelid > 0)
        c.execute(AvatarCommand.Stop(AvatarAnimationId.BLINK)); advance(c, 10_000)
        assertEquals(0f, c.pose.eyelid, 0f)
    }
    @Test fun declaredStatesDoNotInventVoiceOrStateSpecificAnimations() {
        val c = controller(); advance(c, 100); val before = c.pose; val playing = c.status.value.playing
        AvatarState.entries.forEach { state ->
            c.execute(AvatarCommand.SetState(state)); assertEquals(state, c.status.value.state)
            assertEquals(before, c.pose); assertEquals(playing, c.status.value.playing)
        }
    }
}
