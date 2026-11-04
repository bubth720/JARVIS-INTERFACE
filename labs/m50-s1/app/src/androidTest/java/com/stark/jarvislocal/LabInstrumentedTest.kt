package com.stark.jarvislocal

import android.content.pm.PackageManager
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jarvis.interfaceapp.ui.chat.core.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LabInstrumentedTest {
    private fun <T> main(block: () -> T): T = runBlocking { withContext(Dispatchers.Main) { block() } }
    private fun eventually(check: () -> Boolean) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 7_000
        while (!main(check)) {
            if (android.os.SystemClock.elapsedRealtime() > deadline) fail("Laboratory condition timeout")
            Thread.sleep(20)
        }
    }
    private fun fixture(scenario: LabCoreTransport.Scenario = LabCoreTransport.Scenario.NORMAL, delayMs: Long = 250,
                        action: (LabCoreTransport, CoreConversationClientAdapter) -> Unit) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val core = main { LabCoreTransport(scope, scenario = scenario, chunkDelayMs = delayMs) }
        val client = main { CoreConversationClientAdapter(core, scope) }
        try { eventually { client.state.value.link == CoreTextLink.READY }; action(core, client) }
        finally { main { client.close() }; eventually { !core.coordinator.state.value.busy }; scope.cancel() }
    }

    @Test fun manifestIsIsolatedAndHasNoPermissions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        assertEquals("fr.jarvis.lab.m50s1", context.packageName)
        assertTrue(info.requestedPermissions.isNullOrEmpty())
        assertTrue(info.applicationInfo!!.flags and android.content.pm.ApplicationInfo.FLAG_TEST_ONLY != 0)
        assertEquals(29, info.applicationInfo!!.minSdkVersion)
        assertFalse(info.applicationInfo!!.flags and android.content.pm.ApplicationInfo.FLAG_ALLOW_BACKUP != 0)
    }

    @Test fun actualUiSubmitsToSingleCoreAndRotationKeepsItsInstance() {
        ActivityScenario.launch(LabActivity::class.java).use { activity ->
            lateinit var owner: LabApplication
            activity.onActivity {
                owner = it.application as LabApplication
                assertNull(owner.session)
                it.window.decorView.findViewWithTag<Button>("activate-lab").performClick()
            }
            eventually { owner.session?.client?.state?.value?.link == CoreTextLink.READY }
            val identity = main { owner.session!!.transport.instanceId }
            activity.onActivity { it.question.setText("fixture"); it.submit.performClick() }
            eventually { owner.session!!.transport.acknowledgements == 1 }
            activity.recreate()
            activity.onActivity { assertEquals(identity, (it.application as LabApplication).session!!.transport.instanceId) }
            eventually { !owner.session!!.client.state.value.busy }
            activity.onActivity {
                assertTrue(it.response.text.startsWith("LAB/SYNTHETIC"))
                assertEquals(1, owner.session!!.transport.acknowledgements)
                assertTrue(it.status.text.contains("CORE réel"))
                it.question.setText("stop-fixture")
                it.submit.performClick()
            }
            lateinit var screen: LabActivity
            activity.onActivity { screen = it }
            eventually { screen.stop.isEnabled }
            activity.onActivity { it.stop.performClick() }
            eventually { !owner.session!!.client.state.value.busy }
            activity.onActivity {
                assertEquals(2, owner.session!!.transport.acknowledgements)
                assertTrue(owner.session!!.client.state.value.phase.contains("Annulation"))
                it.window.decorView.findViewWithTag<Button>("close-lab").performClick()
                assertTrue(owner.session!!.closed)
            }
            activity.onActivity {
                assertFalse(it.submit.isEnabled)
                assertEquals("", it.response.text.toString())
            }
        }
    }

    @Test fun stopWaitsForRealCoreDrainAndRejectsStaleKey() = fixture(delayMs = 1_000) { core, client ->
        main { assertTrue(client.submit("fixture")) }
        eventually { client.state.value.key != null }
        val key = main { client.state.value.key!! }
        runBlocking { withContext(Dispatchers.Main) { assertFalse(core.stop(key.copy(requestId = UUID.randomUUID().toString()))) } }
        main { client.cancel(); assertTrue(core.coordinator.state.value.busy); assertFalse(client.submit("overlap")) }
        eventually { !client.state.value.busy }
        assertTrue(main { client.state.value.phase.contains("Annulation") })
        assertFalse(main { core.coordinator.state.value.busy })
    }

    @Test fun ownerErrorDoesNotInventAResponse() = fixture(LabCoreTransport.Scenario.OWNER_ERROR) { core, client ->
        main { client.submit("fixture") }
        eventually { core.coordinator.state.value.phase == SessionPhase.ERROR && !client.state.value.busy }
        assertEquals("", main { client.state.value.text })
        assertTrue(main { client.state.value.phase.contains("OWNER_FAILURE") })
    }

    @Test fun unknownAfterCoreCompletionBlocksAllNewSubmissions() = fixture(LabCoreTransport.Scenario.RESOURCE_UNKNOWN) { core, client ->
        main { client.submit("fixture") }
        eventually { client.state.value.link == CoreTextLink.UNKNOWN }
        assertFalse(main { client.submit("cannot retry") })
        assertEquals(CoreTextLink.UNKNOWN, main { core.connection.value })
        eventually { !core.coordinator.state.value.busy }
        assertEquals(CoreTextLink.UNKNOWN, main { client.state.value.link })
    }

    @Test fun duplicateEventsDoNotDuplicateTheDisplayedResponse() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val core = main { LabCoreTransport(scope) }
        val relay = object : CoreConversationTransport by core {
            override val updates = core.updates.transform { emit(it); emit(it) }
        }
        val client = main { CoreConversationClientAdapter(relay, scope) }
        try {
            eventually { client.state.value.link == CoreTextLink.READY }
            main { client.submit("fixture") }
            eventually { client.state.value.key != null && !client.state.value.busy }
            assertEquals(2, main { Regex("LAB/SYNTHETIC").findAll(client.state.value.text).count() })
        } finally { main { client.close() }; eventually { !core.coordinator.state.value.busy }; scope.cancel() }
    }
}
