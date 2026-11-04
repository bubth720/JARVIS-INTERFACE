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
        return LabSession(LabCoreTransport(ownerScope, scenario = scenario, chunkDelayMs = 500), ownerScope).also { session = it }
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
