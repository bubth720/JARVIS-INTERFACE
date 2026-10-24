package com.jarvis.interfaceapp.ui.chat.core

import com.jarvis.interfaceapp.ui.chat.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class CoreConversationClientTest {
    private fun id()=UUID.randomUUID().toString()
    private class Port:CoreConversationTransport {
        override var origin=CoreTextOrigin.LABORATORY
        val health=MutableStateFlow(CoreTextLink.READY)
        override val connection=health.asStateFlow()
        val stream=MutableSharedFlow<CoreMessageEnvelope>(extraBufferCapacity=64)
        override val updates=stream.asSharedFlow()
        var key=ContentKey(UUID.randomUUID().toString(),UUID.randomUUID().toString(),0)
        var ackDelay=0L;var refused=false;var badAck=false;var early=false;var stops=0;var stopAccepted=true
        override suspend fun submit(value:SubmitUserText):ContentAck? {
            if(early) { emit(1,ContentBody.AssistantTextChunk("précoce"));yield() }
            delay(ackDelay)
            return if(refused) null else ContentAck(if(badAck)UUID.randomUUID().toString() else value.submissionId,key)
        }
        override suspend fun stop(key:ContentKey):Boolean { stops++;return stopAccepted }
        suspend fun emit(sequence:Long,body:ContentBody,key:ContentKey=this.key,eventId:String=UUID.randomUUID().toString()) {
            stream.emit(CoreMessageEnvelope(eventId,key,sequence,body));yield()
        }
    }
    @Test fun absentOwnerIsUnavailableAndCannotSubmit()=runTest {
        val c=CoreConversationClientAdapter(UnavailableCoreConversationTransport(),this);runCurrent()
        assertEquals(CoreTextLink.UNAVAILABLE,c.state.value.link);assertFalse(c.submit("bonjour"));assertEquals("",c.state.value.text);c.close()
    }
    @Test fun readyAnnouncementWithoutSupportedOriginIsNotAuthorization()=runTest {
        val p=Port().also { it.origin=CoreTextOrigin.NONE };val c=CoreConversationClientAdapter(p,this);runCurrent()
        assertFalse(c.submit("test"));c.close()
    }
    @Test fun validAckChunksAndTerminalAreCorrelated()=runTest {
        val p=Port();val c=CoreConversationClientAdapter(p,this);runCurrent();assertTrue(c.submit("public"));runCurrent()
        p.emit(1,ContentBody.AssistantTextChunk("A"));p.emit(2,ContentBody.AssistantTextChunk("B"));p.emit(3,ContentBody.Completed);runCurrent()
        assertEquals("AB",c.state.value.text);assertFalse(c.state.value.busy);assertTrue(c.state.value.phase.contains("LAB"));c.close()
    }
    @Test fun reorderedChunksAreBufferedWithinBoundAndAppliedOnce()=runTest {
        val p=Port();val c=CoreConversationClientAdapter(p,this);runCurrent();c.submit("public");runCurrent()
        p.emit(3,ContentBody.Completed);p.emit(2,ContentBody.AssistantTextChunk("B"));p.emit(1,ContentBody.AssistantTextChunk("A"));runCurrent()
        assertEquals("AB",c.state.value.text);assertFalse(c.state.value.busy);c.close()
    }
    @Test fun wrongSessionRequestOrGenerationCannotInjectText()=runTest {
        val p=Port();val c=CoreConversationClientAdapter(p,this);runCurrent();c.submit("public");runCurrent()
        for(k in listOf(p.key.copy(sessionId=id()),p.key.copy(requestId=id()),p.key.copy(generation=1))) p.emit(1,ContentBody.AssistantTextChunk("étranger"),k)
        runCurrent();assertEquals("",c.state.value.text);c.close()
    }
    @Test fun duplicateAndPostTerminalMessagesAreIgnored()=runTest {
        val p=Port();val c=CoreConversationClientAdapter(p,this);runCurrent();c.submit("public");runCurrent();val event=id()
        p.emit(1,ContentBody.AssistantTextChunk("A"),eventId=event);p.emit(1,ContentBody.AssistantTextChunk("A"),eventId=event)
        p.emit(2,ContentBody.Completed);p.emit(3,ContentBody.AssistantTextChunk("tardif"));runCurrent()
        assertEquals("A",c.state.value.text);c.close()
    }
    @Test fun ackTimeoutBlocksRestartEvenAfterConnectionRestores()=runTest {
        val p=Port().also { it.ackDelay=10000 };val c=CoreConversationClientAdapter(p,this);runCurrent();c.submit("public");runCurrent()
        advanceTimeBy(5001);runCurrent();assertEquals(CoreTextLink.UNKNOWN,c.state.value.link);assertFalse(c.submit("nouveau"));c.close()
    }
    @Test fun reusedAckKeyAndWrongSubmissionAckAreRejected()=runTest {
        val p=Port();val c=CoreConversationClientAdapter(p,this);runCurrent();c.submit("un");runCurrent();p.emit(1,ContentBody.Completed);runCurrent()
        c.submit("deux");runCurrent();assertEquals(CoreTextLink.UNKNOWN,c.state.value.link);c.close()
        val q=Port().also { it.badAck=true };val d=CoreConversationClientAdapter(q,this);runCurrent();d.submit("public");runCurrent()
        assertEquals(CoreTextLink.UNKNOWN,d.state.value.link);d.close()
    }
    @Test fun earlyContentIsNotLostOrAcceptedWithoutMatchingAck()=runTest {
        val p=Port().also { it.early=true };val c=CoreConversationClientAdapter(p,this);runCurrent();c.submit("public");runCurrent()
        assertEquals("précoce",c.state.value.text);c.close()
    }
    @Test fun cancellationDropsContentAndWaitsForLogicalConfirmation()=runTest {
        val p=Port();val c=CoreConversationClientAdapter(p,this);runCurrent();c.submit("public");runCurrent();c.cancel();runCurrent()
        p.emit(1,ContentBody.AssistantTextChunk("interdit"));p.emit(2,ContentBody.Completed);runCurrent();assertTrue(c.state.value.busy)
        p.emit(3,ContentBody.Cancelled);runCurrent();assertFalse(c.state.value.busy);assertEquals("",c.state.value.text);assertEquals(1,p.stops);c.close()
    }
    @Test fun cancellationBeforeAckStopsTheAckedRequest()=runTest {
        val p=Port().also { it.ackDelay=100 };val c=CoreConversationClientAdapter(p,this);runCurrent();c.submit("public");runCurrent();c.cancel()
        advanceTimeBy(101);runCurrent();assertEquals(1,p.stops);assertTrue(c.state.value.busy);c.close()
    }
    @Test fun resourceUnknownAndConnectionLossNeverLookFinished()=runTest {
        val p=Port();val c=CoreConversationClientAdapter(p,this);runCurrent();c.submit("public");runCurrent()
        p.emit(1,ContentBody.Failure(ContentFailure.RESOURCE_UNCONFIRMED));runCurrent()
        assertEquals(CoreTextLink.UNKNOWN,c.state.value.link);assertFalse(c.submit("autre"));c.close()
        val q=Port();val d=CoreConversationClientAdapter(q,this);runCurrent();d.submit("public");runCurrent();q.health.value=CoreTextLink.UNAVAILABLE;runCurrent()
        q.health.value=CoreTextLink.READY;runCurrent();assertEquals(CoreTextLink.UNKNOWN,d.state.value.link);d.close()
    }
    @Test fun metadataCompletionAloneDoesNotInventAnAssistantResponse()=runTest {
        val p=Port();val c=CoreConversationClientAdapter(p,this);runCurrent();c.submit("public");runCurrent()
        c.observeMetadata(CoreMetadata(CoreRequestKey(p.key.sessionId,p.key.requestId),id(),CoreUiKind.RESPONSE_COMPLETED,0,1))
        assertEquals("",c.state.value.text);assertTrue(c.state.value.busy)
        p.emit(1,ContentBody.Completed);runCurrent();assertTrue(c.state.value.phase.contains("sans texte"));c.close()
    }
    @Test fun boundedReorderQueueAndUtf8BudgetsAreEnforced()=runTest {
        val p=Port();val c=CoreConversationClientAdapter(p,this);runCurrent();assertFalse(c.submit("🙂".repeat(4097)));assertFalse(c.submit("\uD800"));assertFalse(c.submit(" "))
        c.submit("public");runCurrent();for(i in 2L..10L)p.emit(i,ContentBody.Completed);runCurrent()
        assertEquals(CoreTextLink.UNKNOWN,c.state.value.link);c.close()
    }
    @Test fun aggregateTextIsBoundedBeforeAppendedToState()=runTest {
        val p=Port();val c=CoreConversationClientAdapter(p,this);runCurrent();c.submit("public");runCurrent()
        for(i in 1L..17L)p.emit(i,ContentBody.AssistantTextChunk("a".repeat(16*1024)))
        runCurrent();assertEquals(256*1024,c.state.value.text.length);assertEquals(CoreTextLink.UNKNOWN,c.state.value.link);c.close()
    }
    @Test fun toolsAreOnlyPresentedAndNeverExecute()=runTest {
        val p=Port();val c=CoreConversationClientAdapter(p,this);runCurrent();c.submit("public");runCurrent();p.emit(1,ContentBody.ToolPending("csv.normalize"));runCurrent()
        assertEquals(listOf("csv.normalize"),c.state.value.toolsPending);assertTrue(c.state.value.busy);c.close()
    }
    @Test fun closeRevokesPendingAckAndClearsEphemeralContent()=runTest {
        val p=Port().also { it.ackDelay=100 };val c=CoreConversationClientAdapter(p,this);runCurrent();c.submit("public");runCurrent();c.close();advanceUntilIdle()
        assertEquals(CoreTextLink.DISABLED,c.state.value.link);assertNull(c.state.value.key);assertEquals("",c.state.value.text);assertFalse(c.submit("nouveau"))
    }
    @Test fun malformedEnvelopesAreRefusedAtBoundary() {
        assertThrows(Exception::class.java) { ContentKey("bad",id(),0) }
        assertThrows(Exception::class.java) { ContentKey(id(),id(),-1) }
        assertThrows(Exception::class.java) { ContentBody.AssistantTextChunk("\u0000") }
        assertThrows(Exception::class.java) { CoreMessageEnvelope(id(),ContentKey(id(),id(),0),0,ContentBody.Completed) }
    }
}
