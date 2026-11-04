package com.stark.jarvislocal

import com.jarvis.interfaceapp.ui.chat.core.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LabCoreTransportTest {
    private fun TestScope.transport(scenario: LabCoreTransport.Scenario = LabCoreTransport.Scenario.NORMAL, delay: Long = 120) =
        LabCoreTransport(backgroundScope, StandardTestDispatcher(testScheduler), scenario, delay)

    @Test fun realCoordinatorAckAndFixedSyntheticContent() = runTest {
        val core = transport()
        val client = CoreConversationClientAdapter(core, backgroundScope)
        runCurrent()
        assertTrue(core.coordinator.state.value.prepared)
        assertTrue(client.submit("private question must never be echoed"))
        runCurrent()
        assertNotNull(client.state.value.key)
        assertEquals(1, core.acknowledgements)
        assertTrue(core.coordinator.state.value.busy)
        assertFalse(client.submit("second request"))
        advanceTimeBy(300); runCurrent()
        assertFalse(client.state.value.busy)
        assertFalse(core.coordinator.state.value.busy)
        assertTrue(client.state.value.text.startsWith("LAB/SYNTHETIC"))
        assertFalse(client.state.value.text.contains("private question"))
        client.close()
    }

    @Test fun stopKeepsTheGateUntilActualSyntheticWorkerDrain() = runTest {
        val core = transport(delay = 1_000)
        val client = CoreConversationClientAdapter(core, backgroundScope)
        runCurrent(); assertTrue(client.submit("stop fixture")); runCurrent()
        client.cancel(); runCurrent()
        assertTrue(core.coordinator.state.value.busy)
        assertTrue(client.state.value.busy)
        assertFalse(client.submit("cannot overlap"))
        advanceTimeBy(1_100); runCurrent()
        assertFalse(core.coordinator.state.value.busy)
        assertFalse(client.state.value.busy)
        assertEquals("", client.state.value.text)
        assertTrue(client.state.value.phase.contains("Annulation"))
        client.close()
    }

    @Test fun staleStopCannotCancelTheCurrentCoreRequest() = runTest {
        val core = transport(delay = 1_000)
        val client = CoreConversationClientAdapter(core, backgroundScope)
        runCurrent(); client.submit("fixture"); runCurrent()
        val key = requireNotNull(client.state.value.key)
        assertFalse(core.stop(key.copy(generation = key.generation + 1)))
        assertFalse(core.stop(key.copy(requestId = UUID.randomUUID().toString())))
        assertTrue(core.coordinator.state.value.busy)
        client.cancel(); runCurrent(); advanceTimeBy(1_100); runCurrent(); client.close()
    }

    @Test fun ownerErrorIsARealCoordinatorErrorNotAResponse() = runTest {
        val core = transport(LabCoreTransport.Scenario.OWNER_ERROR)
        val client = CoreConversationClientAdapter(core, backgroundScope)
        runCurrent(); client.submit("fixture"); runCurrent()
        assertEquals(SessionPhase.ERROR, core.coordinator.state.value.phase)
        assertEquals("", client.state.value.text)
        assertFalse(client.state.value.busy)
        assertTrue(client.state.value.phase.contains("OWNER_FAILURE"))
        client.close()
    }

    @Test fun resourceUnknownAfterCompletedMetadataNeverBecomesSuccessfulContent() = runTest {
        val core = transport(LabCoreTransport.Scenario.RESOURCE_UNKNOWN)
        val delivered = mutableListOf<CoreMessageEnvelope>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { core.updates.collect { delivered.add(it) } }
        val client = CoreConversationClientAdapter(core, backgroundScope)
        runCurrent(); client.submit("fixture"); runCurrent(); advanceTimeBy(300); runCurrent()
        assertFalse(core.coordinator.state.value.busy)
        assertEquals(CoreTextLink.UNKNOWN, core.connection.value)
        assertEquals(CoreTextLink.UNKNOWN, client.state.value.link)
        assertTrue(delivered.none { it.body == ContentBody.Completed })
        assertFalse(client.submit("blocked forever in this process"))
        assertNull(core.submit(SubmitUserText(UUID.randomUUID().toString(), "blocked")))
        client.close()
    }

    @Test fun replayedSubmissionIdCannotStartASecondCoreTurn() = runTest {
        val core = transport()
        runCurrent()
        val submission = SubmitUserText(UUID.randomUUID().toString(), "fixture")
        val ack = async { core.submit(submission) }
        runCurrent(); assertNotNull(ack.await())
        advanceTimeBy(300); runCurrent()
        assertNull(core.submit(submission))
        assertEquals(1, core.acknowledgements)
        core.close()
    }

    private class Relay(val base: LabCoreTransport, val mode: Int) : CoreConversationTransport by base {
        override val updates: Flow<CoreMessageEnvelope> = base.updates.transform { event ->
            emit(event)
            if (event.sequence == 1L) emit(if (mode == 1) event else event.copy(sequence = 2))
        }
    }

    @Test fun identicalEventReplayIsDeduplicated() = runTest {
        val core = transport(); val client = CoreConversationClientAdapter(Relay(core, 1), backgroundScope)
        runCurrent(); client.submit("fixture"); runCurrent(); advanceTimeBy(300); runCurrent()
        assertFalse(client.state.value.busy)
        assertEquals(2, Regex("LAB/SYNTHETIC").findAll(client.state.value.text).count())
        client.close()
    }

    @Test fun reusedEventIdAtAnotherSequencePoisonsTheClient() = runTest {
        val core = transport(); val client = CoreConversationClientAdapter(Relay(core, 2), backgroundScope)
        runCurrent(); client.submit("fixture"); runCurrent(); advanceTimeBy(300); runCurrent()
        assertEquals(CoreTextLink.UNKNOWN, client.state.value.link)
        assertFalse(client.submit("no retry")); client.close()
    }

    @Test fun lostAckTimesOutWithoutStartingCoreOrRetrying() = runTest {
        val core = transport()
        val relay = object : CoreConversationTransport by core {
            override suspend fun submit(value: SubmitUserText): ContentAck? { delay(6_000); return core.submit(value) }
        }
        val client = CoreConversationClientAdapter(relay, backgroundScope)
        runCurrent(); client.submit("fixture"); runCurrent(); advanceTimeBy(5_100); runCurrent()
        assertEquals(CoreTextLink.UNKNOWN, client.state.value.link)
        assertEquals(0, core.acknowledgements)
        assertFalse(client.submit("no retry")); client.close()
    }

    @Test fun closeClearsRamAndLateWorkerCannotReopenThePort() = runTest {
        val core = transport(delay = 1_000)
        val client = CoreConversationClientAdapter(core, backgroundScope)
        runCurrent(); client.submit("fixture"); runCurrent(); client.close()
        advanceTimeBy(2_100); runCurrent()
        assertEquals(CoreTextLink.DISABLED, client.state.value.link)
        assertEquals(CoreTextLink.DISABLED, core.connection.value)
        assertEquals("", client.state.value.text)
        assertFalse(core.coordinator.state.value.busy)
        assertFalse(client.submit("no retry"))
    }

    @Test fun malformedTextCannotReachTheCoordinator() = runTest {
        val core = transport(); val client = CoreConversationClientAdapter(core, backgroundScope)
        runCurrent()
        assertFalse(client.submit("\uD800"))
        assertFalse(client.submit("a".repeat(16 * 1024 + 1)))
        assertEquals(0, core.acknowledgements)
        client.close()
    }
}
