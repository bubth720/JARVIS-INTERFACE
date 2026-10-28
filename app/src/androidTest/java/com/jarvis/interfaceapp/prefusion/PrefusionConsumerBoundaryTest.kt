package com.jarvis.interfaceapp.prefusion

import com.jarvis.interfaceapp.ui.chat.core.*
import com.jarvis.interfaceapp.fusion.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.UUID

/** Real Android execution of the local consumer, not a connection to CORE or Android identity proof. */
class PrefusionConsumerBoundaryTest {
    private fun uuid()=UUID.randomUUID().toString()
    private class FixturePort(override val origin:CoreTextOrigin=CoreTextOrigin.LABORATORY):CoreConversationTransport {
        val health=MutableStateFlow(CoreTextLink.READY)
        override val connection=health.asStateFlow()
        val stream=MutableSharedFlow<CoreMessageEnvelope>(extraBufferCapacity=32)
        override val updates=stream.asSharedFlow()
        val key=ContentKey(UUID.randomUUID().toString(),UUID.randomUUID().toString(),1)
        var pendingAck:CompletableDeferred<ContentAck?>?=null
        var submission:SubmitUserText?=null
        var stops=0;var hangStop=false;var closed=false
        override suspend fun submit(value:SubmitUserText):ContentAck? {
            submission=value
            return pendingAck?.await() ?: ContentAck(value.submissionId,key)
        }
        override suspend fun stop(key:ContentKey):Boolean {
            stops++
            if(hangStop)awaitCancellation()
            return true
        }
        override fun close(){closed=true}
        suspend fun event(sequence:Long,body:ContentBody,eventId:String=UUID.randomUUID().toString(),eventKey:ContentKey=key) {
            stream.emit(CoreMessageEnvelope(eventId,eventKey,sequence,body));yield()
        }
    }
    private fun boundary(port:FixturePort=FixturePort(),block:suspend (FixturePort,CoreConversationClientAdapter)->Unit)=runBlocking {
        withContext(Dispatchers.Main.immediate) {
            val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
            val client=CoreConversationClientAdapter(port,scope,ContentClientBudget.Fusion02Review)
            try {yield();block(port,client)} finally {client.close();scope.cancel()}
        }
    }
    @Test fun noneOriginCannotUpgradeReadyOrSubmit()=boundary(FixturePort(CoreTextOrigin.NONE)) {p,c->
        assertEquals(CoreTextLink.UNAVAILABLE,c.state.value.link)
        p.health.value=CoreTextLink.UNAVAILABLE;yield();p.health.value=CoreTextLink.READY;yield()
        assertEquals(CoreTextLink.UNAVAILABLE,c.state.value.link);assertFalse(c.submit("synthetic"));assertNull(p.submission)
    }
    @Test fun replayedEventIdAtNewSequenceBlocksNewWork()=boundary {p,c->
        assertTrue(c.submit("synthetic"));yield();val id=uuid()
        p.event(1,ContentBody.AssistantTextChunk("public fixture"),id)
        p.event(2,ContentBody.Completed,id)
        assertEquals(CoreTextLink.UNKNOWN,c.state.value.link);assertTrue(c.state.value.busy)
        assertFalse(c.submit("must not retry"));assertEquals("public fixture",c.state.value.text)
    }
    @Test fun duplicatesWrongCorrelationAndLateContentNeverCreateSecondAnswer()=boundary {p,c->
        assertTrue(c.submit("synthetic"));yield();val id=uuid()
        p.event(1,ContentBody.AssistantTextChunk("A"),id)
        p.event(1,ContentBody.AssistantTextChunk("A"),id)
        p.event(2,ContentBody.AssistantTextChunk("foreign"),eventKey=p.key.copy(sessionId=uuid()))
        p.event(2,ContentBody.Completed)
        p.event(3,ContentBody.AssistantTextChunk("late"))
        assertEquals("A",c.state.value.text);assertFalse(c.state.value.busy)
    }
    @Test fun cancellationBeforeAckStopsCorrelatedRequestAndDropsText()=boundary {p,c->
        p.pendingAck=CompletableDeferred();assertTrue(c.submit("synthetic"));yield();c.cancel()
        p.pendingAck!!.complete(ContentAck(p.submission!!.submissionId,p.key));yield()
        assertEquals(1,p.stops)
        p.event(1,ContentBody.AssistantTextChunk("must not display"));p.event(2,ContentBody.Completed)
        assertTrue(c.state.value.busy);assertEquals("",c.state.value.text)
        p.event(3,ContentBody.Cancelled);assertFalse(c.state.value.busy)
        assertTrue(c.state.value.phase.contains("non attesté"))
    }
    @Test(timeout=15000) fun unresponsiveStopHasRealAndroidDeadlineAndRemainsUnknown()=boundary {p,c->
        p.hangStop=true;assertTrue(c.submit("synthetic"));yield();c.cancel()
        withTimeout(8000){c.state.first {it.link==CoreTextLink.UNKNOWN}}
        assertFalse(c.submit("retry"));assertTrue(c.state.value.phase.contains("expiré"))
    }
    @Test fun unknownResourceAndReconnectionDoNotResetAdmission()=boundary {p,c->
        c.submit("synthetic");yield();p.event(1,ContentBody.Failure(ContentFailure.RESOURCE_UNCONFIRMED))
        p.health.value=CoreTextLink.UNAVAILABLE;yield();p.health.value=CoreTextLink.READY;yield()
        assertEquals(CoreTextLink.UNKNOWN,c.state.value.link);assertFalse(c.submit("retry"))
    }
    @Test fun reviewUtf8AndChunkLimitsAreEnforcedWithoutTruncation()=boundary {p,c->
        assertFalse(c.submit("🙂".repeat(257)));assertFalse(c.submit("\uD800"));assertFalse(c.submit(" "))
        assertTrue(c.submit("synthetic"));yield();p.event(1,ContentBody.AssistantTextChunk("a".repeat(4097)))
        assertEquals("",c.state.value.text);assertEquals(CoreTextLink.UNKNOWN,c.state.value.link)
    }
    @Test fun skippedSequencesExhaustBoundedQueueRatherThanFakingCompletion()=boundary {p,c->
        c.submit("synthetic");yield();for(sequence in 2L..10L)p.event(sequence,ContentBody.Completed)
        assertEquals(CoreTextLink.UNKNOWN,c.state.value.link);assertTrue(c.state.value.busy)
    }
    @Test fun closedSessionAndFreshUnavailableClientCannotReuseRamContent()=boundary {p,c->
        c.submit("synthetic");yield();p.event(1,ContentBody.AssistantTextChunk("public fixture"));c.close()
        assertTrue(p.closed);assertEquals("",c.state.value.text);assertNull(c.state.value.key)
        assertFalse(c.submit("retry"))
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        val fresh=CoreConversationClientAdapter(UnavailableCoreConversationTransport(),scope)
        try {yield();assertEquals(CoreTextLink.UNAVAILABLE,fresh.state.value.link);assertEquals("",fresh.state.value.text)}
        finally {fresh.close();scope.cancel()}
    }
    @Test fun exactSyntheticSignatureCannotAuthenticateAlteredReportOrLabOrigin() {
        // Ephemeral test key, never persisted or claimed to be an Android package certificate.
        val pair=KeyPairGenerator.getInstance("RSA").apply {initialize(2048)}.generateKeyPair()
        val epoch=uuid()
        val report=HostCapabilityReport(epoch,1,1,1000,HostOrigin.REAL_HOST,
            listOf(CapabilityEvidence(CapabilityId.CORE,CapabilityStatus.READY,"SYNTHETIC_FIXTURE")))
        val signature=Signature.getInstance("SHA256withRSA").apply {initSign(pair.private);update(encoded(report))}.sign()
        val verifier=HostEvidenceVerifier {r->Signature.getInstance("SHA256withRSA").run {initVerify(pair.public);update(encoded(r));verify(signature)}}
        val accepted=CapabilityProjection.evaluate(report,verifier,epoch,1,0,1001)
        assertEquals(CapabilityStatus.READY,accepted.first().status) // fixture trust only
        assertTrue(accepted.drop(1).all {it.status==CapabilityStatus.UNKNOWN})
        for(tampered in listOf(report.copy(sequence=2),report.copy(origin=HostOrigin.LAB),
            report.copy(capabilities=listOf(CapabilityEvidence(CapabilityId.CORE,CapabilityStatus.READY,"CLAIMED_UID_1000"))))) {
            assertTrue(CapabilityProjection.evaluate(tampered,verifier,epoch,1,0,1001).none {it.connected})
        }
    }
    @Test fun staleEpochReplayVersionAndExpiredCapabilitiesFailClosedOnAndroid() {
        val epoch=uuid();val report=HostCapabilityReport(epoch,1,2,1000,HostOrigin.REAL_HOST,
            listOf(CapabilityEvidence(CapabilityId.CORE,CapabilityStatus.READY,"SYNTHETIC_FIXTURE")))
        for(r in listOf(report.copy(epoch=uuid()),report.copy(generation=0),report.copy(version=1),report.copy(sequence=1),report.copy(observedAtMs=31002))) {
            assertTrue(CapabilityProjection.evaluate(r,HostEvidenceVerifier {true},epoch,1,1,31001).none {it.connected})
        }
        assertTrue(CapabilityProjection.evaluate(report,HostEvidenceVerifier {true},epoch,1,1,31001).none {it.connected})
        assertTrue(CapabilityProjection.evaluate(report,HostEvidenceVerifier {error("synthetic trust unavailable")},epoch,1,1,1001).none {it.connected})
    }
    private fun encoded(r:HostCapabilityReport):ByteArray=ByteArrayOutputStream().also {out->
        DataOutputStream(out).use {d->
            d.writeInt(r.version);d.writeUTF(r.epoch);d.writeLong(r.generation);d.writeLong(r.sequence);d.writeLong(r.observedAtMs);d.writeUTF(r.origin.name)
            d.writeInt(r.capabilities.size);r.capabilities.forEach {d.writeUTF(it.id.name);d.writeUTF(it.status.name);d.writeUTF(it.code)}
        }
    }.toByteArray()
}
