package com.jarvis.interfaceapp.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

class ChatViewModel : ViewModel() {
    private var controller=ChatController(UnavailableCoreEventUiPort(),viewModelScope)
    private val mutable=MutableStateFlow(controller.state.value)
    val state=mutable.asStateFlow()
    private var observation=viewModelScope.launch { controller.state.collect { mutable.value=it } }
    fun send(text: String)=controller.send(text)
    fun cancel()=controller.cancel()
    fun clearMessages()=controller.clearMessages()
    private var archiveOwner:com.jarvis.interfaceapp.workspace.WorkspaceController?=null
    private var archiver:Job?=null
    /** Collection survives Activity STOP. Only the Application writer owns durable ACKs. */
    fun attachArchive(workspace:com.jarvis.interfaceapp.workspace.WorkspaceController) {
        if(archiveOwner===workspace)return
        archiveOwner=workspace;archiver?.cancel()
        archiver=viewModelScope.launch {
            combine(state,workspace.state.map {it.value.keepHistory}.distinctUntilChanged()) {session,enabled->
                if(enabled && !session.busy) session.messages else emptyList()
            }.distinctUntilChanged().collect {messages->
                if(messages.isNotEmpty())workspace.archive(messages.map {com.jarvis.interfaceapp.workspace.ArchivedMessage(it.id,it.text,it.user,it.demo,it.sources)})
            }
        }
    }
    /** Explicit user switch; no preference or message is persisted. Busy/unknown sessions cannot be reset here. */
    fun demonstrate(enabled: Boolean) {
        if(state.value.busy)return
        observation.cancel();controller.close()
        controller=ChatController(if(enabled) LocalDemonstrationPort(viewModelScope) else UnavailableCoreEventUiPort(),viewModelScope)
        mutable.value=controller.state.value
        observation=viewModelScope.launch { controller.state.collect { mutable.value=it } }
    }
    override fun onCleared() { archiver?.cancel();controller.close();super.onCleared() }
}
