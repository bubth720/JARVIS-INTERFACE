package com.stark.jarvislocal.v1a

import android.app.ActivityManager
import android.app.Application
import android.os.ParcelFileDescriptor
import android.os.StatFs
import com.jarvis.interfaceapp.ui.chat.core.CoreConversationClientAdapter
import fr.jarvis.llm.a02.SealedNativeBridge
import java.io.File
import kotlinx.coroutines.*

/** One CORE session and ONE native engine per process (no second engine, no third-party loader). */
class V1aApplication : Application() {
    private val ownerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val store: ModelStore by lazy {
        val root = File(filesDir, "models")
        ModelStore(root) { StatFs(filesDir.path).availableBytes }
    }
    var session: V1aSession? = null
        private set

    /** Bytes the A02 snapshot may use now: available RAM above the system low-memory threshold. */
    fun memoryBudgetBytes(): Long {
        val info = ActivityManager.MemoryInfo()
        (getSystemService(ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
        return if (info.lowMemory) 0L else (info.availMem - info.threshold).coerceAtLeast(0L)
    }

    fun enable(): V1aSession {
        session?.let { if (!it.closed) return it }
        val entry = checkNotNull(store.current()) { "MODEL_ABSENT" }
        val model = A02LanguageModel(SealedNativeBridge.get(), VerifiedModel(
            sourceFd = {
                val pfd = ParcelFileDescriptor.open(entry.file, ParcelFileDescriptor.MODE_READ_ONLY)
                object : SourceDescriptor { override val fd = pfd.fd; override fun close() = pfd.close() }
            }, sizeBytes = entry.sizeBytes, sha256 = entry.sha256), ::memoryBudgetBytes)
        return V1aSession(V1aCoreTransport(ownerScope, model), model, ownerScope).also { session = it }
    }
}

class V1aSession(val transport: V1aCoreTransport, val model: A02LanguageModel, private val scope: CoroutineScope) {
    val client = CoreConversationClientAdapter(transport, scope)
    var closed: Boolean = false
        private set
    fun close() {
        if (closed) return
        closed = true
        client.close()
        // Native unload only after CORE has drained (close() never interrupts NonCancellable work).
        scope.launch {
            while (transport.coordinator.state.value.busy) delay(50)
            transport.releaseEngine()
        }
    }
}
