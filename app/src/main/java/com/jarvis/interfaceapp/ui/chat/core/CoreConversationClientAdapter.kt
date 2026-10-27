package com.jarvis.interfaceapp.ui.chat.core

import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import com.jarvis.interfaceapp.ui.chat.CoreMetadata
import com.jarvis.interfaceapp.ui.chat.CoreUiKind
import com.jarvis.interfaceapp.ui.chat.CoreErrorCode

data class ContentClientState(val link:CoreTextLink=CoreTextLink.UNAVAILABLE,
    val origin:CoreTextOrigin=CoreTextOrigin.NONE,val key:ContentKey?=null,val busy:Boolean=false,
    val text:String="",val phase:String="CORE NON CONNECTÉ · contrat de contenu/transport non approuvé",
    val toolsPending:List<String> = emptyList())

/** Local consumer limits; does not negotiate or amend an external wire contract. */
data class ContentClientBudget(val questionBytes:Int=16*1024,val chunkBytes:Int=16*1024) {
    init {require(questionBytes in 1..16*1024 && chunkBytes in 1..16*1024)}
    companion object {val Fusion02Review=ContentClientBudget(questionBytes=1024,chunkBytes=4096)}
}

/** UI-dispatcher confined prototype. No real-CORE implementation is supplied or activated.
 * CORE metadata alone can never create assistant content or attest native release. */
