package com.jarvis.interfaceapp.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {
    private var controller=ChatController(UnavailableCoreEventUiPort(),viewModelScope)
    private val mutable=MutableStateFlow(controller.state.value)
    val state=mutable.asStateFlow()
    private var observation=viewModelScope.launch { controller.state.collect { mutable.value=it } }
    fun send(text: String)=controller.send(text)
    fun cancel()=controller.cancel()
    /** Explicit user switch; no preference or message is persisted. Busy/unknown sessions cannot be reset here. */
    fun demonstrate(enabled: Boolean) {
        if(state.value.busy)return
        observation.cancel();controller.close()
        controller=ChatController(if(enabled) LocalDemonstrationPort(viewModelScope) else UnavailableCoreEventUiPort(),viewModelScope)
        mutable.value=controller.state.value
        observation=viewModelScope.launch { controller.state.collect { mutable.value=it } }
    }
    override fun onCleared() { controller.close();super.onCleared() }
}
