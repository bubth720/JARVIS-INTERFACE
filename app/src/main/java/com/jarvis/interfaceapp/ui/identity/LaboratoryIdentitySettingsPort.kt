package com.jarvis.interfaceapp.ui.identity

import java.util.UUID

/** Explicit RAM laboratory. No CORE instance, persistence, B archive or production activation. */
class LaboratoryIdentitySettingsPort(private val elapsedMs: () -> Long = { System.nanoTime()/1_000_000 }) : IdentitySettingsPort {
    override val connection=IdentityConnection.LABORATORY
    private val rows=linkedMapOf<String,MutableList<ProfileView>>()
    private var epoch=0L
    private val pending=linkedMapOf<String,ProfileProposal>()
    private var closed=false
    init {
        listOf("00000000-0000-0000-0000-000000000001" to PersonaPreset.ATHENA,
            "00000000-0000-0000-0000-000000000002" to PersonaPreset.NEUTRAL).forEach { (id,preset) ->
            val d=freezeDraft(ProfileDraft.preset(preset));rows[id]=mutableListOf(ProfileView(ProfileReference(id,1,draftDigest(d)),d))
        }
    }
    override suspend fun read(): IdentitySnapshot {
        check(!closed) { "LAB_CLOSED" };return IdentitySnapshot(epoch,rows.values.map { it.last() },rows.keys.first())
    }
    override suspend fun propose(expected: ProfileReference, epoch: Long, replacement: ProfileDraft, restoreRevision: Long?): ProfileProposal {
        check(!closed) { "LAB_CLOSED" }
        val current=rows[expected.id]?.last()?:error("PROFILE_ABSENT")
        require(this.epoch==epoch && current.reference==expected) { "REVISION_CONFLICT" }
        require(expected.revision<64 && pending.size<16) { "PROPOSAL_QUOTA" }
        val draft=freezeDraft(if(restoreRevision!=null) rows.getValue(expected.id).singleOrNull { it.reference.revision==restoreRevision }?.draft?:error("REVISION_ABSENT") else replacement)
        val now=elapsedMs();require(now>=0 && now<=Long.MAX_VALUE-90_000)
        val id=UUID.randomUUID().toString()
        val proposal=ProfileProposal(id,expected,epoch,uiDigest(id,epoch.toString(),expected.digest,draftDigest(draft),restoreRevision.toString()),
            draft,ProfileDiffUi.compare(current.draft,draft),PersonaContext.entries.map { ReadOnlyPersonalityPreview.create(draft,it) },restoreRevision,now,now+90_000)
        pending[id]=proposal;return proposal
    }
    override suspend fun approve(id:String,exactDigest:String,epoch:Long): IdentityAck {
        if(closed) return IdentityAck.Refused("LAB_CLOSED")
        val p=pending[id]?:return IdentityAck.Refused("PROPOSAL_ABSENT_OR_CONSUMED")
        if(p.approvalDigest!=exactDigest || p.epoch!=epoch) return IdentityAck.Refused("PREVIEW_MISMATCH")
        val now=elapsedMs()
        if(now<p.createdElapsedMs || now>=p.expiresElapsedMs) { pending.remove(id);return IdentityAck.Refused("PROPOSAL_EXPIRED") }
        val history=rows.getValue(p.expected.id)
        if(this.epoch!=epoch || history.last().reference!=p.expected) { pending.remove(id);return IdentityAck.Refused("REVISION_CONFLICT") }
        pending.remove(id)
        val next=ProfileReference(p.expected.id,p.expected.revision+1,draftDigest(p.draft))
        history.add(ProfileView(next,p.draft));this.epoch++
        return IdentityAck.Applied(read(),false)
    }
    override suspend fun reject(id:String) { pending.remove(id) }
    override suspend fun history(id:String):List<ProfileView> = if(closed) emptyList() else rows[id]?.toList().orEmpty()
    override fun close() { closed=true;pending.clear();rows.clear() }
}
