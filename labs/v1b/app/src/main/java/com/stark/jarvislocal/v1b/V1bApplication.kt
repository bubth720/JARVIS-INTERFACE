package com.stark.jarvislocal.v1b

import android.app.ActivityManager
import android.app.Application
import android.os.ParcelFileDescriptor
import android.os.StatFs
import com.jarvis.interfaceapp.ui.chat.core.CoreConversationClientAdapter
import com.stark.jarvislocal.UnloadResult
import fr.jarvis.llm.a02.SealedNativeBridge
import java.io.File
import kotlinx.coroutines.*

/** One CORE session and ONE native engine per process (no second engine, no third-party loader). */
class V1bApplication : Application() {
    private val ownerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val store: ModelStore by lazy {
        val root = File(filesDir, "models")
        ModelStore(root) { StatFs(filesDir.path).availableBytes }
    }
    var session: V1bSession? = null
        private set

    /** Bytes the A02 snapshot may use now: available RAM above the system low-memory threshold. */
    fun memoryBudgetBytes(): Long {
        val info = ActivityManager.MemoryInfo()
        (getSystemService(ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
        return if (info.lowMemory) 0L else (info.availMem - info.threshold).coerceAtLeast(0L)
    }

    /** [outputCeiling]: 128 by default, 256 = experimental option (a02_output_policy.h bounds both). */
    fun enable(outputCeiling: Int = V1bPolicy.DEFAULT_OUTPUT): V1bSession {
        session?.let {
            if (!it.closed) return it
            // Never hold two models: a reload waits until the previous engine is unloaded and destroyed.
            check(it.released) { "Déchargement du modèle précédent en cours : réessaie dans un instant." }
        }
        val entry = checkNotNull(store.current()) { "MODEL_ABSENT" }
        val model = A02LanguageModel(SealedNativeBridge.get(), VerifiedModel(
            sourceFd = {
                val pfd = ParcelFileDescriptor.open(entry.file, ParcelFileDescriptor.MODE_READ_ONLY)
                object : SourceDescriptor { override val fd = pfd.fd; override fun close() = pfd.close() }
            }, sizeBytes = entry.sizeBytes, sha256 = entry.sha256), ::memoryBudgetBytes, outputCeiling)
        return V1bSession(V1bCoreTransport(ownerScope, model), model, ownerScope).also { session = it }
    }
}

class V1bSession(val transport: V1bCoreTransport, val model: A02LanguageModel, private val scope: CoroutineScope) {
    val client = CoreConversationClientAdapter(transport, scope)
    var closed: Boolean = false
        private set
    /** True once the native model is unloaded AND the engine handle destroyed. */
    @Volatile var released: Boolean = false
        private set
    fun close() {
        if (closed) return
        closed = true
        client.close()
        // Native unload only after CORE has drained (close() never interrupts NonCancellable work).
        scope.launch {
            while (transport.coordinator.state.value.busy) delay(50)
            // Unload, then free the native handle and its worker thread: a reload starts from a fresh engine.
            if (transport.releaseEngine() == UnloadResult.CONFIRMED && model.destroy() == UnloadResult.CONFIRMED) released = true
        }
    }
}
