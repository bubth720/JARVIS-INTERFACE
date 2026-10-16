package com.jarvis.interfaceapp.ui

import com.jarvis.interfaceapp.domain.*
import com.jarvis.interfaceapp.storage.configuredLayouts
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardControllerTest {
    private class Repository : LayoutRepository {
        var loaded = LoadedLayouts(defaultLayouts(), false)
        val started = mutableListOf<Layouts>()
        var resets = 0
        var failRead = false
        var failWrite = false
        var failReset = false
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun load(): LoadedLayouts {
            if (failRead) throw IOException("Read failed")
            return loaded
        }
        override suspend fun save(layouts: Layouts) {
            started += layouts
            gate?.await()
            if (failWrite) throw IOException("Write failed")
            loaded = LoadedLayouts(layouts, true)
        }
        override suspend fun reset(): Layouts {
            resets++
            gate?.await()
            if (failReset) throw IOException("Reset failed")
            loaded = LoadedLayouts(defaultLayouts(), true)
            return loaded.layouts
        }
    }
    private val profile = DashboardProfile.COMPACT
    private fun place(y: Int = 1, height: Int = 4) = HomeAction.Place(profile, PanelId.AVATAR, 0, y, 3, height)

    @Test fun firstInstallationUsesDefaultsWithoutClaimingDurability() = runTest {
        val repo = Repository(); val c = DashboardController(repo, backgroundScope)
        assertEquals(SavePhase.LOADING, c.state.value.storage.phase)
        runCurrent()
        assertEquals(defaultLayouts(), c.state.value.home.layouts)
        assertEquals(SavePhase.DEFAULTS, c.state.value.storage.phase)
        assertFalse(c.state.value.storage.durable)
        assertTrue(repo.started.isEmpty())
    }
    @Test fun startupRestoresLayoutsButNeverTemporaryUiState() = runTest {
        val repo = Repository().apply { loaded = LoadedLayouts(configuredLayouts(), true) }
        val c = DashboardController(repo, backgroundScope); runCurrent()
        assertEquals(configuredLayouts(), c.state.value.home.layouts)
        assertFalse(c.state.value.home.editing); assertFalse(c.state.value.home.settingsOpen)
        assertNull(c.state.value.home.placementError)
        assertTrue(c.state.value.storage.durable)
    }
    @Test fun savingIsNotDurableUntilActualWriteCompletes() = runTest {
        val repo = Repository().apply { gate = CompletableDeferred() }
        val c = DashboardController(repo, backgroundScope); runCurrent()
        c.dispatch(HomeAction.ToggleEditing); c.dispatch(place()); runCurrent()
        assertEquals(SavePhase.SAVING, c.state.value.storage.phase)
        assertFalse(c.state.value.storage.durable)
        assertFalse(repo.loaded.exists)
        repo.gate!!.complete(Unit); runCurrent()
        assertEquals(SavePhase.SAVED, c.state.value.storage.phase)
        assertTrue(c.state.value.storage.durable)
        assertEquals(1, repo.loaded.layouts.getValue(profile).first().y)
    }
    @Test fun rapidCommandsWaitForEachWriteAndPreserveTheirOrder() = runTest {
        val repo = Repository().apply { gate = CompletableDeferred() }
        val c = DashboardController(repo, backgroundScope); runCurrent()
        c.dispatch(HomeAction.ToggleEditing)
        c.dispatch(place()); c.dispatch(place(height = 3)); c.dispatch(HomeAction.ToggleLock(profile, PanelId.AVATAR))
        runCurrent()
        assertEquals(1, repo.started.size)
        repo.gate!!.complete(Unit); runCurrent()
        assertEquals(3, repo.started.size)
        assertEquals(4, repo.started[0].getValue(profile).first().height)
        assertEquals(3, repo.started[1].getValue(profile).first().height)
        assertTrue(repo.started[2].getValue(profile).first().locked)
        assertEquals(repo.started.last(), c.state.value.home.layouts)
    }
    @Test fun rejectedNoOpAndNavigationActionsNeverWrite() = runTest {
        val repo = Repository(); val c = DashboardController(repo, backgroundScope); runCurrent()
        c.dispatch(place()) // normal mode
        c.dispatch(HomeAction.ToggleEditing)
        c.dispatch(HomeAction.Place(profile, PanelId.AVATAR, 3, 0, 3, 4)) // collision
        c.dispatch(HomeAction.Place(profile, PanelId.AVATAR, 0, 0, 3, 4)) // identical
        c.dispatch(HomeAction.OpenSettings); c.dispatch(HomeAction.CloseSettings)
        runCurrent(); assertTrue(repo.started.isEmpty())
    }
    @Test fun lockAndResizeChangesAreSavedIndependentlyFromOtherProfile() = runTest {
        val repo = Repository(); val c = DashboardController(repo, backgroundScope); runCurrent()
        c.dispatch(HomeAction.ToggleEditing); c.dispatch(place(height = 3))
        c.dispatch(HomeAction.ToggleLock(profile, PanelId.AVATAR)); runCurrent()
        assertEquals(2, repo.started.size)
        assertEquals(initialPanels(DashboardProfile.WIDE), repo.loaded.layouts.getValue(DashboardProfile.WIDE))
        assertTrue(repo.loaded.layouts.getValue(profile).first().locked)
        assertEquals(3, repo.loaded.layouts.getValue(profile).first().height)
    }
    @Test fun writeFailureRemainsExplicitAndRetrySavesLatestState() = runTest {
        val repo = Repository().apply { failWrite = true }
        val c = DashboardController(repo, backgroundScope); runCurrent()
        c.dispatch(HomeAction.ToggleEditing); c.dispatch(place()); runCurrent()
        assertEquals(StorageFailure.WRITE, c.state.value.storage.failure)
        assertFalse(c.state.value.storage.durable)
        assertEquals(1, c.state.value.home.layouts.getValue(profile).first().y)
        repo.failWrite = false; c.retry(); runCurrent()
        assertTrue(c.state.value.storage.durable)
        assertEquals(c.state.value.home.layouts, repo.loaded.layouts)
    }
    @Test fun aLaterAcceptedChangeSavesEarlierUnsavedModifications() = runTest {
        val repo = Repository().apply { failWrite = true }
        val c = DashboardController(repo, backgroundScope); runCurrent()
        c.dispatch(HomeAction.ToggleEditing); c.dispatch(place()); runCurrent()
        repo.failWrite = false; c.dispatch(place(height = 3)); runCurrent()
        assertEquals(1, repo.loaded.layouts.getValue(profile).first().y)
        assertEquals(3, repo.loaded.layouts.getValue(profile).first().height)
        assertTrue(c.state.value.storage.durable)
    }
    @Test fun corruptAndIncompatibleDataAreProtectedUntilExplicitReset() = runTest {
        for (issue in LayoutReadIssue.entries) {
            val repo = Repository().apply { loaded = LoadedLayouts(defaultLayouts(), true, issue) }
            val c = DashboardController(repo, backgroundScope); runCurrent()
            assertEquals(SavePhase.PROTECTED, c.state.value.storage.phase)
            c.dispatch(HomeAction.ToggleEditing); c.dispatch(place()); runCurrent()
            assertFalse(c.state.value.home.editing); assertTrue(repo.started.isEmpty())
            c.reset(); runCurrent()
            assertTrue(c.state.value.storage.canEdit); assertTrue(c.state.value.storage.durable)
            assertEquals(defaultLayouts(), c.state.value.home.layouts)
        }
    }
    @Test fun readFailureIsSafeAndRetriable() = runTest {
        val repo = Repository().apply { failRead = true }
        val c = DashboardController(repo, backgroundScope); runCurrent()
        assertEquals(StorageFailure.READ, c.state.value.storage.failure)
        assertFalse(c.state.value.storage.canEdit)
        repo.failRead = false; c.retry(); runCurrent()
        assertEquals(SavePhase.DEFAULTS, c.state.value.storage.phase)
        assertTrue(c.state.value.storage.canEdit)
    }
    @Test fun resetIsAcknowledgedOnlyAfterPersistenceAndKeepsNavigation() = runTest {
        val repo = Repository().apply { loaded = LoadedLayouts(configuredLayouts(), true); gate = CompletableDeferred() }
        val c = DashboardController(repo, backgroundScope); runCurrent()
        c.dispatch(HomeAction.OpenSettings); c.reset(); runCurrent()
        assertEquals(configuredLayouts(), c.state.value.home.layouts)
        assertEquals(SavePhase.SAVING, c.state.value.storage.phase)
        repo.gate!!.complete(Unit); runCurrent()
        assertEquals(defaultLayouts(), c.state.value.home.layouts)
        assertTrue(c.state.value.home.settingsOpen)
        assertTrue(c.state.value.storage.durable)
    }
    @Test fun failedResetKeepsPreviousConfigurationAndDoesNotReportSuccess() = runTest {
        val repo = Repository().apply { loaded = LoadedLayouts(configuredLayouts(), true); failReset = true }
        val c = DashboardController(repo, backgroundScope); runCurrent()
        c.reset(); runCurrent()
        assertEquals(StorageFailure.RESET, c.state.value.storage.failure)
        assertEquals(configuredLayouts(), c.state.value.home.layouts)
        assertEquals(configuredLayouts(), repo.loaded.layouts)
    }
    @Test fun newActivitySessionDoesNotCancelAnEnqueuedWrite() = runTest {
        val repo = Repository().apply { gate = CompletableDeferred() }
        val c = DashboardController(repo, backgroundScope); runCurrent()
        c.dispatch(HomeAction.ToggleEditing); c.dispatch(place()); c.startSession(); runCurrent()
        assertEquals(SavePhase.SAVING, c.state.value.storage.phase)
        repo.gate!!.complete(Unit); runCurrent()
        assertFalse(c.state.value.home.editing)
        assertEquals(1, c.state.value.home.layouts.getValue(profile).first().y)
        assertTrue(c.state.value.storage.durable)
    }
    @Test fun observersNeverSeeNewGeometryWithAnOldDurabilityAcknowledgment() = runTest {
        val repo = Repository().apply {
            loaded = LoadedLayouts(defaultLayouts(), true)
            gate = CompletableDeferred()
        }
        val c = DashboardController(repo, backgroundScope); runCurrent()
        val observed = mutableListOf<DashboardUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { c.state.collect { observed += it } }
        c.dispatch(HomeAction.ToggleEditing); c.dispatch(place()); runCurrent()
        assertTrue(observed.any { it.home.layouts.getValue(profile).first().y == 1 })
        assertFalse(observed.any { it.home.layouts.getValue(profile).first().y == 1 && it.storage.durable })
        repo.gate!!.complete(Unit); runCurrent()
        assertTrue(c.state.value.storage.durable)
    }

}
