package com.jarvis.interfaceapp.ui

import com.jarvis.interfaceapp.domain.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SavePhase { LOADING, DEFAULTS, SAVING, SAVED, ERROR, PROTECTED }
enum class StorageFailure { READ, WRITE, RESET }
data class StorageState(
    val phase: SavePhase = SavePhase.LOADING,
    val canEdit: Boolean = false,
    val durable: Boolean = false,
    val readIssue: LayoutReadIssue? = null,
    val failure: StorageFailure? = null,
)
data class DashboardUiState(val home: HomeState = HomeState(), val storage: StorageState = StorageState())

/** Application-owned actor: ordered commands and writes survive Activity/ViewModel disposal.
 * Suspension never blocks the UI thread. Pending writes cannot survive process death;
 * only a completed save is acknowledged as durable.
 */
class DashboardController(private val repository: LayoutRepository, scope: CoroutineScope) {
    private sealed interface Command {
        data class Action(val action: HomeAction) : Command
        data object Session : Command
        data object Reset : Command
        data object Retry : Command
        data class Barrier(val completed: CompletableDeferred<Unit>) : Command
    }
    private val commands = Channel<Command>(Channel.UNLIMITED)
    private val mutableState = MutableStateFlow(DashboardUiState())
    val state = mutableState.asStateFlow()

    init {
        scope.launch {
            load()
            for (command in commands) when (command) {
                is Command.Action -> applyAction(command.action)
                Command.Session -> mutableState.value = mutableState.value.let {
                    it.copy(home = it.home.copy(settingsOpen = false, editing = false, placementError = null))
                }
                Command.Reset -> resetNow()
                is Command.Barrier -> command.completed.complete(Unit)
                Command.Retry -> if (mutableState.value.storage.failure == StorageFailure.READ) load()
                    else if (mutableState.value.storage.canEdit) save()
            }
        }
    }
    fun dispatch(action: HomeAction) { commands.trySend(Command.Action(action)).getOrThrow() }
    fun startSession() { commands.trySend(Command.Session).getOrThrow() }
    fun reset() { commands.trySend(Command.Reset).getOrThrow() }
    fun retry() { commands.trySend(Command.Retry).getOrThrow() }

    /** Wait for commands queued before this call; inspect storage state for success/failure. */
    suspend fun awaitIdle() {
        val completed = CompletableDeferred<Unit>()
        commands.send(Command.Barrier(completed))
        completed.await()
    }

    private suspend fun load() {
        mutableState.value = mutableState.value.copy(storage = StorageState())
        try {
            val loaded = repository.load()
            mutableState.value = DashboardUiState(
                home = HomeState(layouts = loaded.layouts),
                storage = StorageState(
                    phase = if (loaded.issue != null) SavePhase.PROTECTED else if (loaded.exists) SavePhase.SAVED else SavePhase.DEFAULTS,
                    canEdit = loaded.issue == null, durable = loaded.exists && loaded.issue == null, readIssue = loaded.issue,
                ),
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            mutableState.value = mutableState.value.copy(storage = StorageState(SavePhase.ERROR, failure = StorageFailure.READ))
        }
    }
    private suspend fun applyAction(action: HomeAction) {
        val before = mutableState.value
        if (!before.storage.canEdit && action !is HomeAction.OpenSettings && action !is HomeAction.CloseSettings &&
            action !is HomeAction.ExitEditing) return
        val next = reduceHome(before.home, action)
        // Publish geometry and its non-durable status atomically: no observer may see
        // new geometry paired with the previous snapshot's successful-save marker.
        if (next.layouts != before.home.layouts) {
            mutableState.value = before.copy(home = next, storage = StorageState(SavePhase.SAVING, canEdit = true))
            save()
        } else mutableState.value = before.copy(home = next)
        // Invalid placements, no-ops, navigation and mode changes never trigger disk writes.
    }
    private suspend fun save() {
        val before = mutableState.value
        mutableState.value = before.copy(storage = StorageState(SavePhase.SAVING, canEdit = true))
        try {
            repository.save(before.home.layouts)
            mutableState.value = mutableState.value.copy(storage = StorageState(SavePhase.SAVED, canEdit = true, durable = true))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            mutableState.value = mutableState.value.copy(storage = StorageState(SavePhase.ERROR, canEdit = true, failure = StorageFailure.WRITE))
        }
    }
    private suspend fun resetNow() {
        val before = mutableState.value
        mutableState.value = before.copy(storage = before.storage.copy(phase = SavePhase.SAVING))
        try {
            val layouts = repository.reset()
            mutableState.value = mutableState.value.copy(
                home = before.home.copy(layouts = layouts, editing = false, placementError = null),
                storage = StorageState(SavePhase.SAVED, canEdit = true, durable = true),
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            mutableState.value = before.copy(storage = before.storage.copy(phase = SavePhase.ERROR, failure = StorageFailure.RESET))
        }
    }
}
