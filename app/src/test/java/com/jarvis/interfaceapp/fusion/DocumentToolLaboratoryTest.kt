package com.jarvis.interfaceapp.fusion

import org.junit.Test
import org.junit.Assert.*

class DocumentToolLaboratoryTest {
    private var time=1000L
    private fun ready()=DocumentToolLaboratory {time}.also {it.open();assertTrue(it.chooseSyntheticSource());assertTrue(it.preview())}
    @Test fun unavailableByDefaultCannotAct() {val l=DocumentToolLaboratory();assertFalse(l.chooseSyntheticSource());assertFalse(l.preview());assertNull(l.prepare())}
    @Test fun syntheticCitationHasVerifiableVersionOffsetsAndHash() {val l=ready();assertTrue(l.citationResolvesInFixture());val c=l.state.value.citation!!;assertEquals(64,c.hash.length);assertEquals(DocumentToolLaboratory.DOCUMENT.length,c.end);assertEquals(1,c.page);assertEquals(1,c.version)}
    @Test fun decimalPreviewIsExactWithoutExecutingServices() {assertEquals("0.30",ready().state.value.total)}
    @Test fun confirmationIsExactAndConsumedOnce() {val l=ready();val r=l.prepare()!!;assertTrue(r.destination.startsWith("lab://new-export/"));assertNotEquals(DocumentToolLaboratory.SOURCE_URI,r.destination);assertEquals("sum\n0.30\n",r.payload);assertTrue(l.confirm(r));val id=l.state.value.receiptId;assertFalse(l.confirm(r));assertEquals(id,l.state.value.receiptId);assertEquals(LabPhase.COMMITTED,l.state.value.phase)}
    @Test fun noConfirmationNoCommit() {val l=ready();l.prepare();assertEquals(LabPhase.PREPARED,l.state.value.phase);assertNull(l.state.value.receiptId)}
    @Test fun changedDestinationOrDigestIsRejected() {for(change in listOf<(LabReview)->LabReview>({it.copy(destination=DocumentToolLaboratory.SOURCE_URI)},{it.copy(digest="a".repeat(64))},{it.copy(payload="sum\n100\n")})){val l=ready();val r=l.prepare()!!;assertFalse(l.confirm(change(r)));assertEquals(LabPhase.REFUSED,l.state.value.phase);assertNull(l.state.value.receiptId)}}
    @Test fun revocationBeforeCommitPreventsEffect() {val l=ready();val r=l.prepare()!!;l.revoke();assertFalse(l.confirm(r));assertNull(l.state.value.receiptId);assertFalse(l.citationResolvesInFixture())}
    @Test fun revocationAfterCommitDoesNotInventRollback() {val l=ready();l.confirm(l.prepare()!!);val receipt=l.state.value.receiptId;l.revoke();assertEquals(LabPhase.COMMITTED,l.state.value.phase);assertEquals(receipt,l.state.value.receiptId)}
    @Test fun expiredConfirmationCannotCommit() {val l=ready();val r=l.prepare()!!;time+=30_000;assertFalse(l.confirm(r));assertEquals(LabPhase.REFUSED,l.state.value.phase)}
    @Test fun cancelledReviewCannotBeReplayed() {val l=ready();val r=l.prepare()!!;l.cancelReview();val next=l.prepare()!!;assertNotEquals(r.token,next.token);assertFalse(l.confirm(r));assertNull(l.state.value.receiptId)}
    @Test fun lostAckStaysUnknownAndCannotRetry() {val l=ready();val r=l.prepare()!!;l.confirm(r,true);assertEquals(LabPhase.UNKNOWN,l.state.value.phase);assertNull(l.prepare());assertFalse(l.confirm(r));assertEquals(LabPhase.UNKNOWN,l.state.value.phase)}
    @Test fun processReplacementHasNoCommandsToReplay() {val l=ready();l.confirm(l.prepare()!!,true);val next=DocumentToolLaboratory();assertFalse(next.state.value.enabled);assertEquals(LabPhase.EMPTY,next.state.value.phase);assertNull(next.state.value.receiptId)}
    @Test fun closeErasesFixtureGrantAndDraft() {val l=ready();l.prepare();l.close();assertFalse(l.citationResolvesInFixture());assertNull(l.state.value.review);assertFalse(l.state.value.enabled)}
}
