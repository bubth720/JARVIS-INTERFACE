package com.jarvis.interfaceapp.ui.chat

import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Ephemeral UI state only; never written to DataStore, logs, disk or saved instance state. */
data class ConversationMessage(val id: String, val text: String, val demo: Boolean, val user: Boolean)
data class SessionUiState(val connection: PortConnection = PortConnection.UNAVAILABLE,
    val key: CoreRequestKey? = null, val generation: Long = -1, val busy: Boolean = false,
    val phase: String = "NON CONNECTÉ", val messages: List<ConversationMessage> = emptyList(),
    val notifications: List<String> = emptyList())

/** Commands and observations are confined to the UI dispatcher; not a CORE scheduler. */
class ChatController(private val port: CoreEventUiPort, private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(SessionUiState(connection=port.connection.value))
    val state = mutable.asStateFlow()
    private var resourceBlocked = false
    private var streamFailed = false
    private var submitting = false
    private var invalidated = false
    private var terminal = false
    private var sequence = 0L
    private val seen = linkedSetOf<String>()
    private val usedRequests = linkedSetOf<Pair<CoreRequestKey,Long>>()
    private val early = ArrayDeque<CoreUiUpdate>()
    private val jobs = listOf(scope.launch { port.connection.collect { health ->
        mutable.update { it.copy(connection=if(streamFailed) PortConnection.UNAVAILABLE else health) }
        if (health !in setOf(PortConnection.CONNECTED,PortConnection.DEMONSTRATION) && mutable.value.busy)
            unknown("Connexion perdue — session bloquée, fin native NON ATTESTÉE")
    } }, scope.launch { port.updates.catch { e ->
        if(e is CancellationException)throw e
        streamFailed=true;mutable.update { it.copy(connection=PortConnection.UNAVAILABLE) }
        if(mutable.value.busy)unknown("Flux CORE perdu — session bloquée") else phase("Flux CORE indisponible — aucun envoi possible")
    }.onCompletion { cause ->
        if(cause==null && port.connection.value==PortConnection.CONNECTED){
            streamFailed=true;mutable.update { it.copy(connection=PortConnection.UNAVAILABLE) }
            if(mutable.value.busy)unknown("Flux CORE fermé — session bloquée")
        }
    }.collect { update ->
        if (submitting && mutable.value.key==null) {
            if (early.size>=32) { early.clear(); invalidated=true }
            else early.addLast(update)
        } else accept(update)
    } })
    fun send(text: String): Boolean {
        val question=text.trim()
        if (mutable.value.busy || submitting || question.isEmpty() || question.length>4000 || '\u0000' in question) return false
        if (mutable.value.connection !in setOf(PortConnection.CONNECTED,PortConnection.DEMONSTRATION)) {
            mutable.update { it.copy(phase="CORE NON CONNECTÉ — aucun envoi ni calcul effectué") }; return false
        }
        submitting=true; terminal=false; invalidated=false; sequence=0; seen.clear(); early.clear()
        mutable.update { it.copy(key=null,generation=-1,busy=true,phase="Envoi en attente d'ACK") }
        scope.launch {
            try {
                when(val result=port.submit(question)) {
                    is SubmissionResult.Refused -> finish("Refus : ${result.reason.take(240)}")
                    is SubmissionResult.Accepted -> {
                        require(result.generation>=0)
                        require((result.key to result.generation) !in usedRequests) { "Reused correlation ACK" }
                        usedRequests.add(result.key to result.generation)
                        while(usedRequests.size>256)usedRequests.remove(usedRequests.first())
                        mutable.update { it.copy(key=result.key,generation=result.generation,
                            messages=(it.messages+ConversationMessage(UUID.randomUUID().toString(),question,it.connection==PortConnection.DEMONSTRATION,true)).takeLast(200),phase="Préparation") }
                        if(invalidated) { phase("Annulation avant ACK — attente de fin effective"); port.cancel(result.key) }
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { unknown("ACK perdu ou invalide — session bloquée, aucun résultat présumé") }
            finally {
                submitting=false
                val buffered=early.toList();early.clear();buffered.forEach(::accept)
            }
        }
        return true
    }
    private fun accept(update: CoreUiUpdate) {
        val current=mutable.value
        if (current.key==null) return
        if (terminal && (update !is CoreUiUpdate.Metadata || update.value.kind!=CoreUiKind.ERROR)) return
        when(update) {
            is CoreUiUpdate.DemoText -> if (update.key==current.key && current.connection==PortConnection.DEMONSTRATION && !invalidated) {
                mutable.update { it.copy(messages=(it.messages+ConversationMessage(UUID.randomUUID().toString(),update.text,true,false)).takeLast(200)) }
            }
            is CoreUiUpdate.Metadata -> {
                val e=update.value
                if(e.key!=current.key || e.generation!=current.generation || e.eventId in seen || e.sequence<=sequence) return
                sequence=e.sequence;seen.add(e.eventId)
                while(seen.size>256)seen.remove(seen.first())
                if(invalidated && e.kind !in setOf(CoreUiKind.CANCELLED,CoreUiKind.ERROR)) return
                when(e.kind) {
                    CoreUiKind.USER_INPUT -> phase("Préparation")
                    CoreUiKind.THINKING -> phase(if(current.connection==PortConnection.DEMONSTRATION) "Démo : progression simulée, aucun calcul IA" else "CORE traite la demande — moteur non attesté")
                    CoreUiKind.RESPONSE_CHUNK -> phase("Réponse signalée ; contenu non raccordé")
                    CoreUiKind.TOOL_PENDING -> phase("Outil en attente d'autorisation CORE")
                    CoreUiKind.SPEECH_REQUESTED -> phase("Parole demandée ; VOICE non raccordée")
                    CoreUiKind.CANCEL_REQUESTED -> { invalidated=true;phase("Annulation demandée ; attente de fin effective") }
                    CoreUiKind.RESPONSE_COMPLETED -> finish(if(current.connection==PortConnection.DEMONSTRATION) "Démo terminée — aucun LLM exécuté" else "Réponse CORE disponible — contenu non raccordé")
                    CoreUiKind.CANCELLED -> finish("Annulé — aucune preuve de déchargement natif")
                    CoreUiKind.ERROR -> if(e.errorCode==CoreErrorCode.RESOURCE_UNCONFIRMED) { resourceBlocked=true;unknown("Ressources non confirmées — nouveau lancement bloqué") } else finish("Erreur CORE — aucun résultat présumé")
                }
            }
        }
    }
    private fun unknown(text: String) { invalidated=true; mutable.update { it.copy(busy=true,phase=text,notifications=(it.notifications+text).takeLast(10)) } }
    private fun phase(text: String) { mutable.update { it.copy(phase=text) } }
    private fun finish(text: String) {
        if(resourceBlocked){unknown("Ressources non confirmées — nouveau lancement bloqué");return}
        terminal=true
        mutable.update { it.copy(busy=false,phase=text,notifications=(it.notifications+text).takeLast(10)) }
    }
    fun cancel() {
        if(!mutable.value.busy) return
        invalidated=true; phase("Annulation demandée ; attente de fin effective")
        mutable.value.key?.let { key -> scope.launch {
            try { if(!port.cancel(key)) phase("Annulation non confirmée ; attente du port") }
            catch(e: CancellationException){throw e}
            catch (_: Exception) { phase("Annulation non confirmée ; attente du port") }
        } }
    }
    fun close() { jobs.forEach { it.cancel() }; port.close() }
}