class CoreConversationClientAdapter(private val transport:CoreConversationTransport,private val scope:CoroutineScope,private val budget:ContentClientBudget=ContentClientBudget()) {
    private val mutable=MutableStateFlow(ContentClientState(if(transport.origin==CoreTextOrigin.LABORATORY) transport.connection.value else CoreTextLink.UNAVAILABLE,transport.origin))
    val state=mutable.asStateFlow()
    private var closed=false
    private var streamClosed=false
    private val ownedJob=SupervisorJob(scope.coroutineContext[Job])
    private val localScope=CoroutineScope(scope.coroutineContext+ownedJob)
    private var awaiting=false
    private var terminal=false
    private var cancelled=false
    private var lastSequence=0L
    private var totalBytes=0
    private var chunkCount=0
    private val early=ArrayDeque<CoreMessageEnvelope>()
    private val ordered=sortedMapOf<Long,CoreMessageEnvelope>()
    private val seen=linkedSetOf<String>()
    private val acknowledgedKeys=linkedSetOf<ContentKey>()
    private val jobs=listOf(localScope.launch {
        transport.connection.collect { link ->
            if(mutable.value.link!=CoreTextLink.UNKNOWN) mutable.update { it.copy(link=if(transport.origin==CoreTextOrigin.LABORATORY) link else CoreTextLink.UNAVAILABLE) }
            if(mutable.value.busy && link!=CoreTextLink.READY) unknown("Connexion perdue ; état natif non attesté")
        }
    },localScope.launch {
        transport.updates.catch { e -> if(e is CancellationException)throw e;unknown("Flux perdu") }
            .onCompletion { cause -> if(cause==null && !closed) {
                streamClosed=true
                if(mutable.value.busy) unknown("Flux fermé sans fin de contenu")
                else mutable.update { it.copy(link=CoreTextLink.UNAVAILABLE,phase="Flux de contenu fermé") }
            } }
            .collect { event ->
                if(awaiting && mutable.value.key==null) {
                    if(early.size>=16) unknown("Trop d’événements avant ACK") else early.addLast(event)
                } else accept(event)
            }
    })
    fun submit(text:String):Boolean {
        if(text.length !in 1..16*1024)return false
        if(closed || streamClosed || text.isBlank() || mutable.value.busy || awaiting || mutable.value.link!=CoreTextLink.READY ||
            transport.origin!=CoreTextOrigin.LABORATORY) return false
        val submission=try { textBytes(text,budget.questionBytes);SubmitUserText(UUID.randomUUID().toString(),text) } catch(_:Exception) { return false }
        awaiting=true;terminal=false;cancelled=false;lastSequence=0;totalBytes=0;chunkCount=0;early.clear();ordered.clear();seen.clear()
        mutable.update { it.copy(key=null,busy=true,text="",toolsPending=emptyList(),phase="LAB · attente ACK, aucun CORE connecté") }
        localScope.launch {
            try {
                val ack=withTimeout(5000) { transport.submit(submission) }
                if(closed || mutable.value.link==CoreTextLink.UNKNOWN) return@launch
                if(ack==null) { terminal=true;mutable.update { it.copy(busy=false,phase="Refus du port de laboratoire") };return@launch }
                require(ack.submissionId==submission.submissionId && ack.key !in acknowledgedKeys && acknowledgedKeys.size<128)
                acknowledgedKeys.add(ack.key)
                mutable.update { it.copy(key=ack.key,phase="LAB · ACK corrélé, attente du contenu") }
                if(cancelled) stopWithDeadline(ack.key)
            } catch(e:TimeoutCancellationException) { unknown("ACK expiré ; aucun redémarrage présumé sûr") }
            catch(e:CancellationException) { throw e }
            catch(_:Exception) { unknown("ACK invalide ou perdu") }
            finally { awaiting=false;val pending=early.toList();early.clear();pending.forEach(::accept) }
        }
        return true
    }
    private fun accept(event:CoreMessageEnvelope) {
        val current=mutable.value
        if(closed || terminal || current.link==CoreTextLink.UNKNOWN || event.key!=current.key || event.sequence<=lastSequence) return
        if(event.eventId in seen) { unknown("Identifiant rejoué dans une nouvelle séquence");return }
        if(event.sequence!=lastSequence+1) {
            if(ordered.size>=8 || (ordered[event.sequence]!=null && ordered[event.sequence]!=event)) unknown("Ordre de flux ambigu ou budget dépassé")
            else ordered[event.sequence]=event
            return
        }
        apply(event)
        while(!terminal && mutable.value.link!=CoreTextLink.UNKNOWN) {
            val next=ordered.remove(lastSequence+1) ?: break
            if(next.eventId in seen) { unknown("Identifiant rejoué dans une autre séquence");break }
            apply(next)
        }
    }
    private fun apply(event:CoreMessageEnvelope) {
        lastSequence=event.sequence;seen.add(event.eventId);while(seen.size>256)seen.remove(seen.first())
        when(val body=event.body) {
            is ContentBody.AssistantTextChunk -> if(!cancelled) {
                val bytes=runCatching {textBytes(body.text,budget.chunkBytes)}.getOrNull()
                if(bytes==null) {unknown("Chunk hors budget du consommateur");return}
                totalBytes+=bytes;chunkCount++
                if(totalBytes>256*1024 || chunkCount>256) unknown("Budget de contenu dépassé")
                else mutable.update { it.copy(text=it.text+body.text,phase="LAB · contenu de test, aucune inférence") }
            }
            ContentBody.Completed -> if(!cancelled) {
                terminal=true;ordered.clear()
                mutable.update { it.copy(busy=false,phase=if(it.text.isBlank()) "Fin sans texte : aucune réponse présentée" else "LAB terminé · pas une réponse CORE/LLM") }
            }
            ContentBody.Cancelled -> { terminal=true;ordered.clear();mutable.update { it.copy(busy=false,phase="Annulation logique confirmée · déchargement natif non attesté") } }
            is ContentBody.Failure -> if(body.code==ContentFailure.RESOURCE_UNCONFIRMED) unknown("Ressources non confirmées") else {
                terminal=true;ordered.clear();mutable.update { it.copy(busy=false,phase="Échec du port : ${body.code}") }
            }
            is ContentBody.ToolPending -> if(!cancelled) mutable.update { it.copy(toolsPending=(it.toolsPending+body.publicToolId).distinct().takeLast(16),phase="Outil signalé · aucune exécution ni autorisation accordée") }
        }
    }
    fun observeMetadata(event:CoreMetadata) {
        val key=mutable.value.key ?: return
        if(event.key.sessionId!=key.sessionId || event.key.requestId!=key.requestId || event.generation!=key.generation || terminal) return
        if(event.kind==CoreUiKind.ERROR && event.errorCode==CoreErrorCode.RESOURCE_UNCONFIRMED) unknown("Métadonnées : ressources non confirmées")
        else if(event.kind==CoreUiKind.RESPONSE_COMPLETED && mutable.value.text.isBlank())
            mutable.update { it.copy(phase="Métadonnées de fin seules : contenu non raccordé") }
    }
    fun cancel() {
        if(!mutable.value.busy || mutable.value.link==CoreTextLink.UNKNOWN) return
        cancelled=true;mutable.update { it.copy(phase="Arrêt demandé · attente confirmation logique") }
        mutable.value.key?.let { key -> localScope.launch { stopWithDeadline(key) } }
    }
    private suspend fun stopWithDeadline(key:ContentKey) {
        try {
            val accepted=withTimeout(5000) { transport.stop(key) }
            if(!accepted && !terminal) unknown("Arrêt non confirmé")
        } catch(_:TimeoutCancellationException) {
            if(!terminal) unknown("Délai d’arrêt expiré ; fin effective inconnue")
        } catch(e:CancellationException) { throw e }
        catch(_:Exception) { if(!terminal) unknown("Port d’arrêt indisponible") }
    }
    private fun unknown(message:String) { if(closed)return;ordered.clear();early.clear();mutable.update { it.copy(link=CoreTextLink.UNKNOWN,busy=true,phase="$message · nouveau lancement bloqué") } }
    fun close() { closed=true;ownedJob.cancel();transport.close();cancelled=true;terminal=true;early.clear();ordered.clear();mutable.value=ContentClientState(link=CoreTextLink.DISABLED,phase="Banc fermé, contenu RAM effacé") }
}
