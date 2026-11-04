package com.stark.jarvislocal

import android.app.Application
import com.jarvis.interfaceapp.ui.chat.core.CoreConversationClientAdapter
import java.util.UUID
import kotlinx.coroutines.*

/** One opt-in session per process. Neither rotation nor an Activity can reset UNKNOWN. */
class LabApplication : Application() {
    val epoch: String = UUID.randomUUID().toString()
    private val ownerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    var session: LabSession? = null
        private set
    fun enable(scenario: LabCoreTransport.Scenario = LabCoreTransport.Scenario.NORMAL): LabSession {
        session?.let { return it }
        // The normal lab used to wait 500 ms before each of two synthetic chunks.
        // Keep deliberately slow delivery ONLY for deterministic Stop/drainage tests.
        val syntheticDelayMs = if (scenario == LabCoreTransport.Scenario.SLOW_STOP) 500L else 60L
        return LabSession(LabCoreTransport(ownerScope, scenario = scenario, chunkDelayMs = syntheticDelayMs), ownerScope).also { session = it }
    }
}

class LabSession(val transport: LabCoreTransport, scope: CoroutineScope) {
    val client = CoreConversationClientAdapter(transport, scope)
    var closed: Boolean = false
        private set
    fun close() {
        if (closed) return
        closed = true
        client.close()
    }
}
