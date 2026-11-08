package com.jarvis.interfaceapp.avatar.review

import org.junit.Assert.*
import org.junit.Test

class AvatarSceneTest {
    private class Resource : SceneResourceV1 { var pauses=0;var closes=0;var fail=false
        override fun pause() { pauses++ };override fun close() { closes++;if(fail)error("SYNTHETIC cleanup") } }
    private fun scene(r: Resource=Resource(),eco: Boolean=true,reduced: Boolean=false) =
        DeterministicAvatarSceneV1(r,123,eco,reduced).also { it.loadProfile(AvatarProfileV1("synthetic-test","a".repeat(64))) }
    @Test fun noProfileRemainsUnrendered() { val s=DeterministicAvatarSceneV1(Resource());assertEquals(SceneStateV1.NO_PROFILE,s.tick(0).state);assertEquals("NOT_IMPLEMENTED",s.diagnostics().renderer) }
    @Test fun stopDefersAutomaticGestureUntilNewIdleInterval() { val s=scene(eco=false);s.tick(20000);s.stop();assertNull(s.tick(20001).activeMotion) }
    @Test fun publicInteractionDoesNotInventPcm() { val s=scene();s.setPose(PoseV1.THINKING);assertFalse(s.tick(0).mouthActive);assertEquals(SceneStateV1.THINKING,s.diagnostics().state) }
    @Test fun pcmRequiredForSpeaking() { val s=scene();val e=s.diagnostics().epoch;assertTrue(s.setSpeechState(PlaybackEventV1(e,1,PlaybackStateV1.PREPARING)));assertFalse(s.diagnostics().mouthActive);s.setSpeechState(PlaybackEventV1(e,2,PlaybackStateV1.PCM_STARTED,128));assertTrue(s.diagnostics().mouthActive) }
    @Test fun textCannotActAsPlaybackEvent() { assertTrue(runCatching { PlaybackEventV1(1,1,PlaybackStateV1.PCM_STARTED,0) }.isFailure) }
    @Test fun stopInvalidatesOldAudioEpoch() { val s=scene();val e=s.diagnostics().epoch;s.stop();assertFalse(s.setSpeechState(PlaybackEventV1(e,1,PlaybackStateV1.PCM_STARTED,10)));assertFalse(s.diagnostics().mouthActive) }
    @Test fun duplicateAndOutOfOrderAudioRefused() { val s=scene();val e=s.diagnostics().epoch;assertTrue(s.setSpeechState(PlaybackEventV1(e,2,PlaybackStateV1.PREPARING)));assertFalse(s.setSpeechState(PlaybackEventV1(e,2,PlaybackStateV1.PCM_STARTED,8)));assertFalse(s.setSpeechState(PlaybackEventV1(e,1,PlaybackStateV1.PAUSED))) }
    @Test fun hiddenStopsAndPausesResources() { val r=Resource();val s=scene(r);s.setVisible(false);assertEquals(SceneStateV1.HIDDEN,s.tick(100).state);assertNull(s.diagnostics().suggestedFrameIntervalMillis);assertTrue(r.pauses >= 2) }
    @Test fun showingDoesNotRestartOldSpeech() { val s=scene();val e=s.diagnostics().epoch;s.setVisible(false);s.setVisible(true);assertFalse(s.setSpeechState(PlaybackEventV1(e,1,PlaybackStateV1.PCM_STARTED,8)));assertEquals(SceneStateV1.IDLE,s.diagnostics().state) }
    @Test fun errorIsHigherPriorityThanGesture() { val s=scene(eco=false);s.enqueueMotion(MotionV1(MotionKindV1.NOD,1000));s.setExpression(ExpressionV1.ERROR);assertEquals(SceneStateV1.ERROR,s.tick(100).state);assertNull(s.diagnostics().activeMotion);assertFalse(s.enqueueMotion(MotionV1(MotionKindV1.BLINK,100))) }
    @Test fun stopDoesNotClearErrorSilently() { val s=scene();s.setExpression(ExpressionV1.ERROR);s.stop();assertEquals(SceneStateV1.ERROR,s.diagnostics().state) }
    @Test fun reducedMotionHasNoAnimationClockRequest() { val s=scene(reduced=true);assertFalse(s.enqueueMotion(MotionV1(MotionKindV1.BLINK,200)));repeat(50){assertNull(s.tick(it*1000L).activeMotion)};assertNull(s.diagnostics().suggestedFrameIntervalMillis) }
    @Test fun ecoAllowsOnlyBoundedBlinkQueue() { val s=scene();assertFalse(s.enqueueMotion(MotionV1(MotionKindV1.NOD,200)));repeat(4){assertTrue(s.enqueueMotion(MotionV1(MotionKindV1.BLINK,200)))};assertFalse(s.enqueueMotion(MotionV1(MotionKindV1.BLINK,200))) }
    @Test fun longLogicalSequenceIsDeterministicAndInterruptible() { val a=scene(eco=false);val b=scene(eco=false);for(t in 0L..60_000L step 100)assertEquals(a.tick(t),b.tick(t));a.stop();assertNull(a.diagnostics().activeMotion);assertEquals(0,a.diagnostics().queuedMotions) }
    @Test fun rotationDoesNotChangeEpochOrRestartPose() { val s=scene();s.setPose(PoseV1.LISTENING);val e=s.diagnostics().epoch;s.resize(400,900);s.resize(1200,700);assertEquals(e,s.diagnostics().epoch);assertEquals(SceneStateV1.LISTENING,s.diagnostics().state) }
    @Test fun backwardsClockAndInvalidGeometryRefused() { val s=scene();s.tick(100);assertTrue(runCatching{s.tick(99)}.isFailure);assertTrue(runCatching{s.resize(-1,0)}.isFailure) }
    @Test fun pauseResumeRequiresFreshPlayback() { val s=scene();val e=s.diagnostics().epoch;s.pause();s.resume();assertFalse(s.setSpeechState(PlaybackEventV1(e,1,PlaybackStateV1.PCM_STARTED,8))) }
    @Test fun releaseClosesOnceAndPreventsMutation() { val r=Resource();val s=scene(r);s.release();s.release();assertEquals(1,r.closes);assertEquals(SceneStateV1.RELEASED,s.diagnostics().state);assertTrue(runCatching{s.tick(1)}.isFailure) }
    @Test fun cleanupFailureDoesNotEnableSilentReuse() { val r=Resource().also{it.fail=true};val s=scene(r);assertTrue(runCatching{s.release()}.isFailure);s.release();assertEquals(1,r.closes);assertEquals(SceneStateV1.RELEASED,s.diagnostics().state);assertFalse(s.diagnostics().resourcesReleased);assertEquals("CLEANUP_FAILED",s.diagnostics().resourceState) }
    @Test fun missingProfileClearsQueuedEvents() { val s=scene();s.enqueueMotion(MotionV1(MotionKindV1.BLINK,200));s.loadProfile(null);assertEquals(0,s.diagnostics().queuedMotions);assertEquals(SceneStateV1.NO_PROFILE,s.diagnostics().state) }
    @Test fun noVisemeClaimOrPrivateProfileDiagnostics() { val s=scene();assertEquals("UNSUPPORTED",s.diagnostics().visemes);assertFalse(s.diagnostics().toString().contains("synthetic-test")) }
}
