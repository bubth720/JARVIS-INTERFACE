package com.jarvis.interfaceapp.ui.identity

import com.jarvis.interfaceapp.ui.memory.*
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class IdentitySettingsUiState(val connection:IdentityConnection=IdentityConnection.UNAVAILABLE,
    val snapshot:IdentitySnapshot=IdentitySnapshot(0,emptyList(),null),val selectedId:String?=null,
    val draft:ProfileDraft?=null,val context:PersonaContext?=null,val proposal:ProfileProposal?=null,
    val history:List<ProfileView> = emptyList(),val busy:Boolean=false,val requiresRefresh:Boolean=false,
    val message:String="NON CONNECTÉ · aucun profil chargé ni sauvegardé",val memoryAllowed:Boolean=false,
    val memories:List<UiMemoryRecord> = emptyList(),val memoryCandidate:UiMemoryCandidate?=null,
    val deleteCandidate:UiMemoryReference?=null,val exportPreview:UiMemoryExport?=null,
    val exportReady:ByteArray?=null,val exportToken:String?=null,val memoryText:String="") {
    val editable:Boolean get()=connection!=IdentityConnection.UNAVAILABLE && !busy && !requiresRefresh && draft!=null
    val current:ProfileView? get()=snapshot.profiles.singleOrNull { it.reference.id==selectedId }
}
/** UI-only owner projection, lifecycle-owned RAM. No local profile/memory database or IPC. */
class IdentitySettingsController(private val scope:CoroutineScope,
    private var port:IdentitySettingsPort=UnavailableIdentitySettingsPort(),
    private var memory:MemoryConsentUiPort=UnavailableMemoryConsentUiPort()) {
    private val mutable=MutableStateFlow(IdentitySettingsUiState())
    val state=mutable.asStateFlow()
    private var generation=0L
    private var session=UUID.randomUUID().toString()
    init { refresh() }
    private fun update(token:Long,transform:(IdentitySettingsUiState)->IdentitySettingsUiState) {
        if(token==generation) mutable.value=transform(mutable.value)
    }
    private fun operation(block:suspend (Long)->Unit) {
        if(mutable.value.busy) return
        val token=generation;mutable.value=mutable.value.copy(busy=true)
        scope.launch {
            try { block(token) }
            catch(c:CancellationException) { throw c }
            catch(_:Exception) { update(token) { it.copy(message="Opération refusée ou hôte indisponible · aucun ACK durable",proposal=null,memoryCandidate=null,deleteCandidate=null,exportPreview=null) } }
            finally { update(token) { it.copy(busy=false) } }
        }
    }
    fun refresh() {
        val owner=port
        operation { token ->
            val data=owner.read();val chosen=data.profiles.firstOrNull { it.reference.id==data.selectedId }?:data.profiles.firstOrNull()
            val history=chosen?.let { owner.history(it.reference.id) }.orEmpty()
            val projected=if(mutable.value.memoryAllowed) memory.visible(session,true).filter { it.scope!=UiMemoryScope.SENSITIVE } else emptyList()
            update(token) { it.copy(connection=owner.connection,snapshot=data,selectedId=chosen?.reference?.id,draft=chosen?.draft,
                proposal=null,requiresRefresh=false,history=history,memories=projected,memoryCandidate=null,deleteCandidate=null,exportPreview=null,exportReady=null,exportToken=null,message=if(owner.connection==IdentityConnection.UNAVAILABLE)
                    "NON CONNECTÉ · aucun profil chargé ni sauvegardé" else if(owner.connection==IdentityConnection.LABORATORY)
                    "LABORATOIRE EN RAM · CORE non connecté · aucune personnalité active" else "Projection du propriétaire autorisé · aucune activation par INTERFACE") }
        }
    }
    fun laboratory(enabled:Boolean) {
        generation++;port.close();memory.revoke(session);memory.close();session=UUID.randomUUID().toString()
        port=if(enabled) LaboratoryIdentitySettingsPort() else UnavailableIdentitySettingsPort()
        memory=if(enabled) LaboratoryMemoryConsentUiPort() else UnavailableMemoryConsentUiPort()
        mutable.value=IdentitySettingsUiState();refresh()
    }
    fun selectProfile(id:String) {
        if(mutable.value.busy) return
        val owner=port;val p=mutable.value.snapshot.profiles.singleOrNull { it.reference.id==id }?:return
        mutable.value=mutable.value.copy(selectedId=id,draft=p.draft,proposal=null,context=null)
        operation { token -> val h=owner.history(id);update(token) { it.copy(history=h) } }
    }
    fun context(value:PersonaContext?) { if(!mutable.value.busy) mutable.value=mutable.value.copy(context=value) }
    private fun edit(transform:(ProfileDraft)->ProfileDraft) {
        val s=mutable.value;if(!s.editable) return
        mutable.value=s.copy(draft=transform(s.draft!!),proposal=null,message="Brouillon uniquement · proposer et confirmer avant toute décision")
    }
    fun overrideContext(enabled:Boolean) {
        val c=mutable.value.context?:return
        edit { d -> d.copy(overrides=if(enabled) d.overrides+(c to d.parameters(c)) else d.overrides-c) }
    }
    fun parameters(transform:(PersonaParameters)->PersonaParameters) {
        val c=mutable.value.context
        edit { d -> if(c==null) d.copy(defaults=transform(d.defaults))
            else if(c in d.overrides) d.copy(overrides=d.overrides+(c to transform(d.overrides.getValue(c)))) else d }
    }
    fun interest(value:SimulatedInterest,enabled:Boolean)=edit { it.copy(interests=if(enabled) it.interests+value else it.interests-value) }
    fun propose(restoreRevision:Long?=null) {
        val s=mutable.value;val current=s.current?:return;val d=s.draft?:return;val owner=port
        if(s.connection==IdentityConnection.UNAVAILABLE || s.requiresRefresh) return
        operation { token ->
            val p=owner.propose(current.reference,s.snapshot.epoch,d,restoreRevision)
            update(token) { it.copy(proposal=p,message="Aperçu à vérifier · aucune modification persistée") }
        }
    }
    fun rejectProposal() {
        val p=mutable.value.proposal?:return;val owner=port
        operation { token -> owner.reject(p.id);update(token) { it.copy(proposal=null,message="Proposition rejetée · profil inchangé") } }
    }
    fun approveProposal(id:String,digest:String,epoch:Long) {
        val p=mutable.value.proposal?:return
        if(p.id!=id || p.approvalDigest!=digest || p.epoch!=epoch) { mutable.value=mutable.value.copy(message="Aperçu modifié · confirmation refusée");return }
        val owner=port
        operation { token ->
            val ack=owner.approve(id,digest,epoch)
            if(ack is IdentityAck.Applied) {
                val committed=ack.snapshot.profiles.singleOrNull { it.reference.id==p.expected.id }
                val valid=committed!=null && committed.reference.revision==p.expected.revision+1 &&
                    committed.draft==p.draft && ack.snapshot.epoch>p.epoch && (!ack.durableOwnerAck || owner.connection==IdentityConnection.AUTHORIZED_OWNER)
                if(!valid) update(token) { it.copy(proposal=null,requiresRefresh=true,message="ACK incohérent · résultat INCONNU, recharger avant de continuer") }
                else {
                    val h=owner.history(p.expected.id)
                    update(token) { it.copy(snapshot=ack.snapshot,draft=committed!!.draft,history=h,proposal=null,
                        message=if(ack.durableOwnerAck) "Révision confirmée par CORE propriétaire · aucune activation implicite"
                            else "LABORATOIRE · nouvelle révision en RAM seulement · rien sauvegardé dans CORE") }
                }
            } else update(token) { it.copy(proposal=null,requiresRefresh=ack is IdentityAck.Unknown || ack is IdentityAck.Refused && ack.reason=="REVISION_CONFLICT",message=if(ack is IdentityAck.Refused) "Refus du propriétaire : ${ack.reason}" else "ACK absent · résultat INCONNU, recharger avant de continuer") }
        }
    }
    fun allowMemory(allowed:Boolean) {
        if(mutable.value.busy) return
        val owner=memory;val currentSession=session
        if(!allowed) owner.revoke(currentSession)
        mutable.value=mutable.value.copy(memoryAllowed=allowed,memories=emptyList(),memoryCandidate=null,deleteCandidate=null,exportPreview=null,exportReady=null,exportToken=null,message=if(allowed) "Projection mémoire explicitement ouverte" else "Projection mémoire suspendue · toute autorisation d’export révoquée")
        operation { token -> val records=owner.visible(currentSession,allowed)
            update(token) { it.copy(memories=records.filter { r -> r.scope!=UiMemoryScope.SENSITIVE }) } }
    }
    fun memoryText(text:String) { if(text.length<=1500) mutable.value=mutable.value.copy(memoryText=text) }
    fun proposeMemory(text:String,scope:UiMemoryScope) {
        if(!mutable.value.memoryAllowed || mutable.value.requiresRefresh || mutable.value.connection==IdentityConnection.UNAVAILABLE) return
        val owner=memory;val currentSession=session
        operation { token -> val p=owner.propose(currentSession,text,scope);update(token) { it.copy(memoryCandidate=p) } }
    }
    fun decideMemory(candidate:UiMemoryCandidate,accept:Boolean) {
        if(!mutable.value.memoryAllowed || mutable.value.memoryCandidate!=candidate) return
        val owner=memory
        operation { token -> val result=owner.decide(candidate,candidate.content,accept);memoryAck(token,result) }
    }
    fun suspendMemory(ref:UiMemoryReference,suspended:Boolean) {
        if(!mutable.value.memoryAllowed || mutable.value.requiresRefresh) return
        val owner=memory;val currentSession=session
        operation { token -> memoryAck(token,owner.suspendRecord(currentSession,ref,suspended)) }
    }
    fun requestDelete(ref:UiMemoryReference) { if(!mutable.value.busy && mutable.value.memoryAllowed) mutable.value=mutable.value.copy(deleteCandidate=ref) }
    fun cancelDelete() { mutable.value=mutable.value.copy(deleteCandidate=null) }
    fun confirmDelete(ref:UiMemoryReference) {
        if(mutable.value.deleteCandidate!=ref || !mutable.value.memoryAllowed) return
        val owner=memory;val currentSession=session
        operation { token -> memoryAck(token,owner.delete(currentSession,ref)) }
    }
    private fun memoryAck(token:Long,result:MemoryUiAck) {
        update(token) { s -> when(result) {
            is MemoryUiAck.LaboratoryApplied -> s.copy(memories=result.records.filter { it.scope!=UiMemoryScope.SENSITIVE },memoryCandidate=null,deleteCandidate=null,exportPreview=null,
                message="LABORATOIRE mémoire · changement en RAM uniquement")
            is MemoryUiAck.OwnerApplied -> if(memory.laboratory) s.copy(message="ACK propriétaire incohérent · résultat INCONNU",requiresRefresh=true) else s.copy(memories=result.records.filter { it.scope!=UiMemoryScope.SENSITIVE },memoryCandidate=null,deleteCandidate=null,exportPreview=null,
                message="Décision confirmée par le propriétaire mémoire")
            is MemoryUiAck.Refused -> s.copy(memoryCandidate=null,deleteCandidate=null,exportPreview=null,message="Mémoire refusée : ${result.reason}")
            MemoryUiAck.Unknown -> s.copy(memoryCandidate=null,deleteCandidate=null,exportPreview=null,requiresRefresh=true,message="ACK mémoire absent · résultat INCONNU")
        } }
    }
    fun previewExport(selected:Set<UiMemoryReference>) {
        if(!mutable.value.memoryAllowed || mutable.value.requiresRefresh) return
        val owner=memory;val currentSession=session
        operation { token -> val p=owner.previewExport(currentSession,selected);update(token) { it.copy(exportPreview=p) } }
    }
    fun cancelExport() { mutable.value=mutable.value.copy(exportPreview=null,exportReady=null,exportToken=null) }
    fun approveExport(preview:UiMemoryExport) {
        if(mutable.value.exportPreview!=preview || !mutable.value.memoryAllowed) return
        val owner=memory
        operation { token -> val bytes=owner.approveExport(preview,preview.digest)
            update(token) { it.copy(exportPreview=null,exportReady=bytes.copyOf(),exportToken=preview.id,message="Export approuvé · choisir volontairement une destination vide") } }
    }
    fun exportResult(token:String,verified:Boolean,cancelled:Boolean=false) {
        if(mutable.value.exportToken!=token) return
        mutable.value=mutable.value.copy(exportReady=null,exportToken=null,message=if(cancelled) "Export annulé · aucune destination écrite"
            else if(verified) "Copie d’export vérifiée · aucune mémoire importée ou activée"
            else "Résultat de copie INCONNU · vérifier la destination, aucun succès déclaré")
    }
    fun close() { generation++;port.close();memory.revoke(session);memory.close();mutable.value=IdentitySettingsUiState() }
}
