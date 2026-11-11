package com.stark.jarvislocal.v1a

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.stark.jarvislocal.SessionPhase
import fr.jarvis.llm.a02.SealedNativeBridge
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Emulator proof in the APP sandbox (untrusted_app, seccomp, SELinux) WITHOUT any model:
 * the A02 memfd snapshot, seals and identity checks must succeed so that the protected engine
 * is the one refusing a non-GGUF payload. Real-model generation stays NOT_RUN until the
 * Android GGUF provenance is approved.
 */
@RunWith(AndroidJUnit4::class)
class V1aInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun fds() = File("/proc/self/fd").list()!!.size
    private fun refusal(block: () -> Unit) = try { block(); "ADMITTED" } catch (e: IllegalStateException) { e.message.orEmpty() }
    private fun junk(): Pair<File, ByteArray> {
        val bytes = ByteArray(256 * 1024) { (it * 31).toByte() }
        val f = File(context.filesDir, "a02-junk.bin"); f.writeBytes(bytes); return f to bytes
    }

    @Test fun manifestHasNoPermissionAndShipsOnlyTheA02Engine() {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        assertEquals("fr.jarvis.v1a.lab", context.packageName)
        assertTrue(info.requestedPermissions.isNullOrEmpty())
        assertEquals(29, info.applicationInfo!!.minSdkVersion)
        assertEquals(0, info.applicationInfo!!.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
        assertNotEquals(0, info.applicationInfo!!.flags and ApplicationInfo.FLAG_TEST_ONLY)
        val libs = File(info.applicationInfo!!.nativeLibraryDir).list().orEmpty().toList()
        assertFalse(libs.any { it.contains("llama-android") || it == "libjarvis_llm.so" })
    }

    @Test fun a02SnapshotAndSealsWorkInAppSandboxThenProtectedLoaderRefusesNonGguf() {
        val (file, bytes) = junk()
        val native = SealedNativeBridge.get()
        val h = native.create()
        native.begin(h)
        val before = fds()
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            val ok = refusal { native.loadSealedFd(h, pfd.fd, bytes.size.toLong(), sha(bytes).toByteArray(), 1L shl 31, 512, 1, true) }
            // Not an A02_* code: snapshot, seals, re-hash and /proc identity passed inside the app; the protected
            // validator (served from the sealed fd, no procfs re-open) then refuses the non-GGUF payload.
            assertFalse("A02 refused in app context: $ok", ok.startsWith("A02_") || ok == "ADMITTED")
            // The protected validator must have READ the sealed bytes (not failed to open them).
            assertTrue("payload not read through the sealed fd: $ok", ok.contains("invalid magic"))
            assertEquals("A02_DIGEST_REFUSED", refusal { native.loadSealedFd(h, pfd.fd, bytes.size.toLong(), ("0".repeat(64)).toByteArray(), 1L shl 31, 512, 1, true) })
            assertEquals("A02_SIZE_REFUSED", refusal { native.loadSealedFd(h, pfd.fd, bytes.size + 1L, sha(bytes).toByteArray(), 1L shl 31, 512, 1, true) })
            assertEquals("A02_MEMORY_BUDGET_REFUSED", refusal { native.loadSealedFd(h, pfd.fd, bytes.size.toLong(), sha(bytes).toByteArray(), bytes.size.toLong(), 512, 1, true) })
            repeat(20) { refusal { native.loadSealedFd(h, pfd.fd, bytes.size.toLong(), sha(bytes).toByteArray(), 1L shl 31, 512, 1, true) } }
        }
        assertEquals(before, fds())
        native.destroy(h)
        file.delete()
    }

    @Test fun activationWithoutModelIsRefusedVisibly() {
        File(context.filesDir, "models").deleteRecursively()
        ActivityScenario.launch(V1aActivity::class.java).use { scenario ->
            scenario.onActivity { it.enable.performClick(); assertTrue(it.error.text.contains("Aucun modèle")) }
        }
    }

    @Test fun coreSessionOverRealEngineReportsA02RefusalWithoutAnyReply() {
        val (file, bytes) = junk()
        val app = context.applicationContext as V1aApplication
        File(context.filesDir, "models").deleteRecursively()
        file.inputStream().use { app.store.import(it, sha(bytes)) }
        val session = runBlocking(Dispatchers.Main) { app.enable() }
        val deadline = System.currentTimeMillis() + 20_000
        while (System.currentTimeMillis() < deadline &&
            runBlocking(Dispatchers.Main) { session.transport.coordinator.state.value.phase } != SessionPhase.ERROR) Thread.sleep(50)
        runBlocking(Dispatchers.Main) {
            assertEquals(SessionPhase.ERROR, session.transport.coordinator.state.value.phase)
            assertFalse(session.transport.lastError.value.orEmpty().startsWith("A02_"))
            assertTrue(session.transport.history.snapshot().isEmpty())
            assertFalse(session.client.submit("Bonjour"))
            session.close()
        }
        File(context.filesDir, "models").deleteRecursively(); file.delete()
    }
}
