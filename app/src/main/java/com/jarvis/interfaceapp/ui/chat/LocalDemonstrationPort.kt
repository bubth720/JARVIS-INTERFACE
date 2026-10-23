package com.jarvis.interfaceapp.ui.chat

import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Explicit software fixture, never a CORE connection, model or speech engine. No user text in the reply. */
class LocalDemonstrationPort(private val scope: CoroutineScope) : CoreEventUiPort {
    override val connection=MutableStateFlow(PortConnection.DEMONSTRATION).asStateFlow()
    private val mutable=MutableSharedFlow<CoreUiUpdate>(extraBufferCapacity=32)
    override val updates=mutable.asSharedFlow()
    private val session=UUID.randomUUID().toString()
    private var current: CoreRequestKey?=null
    private var work: Job?=null
    private var sequence=0L
    private suspend fun emit(key: CoreRequestKey,kind: CoreUiKind) {
        mutable.emit(CoreUiUpdate.Metadata(CoreMetadata(key,UUID.randomUUID().toString(),kind,1,++sequence)))
    }
    override suspend fun submit(text: String): SubmissionResult {
        if(current!=null) return SubmissionResult.Refused("Démonstration déjà active")
        val key=CoreRequestKey(session,UUID.randomUUID().toString());current=key
        work=scope.launch {
            emit(key,CoreUiKind.USER_INPUT);emit(key,CoreUiKind.THINKING);delay(400)
            mutable.emit(CoreUiUpdate.DemoText(key,"Démonstration locale : saisie et événements affichés. Aucun CORE, LLM ou outil exécuté."))
            emit(key,CoreUiKind.RESPONSE_COMPLETED);current=null
        }
        return SubmissionResult.Accepted(key,1)
    }
    override suspend fun cancel(key: CoreRequestKey): Boolean {
        if(key!=current)return false
        work?.cancel();emit(key,CoreUiKind.CANCEL_REQUESTED);emit(key,CoreUiKind.CANCELLED);current=null;return true
    }
    override fun close() { work?.cancel();current=null }
}
