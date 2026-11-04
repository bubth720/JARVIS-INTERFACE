package com.stark.jarvislocal

import android.os.SystemClock
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jarvis.interfaceapp.ui.chat.core.CoreTextLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Measures UI-visible synthetic latency, NOT LLM time-to-first-token or real model throughput.
 * Each run waits for an actual new CORE ACK to avoid accepting the previous answer as a sample.
 * Runs only on authorized Android emulators, never a personal phone.
 */
@RunWith(AndroidJUnit4::class)
class LabLatencyInstrumentedTest {
    private fun <T> main(block: () -> T): T =
        runBlocking { withContext(Dispatchers.Main) { block() } }

    private fun eventually(check: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 8_000L
        while (!main(check)) {
            if (SystemClock.elapsedRealtime() >= deadline) fail("Synthetic UI latency condition timed out")
            Thread.sleep(15L)
        }
    }

    @Test fun firstRenderedTextAndFullSyntheticResponseP95BelowThreeSeconds() {
        ActivityScenario.launch(LabActivity::class.java).use { activity ->
            lateinit var owner: LabApplication
            lateinit var screen: LabActivity
            activity.onActivity {
                screen = it
                owner = it.application as LabApplication
                it.window.decorView.findViewWithTag<Button>("activate-lab").performClick()
            }
            eventually { owner.session?.client?.state?.value?.link == CoreTextLink.READY }
            val firstVisibleMs = mutableListOf<Long>()
            val completedMs = mutableListOf<Long>()
            repeat(12) { index ->
                val previousAcks = main { owner.session!!.transport.acknowledgements }
                val startNanos = SystemClock.elapsedRealtimeNanos()
                activity.onActivity {
                    it.question.setText("synthetic-sample")
                    assertTrue(it.submit.isEnabled)
                    it.submit.performClick()
                }
                // The coordinator's monotonic ACK proves that this is the current turn.
                eventually { owner.session!!.transport.acknowledgements == previousAcks + 1 }
                eventually { screen.response.text.toString().startsWith("LAB/SYNTHETIC") }
                firstVisibleMs.add((SystemClock.elapsedRealtimeNanos() - startNanos) / 1_000_000L)
                eventually { !owner.session!!.client.state.value.busy && screen.submit.isEnabled }
                completedMs.add((SystemClock.elapsedRealtimeNanos() - startNanos) / 1_000_000L)
                assertEquals(previousAcks + 1, main { owner.session!!.transport.acknowledgements })
                assertTrue(main { screen.response.text.contains("aucun moteur LLM") })
            }
            // Nearest-rank percentile for 12 independent turns, conservatively index 11.
            val p95Visible = firstVisibleMs.sorted()[11]
            val p95Completed = completedMs.sorted()[11]
            println("M50_SYNTHETIC_LATENCY api=${android.os.Build.VERSION.SDK_INT} " +
                "samples=12 first_visible_p95_ms=$p95Visible completed_p95_ms=$p95Completed")
            assertTrue("Synthetic first-visible p95 exceeded 3 s: $p95Visible ms", p95Visible < 3_000L)
            assertTrue("Synthetic complete p95 exceeded 3 s: $p95Completed ms", p95Completed < 3_000L)
            main { owner.session!!.close() }
        }
    }
}
