package com.jarvis.interfaceapp.cockpitv2

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** RAM only. No repository, SavedStateHandle, disk, service or conversation history. */
data class CockpitState(
    val open: Boolean = false,
    val wheelOpen: Boolean = false,
    val knowledgeOpen: Boolean = false,
    val draft: String = "",
)

class CockpitSession {
    private val mutableState = MutableStateFlow(CockpitState())
    val state = mutableState.asStateFlow()
    fun open() { mutableState.value = CockpitState(open = true) }
    fun close() { mutableState.value = CockpitState() }
    fun editDraft(value: String) { if (state.value.open) mutableState.value = state.value.copy(draft = value.take(2_000)) }
    fun toggleWheel() { if (state.value.open) mutableState.value = state.value.copy(wheelOpen = !state.value.wheelOpen) }
    fun dismissWheel() { mutableState.value = state.value.copy(wheelOpen = false) }
    fun showKnowledge() { if (state.value.open) mutableState.value = state.value.copy(knowledgeOpen = true, wheelOpen = false) }
    fun hideKnowledge() { mutableState.value = state.value.copy(knowledgeOpen = false) }
    fun back() {
        when {
            state.value.wheelOpen -> dismissWheel()
            state.value.knowledgeOpen -> hideKnowledge()
            else -> close()
        }
    }
}
