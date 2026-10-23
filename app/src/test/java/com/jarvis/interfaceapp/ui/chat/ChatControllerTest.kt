package com.jarvis.interfaceapp.ui.chat

import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import com.stark.jarvislocal.events.*

@OptIn(ExperimentalCoroutinesApi::class)
class ChatControllerTest {
    private class Port : CoreEventUiPort {
        override val connection=MutableStateFlow(PortConnection.CONNECTED)
        val events=MutableSharedFlow<CoreUiUpdate>(extraBufferCapacity=64)
        override val updates=events.asSharedFlow()
        val key=CoreRequestKey(UUID.randomUUID().toString(),UUID.randomUUID().toString())
        var gate: CompletableDeferred<Unit>?=null
        var sends=0;var cancels=0;var failure=false
        override suspend fun submit(text: String): SubmissionResult {
            sends++;gate?.await();if(failure)error("synthetic ACK loss")
            return SubmissionResult.Accepted(key,1)
        }
        override suspend fun cancel(key: CoreRequestKey): Boolean {cancels++;return true}
        fun event(kind: CoreUiKind,seq: Long,key: CoreRequestKey=this.key,generation: Long=1,id: String=UUID.randomUUID().toString(),error: CoreErrorCode?=null) =
            events.tryEmit(CoreUiUpdate.Metadata(CoreMetadata(key,id,kind,generation,seq,errorCode=error)))
    }
    @Test fun absentCoreNeverSendsOrPretendsConnected() = runTest {
        val c=ChatController(UnavailableCoreEventUiPort(),backgroundScope);runCurrent()
        assertFalse(c.send("Question publique"));assertTrue(c.state.value.messages.isEmpty())
        assertEquals(PortConnection.UNAVAILABLE,c.state.value.connection);assertTrue(c.state.value.phase.contains("NON CONNECTÉ"))
    }
    @Test fun sourcePinnedRealCoreBusDrivesCorrelatedLocalUiWithoutInference() = runTest {
        val p=Port();val c=ChatController(p,backgroundScope);runCurrent();c.send("Question synthétique");runCurrent()
        val bus=CoreEventBus({0});bus.setEnabled(true)
        val g=bus.generationIfEnabled()!!
        fun publish(event: CoreEvent) {
            assertTrue(bus.publish(EventEnvelope(p.key.sessionId,p.key.requestId,event),g))
            val o=bus.state.value.events.last();val e=o.envelope
            p.events.tryEmit(CoreUiUpdate.Metadata(CoreMetadata(CoreRequestKey(e.sessionId,e.requestId),e.eventId,
                CoreUiKind.valueOf(e.event.kind),g,o.sequence,e.version,
                (e.event as? CoreEvent.ResponseChunk)?.index,
                (e.event as? CoreEvent.Error)?.code?.let { CoreErrorCode.valueOf(it.name) },o.elapsedMs)))
        }
        publish(CoreEvent.UserInput);publish(CoreEvent.Thinking);runCurrent()
        assertTrue(c.state.value.busy);assertTrue(c.state.value.phase.contains("CORE traite"))
        publish(CoreEvent.ResponseCompleted);runCurrent()
        assertFalse(c.state.value.busy);assertEquals(1,c.state.value.messages.size)
        assertTrue(c.state.value.phase.contains("contenu non raccordé"))
        assertEquals(0,p.cancels)
    }
    @Test fun duplicateStaleGenerationForeignRequestAndLateSuccessAreIgnored() = runTest {
        val p=Port();val c=ChatController(p,backgroundScope);runCurrent();c.send("Public");runCurrent()
        p.event(CoreUiKind.THINKING,1,generation=2)
        p.event(CoreUiKind.RESPONSE_COMPLETED,1,key=CoreRequestKey(UUID.randomUUID().toString(),UUID.randomUUID().toString()));runCurrent()
        assertTrue(c.state.value.busy)
        val id=UUID.randomUUID().toString();p.event(CoreUiKind.THINKING,2,id=id);runCurrent()
        p.event(CoreUiKind.RESPONSE_COMPLETED,3,id=id);runCurrent();assertTrue(c.state.value.busy)
        c.cancel();runCurrent();p.event(CoreUiKind.RESPONSE_COMPLETED,4);runCurrent();assertTrue(c.state.value.busy)
        p.event(CoreUiKind.CANCELLED,5);runCurrent();assertFalse(c.state.value.busy)
        p.event(CoreUiKind.RESPONSE_COMPLETED,6);runCurrent();assertTrue(c.state.value.phase.startsWith("Annulé"))
    }
    @Test fun cancelBeforeAckDoesNotClearBusyUntilActualTerminalMetadata() = runTest {
        val p=Port().apply { gate=CompletableDeferred() };val c=ChatController(p,backgroundScope)
        runCurrent();c.send("Public");runCurrent();c.cancel();p.gate!!.complete(Unit);runCurrent()
        assertEquals(1,p.cancels);assertTrue(c.state.value.busy);assertFalse(c.send("Autre"))
        p.event(CoreUiKind.CANCELLED,1);runCurrent();assertFalse(c.state.value.busy)
    }
    @Test fun earlyMetadataBufferedUntilAckAndDoesNotStartSecondSubmission() = runTest {
        val p=Port().apply { gate=CompletableDeferred() };val c=ChatController(p,backgroundScope)
        runCurrent();assertTrue(c.send("Public"));runCurrent();assertFalse(c.send("Autre"))
        p.event(CoreUiKind.THINKING,1);p.event(CoreUiKind.RESPONSE_COMPLETED,2);runCurrent()
        assertTrue(c.state.value.busy);p.gate!!.complete(Unit);runCurrent()
        assertFalse(c.state.value.busy);assertEquals(1,p.sends)
    }
    @Test fun ackLossAndUnconfirmedResourcesStayBlockedAndNeverClaimNativeRelease() = runTest {
        val p=Port().apply { failure=true };val c=ChatController(p,backgroundScope);runCurrent();c.send("Public");runCurrent()
        assertTrue(c.state.value.busy);assertFalse(c.send("Autre"));assertTrue(c.state.value.phase.contains("ACK perdu"))
        val p2=Port();val c2=ChatController(p2,backgroundScope);runCurrent();c2.send("Public");runCurrent()
        p2.event(CoreUiKind.RESPONSE_COMPLETED,1);runCurrent();assertFalse(c2.state.value.busy)
        p2.event(CoreUiKind.ERROR,2,error=CoreErrorCode.RESOURCE_UNCONFIRMED);runCurrent()
        assertTrue(c2.state.value.busy);assertFalse(c2.send("Autre"));assertTrue(c2.state.value.phase.contains("non confirmées"))
        p2.event(CoreUiKind.ERROR,3,error=CoreErrorCode.CORE_FAILURE);runCurrent()
        assertTrue(c2.state.value.busy);assertFalse(c2.send("Autre"))
    }
    @Test fun demonstrationIsExplicitTransientAndSupportsFollowupAndCancellation() = runTest {
        val c=ChatController(LocalDemonstrationPort(backgroundScope),backgroundScope);runCurrent()
        assertEquals(PortConnection.DEMONSTRATION,c.state.value.connection)
        c.send("Public un");runCurrent();advanceTimeBy(401);runCurrent()
        assertEquals(2,c.state.value.messages.size);assertTrue(c.state.value.messages.all {it.demo})
        assertTrue(c.state.value.messages.last().text.contains("Aucun CORE, LLM"))
        c.send("Question de suivi");runCurrent();c.cancel();runCurrent()
        assertFalse(c.state.value.busy);assertEquals(3,c.state.value.messages.size)
    }
    @Test fun realMetadataPortCannotInjectDemoTextOrDurableMemory() = runTest {
        val p=Port();val c=ChatController(p,backgroundScope);runCurrent();c.send("Public");runCurrent()
        p.events.emit(CoreUiUpdate.DemoText(p.key,"Fausse réponse"));runCurrent()
        assertEquals(1,c.state.value.messages.size)
        assertFalse(c.send("x".repeat(4001)))
        assertThrows(IllegalArgumentException::class.java) { CoreRequestKey("not-a-uuid",p.key.requestId) }
        assertThrows(IllegalArgumentException::class.java) { CoreMetadata(p.key,UUID.randomUUID().toString(),CoreUiKind.ERROR,1,1) }
    }
    @Test fun reconnectDoesNotClearUnknownWorkAndReusedAckIsBlocked() = runTest {
        val p=Port();val c=ChatController(p,backgroundScope);runCurrent();c.send("Public");runCurrent()
        p.connection.value=PortConnection.UNAVAILABLE;runCurrent();p.connection.value=PortConnection.CONNECTED;runCurrent()
        assertTrue(c.state.value.busy);assertFalse(c.send("Autre"))
        p.event(CoreUiKind.CANCELLED,1);runCurrent();assertFalse(c.state.value.busy)
        c.send("Autre");runCurrent();assertTrue(c.state.value.busy);assertTrue(c.state.value.phase.contains("ACK perdu"))
    }
    @Test fun brokenEventFlowFailsClosedWithoutCrashingTheUi() = runTest {
        val p=object: CoreEventUiPort {
            override val connection=MutableStateFlow(PortConnection.CONNECTED)
            override val updates=flow<CoreUiUpdate>{throw IllegalStateException("synthetic transport failure")}
            override suspend fun submit(text: String): SubmissionResult = error("must not send")
            override suspend fun cancel(key: CoreRequestKey)=false
        }
        val c=ChatController(p,backgroundScope);runCurrent()
        assertEquals(PortConnection.UNAVAILABLE,c.state.value.connection);assertFalse(c.send("Public"))
    }

}
