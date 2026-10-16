package com.jarvis.interfaceapp.storage

import com.jarvis.interfaceapp.domain.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DataStoreLayoutRepositoryTest {
    @get:Rule val temp = TemporaryFolder()
    private suspend fun <T> withStore(file: File, block: suspend (DataStoreLayoutRepository) -> T): T {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        return try { block(DataStoreLayoutRepository({ file }, scope)) }
        finally { scope.coroutineContext.job.cancelAndJoin() }
    }
    private fun file() = File(temp.newFolder(), "dashboard.json")

    @Test fun firstInstallationDoesNotWriteUntilAModification() = runBlocking {
        val f = file()
        withStore(f) {
            val loaded = it.load()
            assertFalse(loaded.exists); assertNull(loaded.issue)
            assertEquals(defaultLayouts(), loaded.layouts)
            assertFalse(f.exists())
        }
    }
    @Test fun savedMoveSizeLocksAndProfilesSurviveNewDataStoreInstance() = runBlocking {
        val f = file()
        withStore(f) { it.save(configuredLayouts()) }
        assertTrue(f.length() > 0)
        withStore(f) {
            val restored = it.load()
            assertTrue(restored.exists)
            assertEquals(configuredLayouts(), restored.layouts)
        }
    }
    @Test fun corruptionStaysByteIdenticalUntilExplicitResetAndIsArchived() = runBlocking {
        val f = file(); val bytes = "broken-layout".toByteArray(); f.writeBytes(bytes)
        withStore(f) {
            val loaded = it.load()
            assertEquals(LayoutReadIssue.CORRUPT, loaded.issue)
            assertArrayEquals(bytes, f.readBytes())
            try { it.save(configuredLayouts()); fail("Protected document was overwritten") } catch (_: java.io.IOException) { }
            assertArrayEquals(bytes, f.readBytes())
            assertEquals(defaultLayouts(), it.reset())
        }
        val archive = f.parentFile!!.listFiles()!!.single { it.name.startsWith("dashboard.recovery-") }
        assertArrayEquals(bytes, archive.readBytes())
        withStore(f) { assertEquals(defaultLayouts(), it.load().layouts); assertNull(it.load().issue) }
    }
    @Test fun futureVersionRemainsIntactAndCanOnlyBeReplacedByReset() = runBlocking {
        val f = file(); val bytes = "{\"version\":999,\"profiles\":{}}".toByteArray(); f.writeBytes(bytes)
        withStore(f) {
            assertEquals(LayoutReadIssue.INCOMPATIBLE, it.load().issue)
            assertArrayEquals(bytes, f.readBytes())
            it.reset()
        }
        assertTrue(f.parentFile!!.listFiles()!!.any { it.name.startsWith("dashboard.recovery-") && it.readBytes().contentEquals(bytes) })
    }
    @Test fun explicitDefaultResetIsDurableOnFirstInstallation() = runBlocking {
        val f = file()
        withStore(f) { it.load(); it.reset() }
        assertTrue(f.exists())
        withStore(f) { assertTrue(it.load().exists); assertEquals(defaultLayouts(), it.load().layouts) }
    }
    @Test fun resetRestoresBothProfilesAndLocksWithoutTouchingOtherFiles() = runBlocking {
        val f = file(); val other = File(f.parentFile, "unrelated.txt").apply { writeText("keep") }
        withStore(f) { it.save(configuredLayouts()); it.reset() }
        withStore(f) { assertEquals(defaultLayouts(), it.load().layouts) }
        assertEquals("keep", other.readText())
    }
    @Test fun oversizedFileLoadsSafelyWithoutBeingReplaced() = runBlocking {
        val f = file(); f.writeBytes(ByteArray(LayoutCodec.MAX_BYTES * 2) { 32 })
        withStore(f) { assertEquals(LayoutReadIssue.CORRUPT, it.load().issue) }
        assertEquals((LayoutCodec.MAX_BYTES * 2).toLong(), f.length())
    }
}
