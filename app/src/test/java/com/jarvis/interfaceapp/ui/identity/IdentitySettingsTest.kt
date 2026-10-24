package com.jarvis.interfaceapp.ui.identity

import com.jarvis.interfaceapp.ui.memory.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class IdentitySettingsTest {
    @Test fun disconnectedIsInertAndNeverCreatesAProfile()=runTest {
        val c=IdentitySettingsController(this);runCurrent()
        assertEquals(IdentityConnection.UNAVAILABLE,c.state.value.connection);assertNull(c.state.value.draft)
        assertTrue(c.state.value.snapshot.profiles.isEmpty());assertFalse(c.state.value.editable)
        c.propose();runCurrent();assertNull(c.state.value.proposal);assertTrue(c.state.value.memories.isEmpty())
    }
    @Test fun laboratoryRequiresExplicitOptInAndHasNoDurableOwnerAck()=runTest {
        val c=IdentitySettingsController(this);runCurrent();c.laboratory(true);runCurrent()
        assertEquals(IdentityConnection.LABORATORY,c.state.value.connection)
        c.parameters { it.copy(humour=42) };c.propose();runCurrent();val p=c.state.value.proposal!!
        assertEquals(1,c.state.value.current!!.reference.revision)
        c.approveProposal(p.id,p.approvalDigest,p.epoch);runCurrent()
        assertEquals(2,c.state.value.current!!.reference.revision);assertTrue(c.state.value.message.contains("RAM"))
        c.laboratory(false);runCurrent();assertTrue(c.state.value.snapshot.profiles.isEmpty())
    }
    @Test fun contextInheritanceDeletionIsVisibleEvenWhenEffectiveValuesAreEqual() {
        val d=ProfileDraft.preset(PersonaPreset.ATHENA)
        val before=d.copy(overrides=mapOf(PersonaContext.WORK to d.defaults))
        val diff=ProfileDiffUi.compare(before,d)
        assertEquals(1,diff.size);assertEquals("override:WORK",diff.single().field)
        assertEquals("INHERIT_DEFAULTS",diff.single().after)
    }
    @Test fun workAndUrgentKeepCoreEditorialCapsWithoutClaimingInference() {
        val d=ProfileDraft.preset(PersonaPreset.ATHENA)
        val work=ReadOnlyPersonalityPreview.create(d,PersonaContext.WORK)
        val urgent=ReadOnlyPersonalityPreview.create(d,PersonaContext.URGENT)
        assertEquals(30,work.effective.humour);assertEquals(0,urgent.effective.humour)
        assertEquals(40,urgent.effective.initiative);assertTrue(work.label.contains("pas une réponse LLM"))
    }
    @Test fun lexicalRulesArePreservedAndSuspendedInUrgentContext() {
        val d=ProfileDraft.preset(PersonaPreset.ATHENA).copy(rules=listOf(LexicalRule(LexicalToken.BONJOUR,LexicalToken.SALUT)))
        assertTrue(ReadOnlyPersonalityPreview.create(d,PersonaContext.DAILY).after.startsWith("Salut"))
        assertTrue(ReadOnlyPersonalityPreview.create(d,PersonaContext.URGENT).after.startsWith("Bonjour"))
        assertThrows(IllegalArgumentException::class.java) { d.copy(rules=listOf(LexicalRule(LexicalToken.BONJOUR,LexicalToken.SALUT),LexicalRule(LexicalToken.SALUT,LexicalToken.MERCI))) }
    }
    @Test fun percentBoundsAndNameByteBudgetMatchOwner() {
        assertThrows(IllegalArgumentException::class.java) { PersonaParameters(101,0,0,true,PersonaStyle.PLAIN) }
        assertThrows(IllegalArgumentException::class.java) { ProfileDraft.preset(PersonaPreset.ATHENA).copy(name="é".repeat(61)) }
    }
    @Test fun exactPreviewDigestAndEpochAreRequired()=runTest {
        val port=LaboratoryIdentitySettingsPort();val s=port.read();val ref=s.profiles.first().reference
        val p=port.propose(ref,s.epoch,s.profiles.first().draft.copy(defaults=s.profiles.first().draft.defaults.copy(humour=25)))
        assertTrue(port.approve(p.id,"0".repeat(64),p.epoch) is IdentityAck.Refused)
        assertTrue(port.approve(p.id,p.approvalDigest,p.epoch+1) is IdentityAck.Refused)
        assertEquals(1,port.read().profiles.first().reference.revision)
        val ack=port.approve(p.id,p.approvalDigest,p.epoch) as IdentityAck.Applied
        assertFalse(ack.durableOwnerAck);assertEquals(2,ack.snapshot.profiles.first().reference.revision)
        assertTrue(port.approve(p.id,p.approvalDigest,p.epoch) is IdentityAck.Refused)
    }
    @Test fun expiredAndClockRollbackProposalsRefuseWithoutChangingHistory()=runTest {
        var time=100L;val port=LaboratoryIdentitySettingsPort { time };val s=port.read();val p=port.propose(s.profiles.first().reference,0,s.profiles.first().draft)
        time=90_100;assertEquals("PROPOSAL_EXPIRED",(port.approve(p.id,p.approvalDigest,p.epoch) as IdentityAck.Refused).reason)
        assertEquals(1,port.history(p.expected.id).size)
    }
    @Test fun concurrentProposalBecomesStaleAfterAnotherApproval()=runTest {
        val port=LaboratoryIdentitySettingsPort();val s=port.read();val first=s.profiles.first()
        val a=port.propose(first.reference,0,first.draft.copy(defaults=first.draft.defaults.copy(humour=20)))
        val b=port.propose(first.reference,0,first.draft.copy(defaults=first.draft.defaults.copy(humour=80)))
        assertTrue(port.approve(a.id,a.approvalDigest,0) is IdentityAck.Applied)
        assertEquals("REVISION_CONFLICT",(port.approve(b.id,b.approvalDigest,0) as IdentityAck.Refused).reason)
    }
    @Test fun restoreCreatesANewRevisionWithoutErasingAnyHistory()=runTest {
        val port=LaboratoryIdentitySettingsPort();val s=port.read();val initial=s.profiles.first()
        val p=port.propose(initial.reference,0,initial.draft.copy(defaults=initial.draft.defaults.copy(humour=7)))
        port.approve(p.id,p.approvalDigest,0);val edited=port.read()
        val restore=port.propose(edited.profiles.first().reference,edited.epoch,edited.profiles.first().draft,1)
        assertEquals(initial.draft,restore.draft);assertEquals(2,port.history(initial.reference.id).size)
        port.approve(restore.id,restore.approvalDigest,restore.epoch)
        assertEquals(3,port.history(initial.reference.id).size);assertEquals(initial.draft,port.read().profiles.first().draft)
    }
    @Test fun mutableInputCannotChangeApprovedPreviewAfterProposal()=runTest {
        val port=LaboratoryIdentitySettingsPort();val s=port.read();val initial=s.profiles.first()
        val overrides=mutableMapOf(PersonaContext.WORK to initial.draft.defaults.copy(humour=2))
        val p=port.propose(initial.reference,0,initial.draft.copy(overrides=overrides))
        overrides.clear();assertTrue(PersonaContext.WORK in p.draft.overrides)
        port.approve(p.id,p.approvalDigest,0);assertTrue(PersonaContext.WORK in port.read().profiles.first().draft.overrides)
    }
    @Test fun unknownAckRequiresRefreshAndNeverClaimsSaved()=runTest {
        val lab=LaboratoryIdentitySettingsPort()
        val unknown=object:IdentitySettingsPort by lab { override suspend fun approve(id:String,exactDigest:String,epoch:Long)=IdentityAck.Unknown }
        val c=IdentitySettingsController(this,unknown);runCurrent();c.propose();runCurrent();val p=c.state.value.proposal!!
        c.approveProposal(p.id,p.approvalDigest,p.epoch);runCurrent()
        assertTrue(c.state.value.requiresRefresh);assertFalse(c.state.value.editable);assertTrue(c.state.value.message.contains("INCONNU"))
        assertEquals(1,c.state.value.current!!.reference.revision)
    }
    @Test fun lateAckFromClosedLaboratoryCannotRepopulateDisconnectedScreen()=runTest {
        val lab=LaboratoryIdentitySettingsPort();val gate=CompletableDeferred<IdentityAck>()
        val late=object:IdentitySettingsPort by lab { override suspend fun approve(id:String,exactDigest:String,epoch:Long):IdentityAck=gate.await() }
        val c=IdentitySettingsController(this,late);runCurrent();c.propose();runCurrent();val p=c.state.value.proposal!!
        c.approveProposal(p.id,p.approvalDigest,p.epoch);runCurrent();c.laboratory(false);runCurrent()
        gate.complete(IdentityAck.Unknown);runCurrent()
        assertEquals(IdentityConnection.UNAVAILABLE,c.state.value.connection);assertFalse(c.state.value.requiresRefresh);assertNull(c.state.value.draft)
    }
    @Test fun changingDraftInvalidatesDisplayedApproval()=runTest {
        val c=IdentitySettingsController(this,LaboratoryIdentitySettingsPort());runCurrent();c.propose();runCurrent();val p=c.state.value.proposal!!
        c.parameters { it.copy(humour=11) };assertNull(c.state.value.proposal)
        c.approveProposal(p.id,p.approvalDigest,p.epoch);runCurrent();assertEquals(1,c.state.value.current!!.reference.revision)
    }
    private suspend fun fails(block:suspend ()->Any?) { var failed=false;try { block() } catch(_:Exception) { failed=true };assertTrue(failed) }
    @Test fun memoryStartsClosedSensitiveScopeRefusedAndNoImplicitConfirmation()=runTest {
        val m=LaboratoryMemoryConsentUiPort();val session=UUID.randomUUID().toString()
        assertTrue(m.visible(session,false).isEmpty());fails { m.propose(session,"Exemple public",UiMemoryScope.GENERAL) }
        m.visible(session,true);fails { m.propose(session,"Exemple public",UiMemoryScope.SENSITIVE) }
        m.propose(session,"Exemple public",UiMemoryScope.GENERAL)
        assertTrue(m.visible(session,true).isEmpty())
    }
    @Test fun memoryRequiresExactContentAndConsumesConsentOnce()=runTest {
        val m=LaboratoryMemoryConsentUiPort();val session=UUID.randomUUID().toString();m.visible(session,true)
        val p=m.propose(session,"Exemple public exact",UiMemoryScope.PREFERENCE)
        assertTrue(m.decide(p,"Autre texte",true) is MemoryUiAck.Refused)
        assertTrue(m.decide(p,p.content,true) is MemoryUiAck.LaboratoryApplied)
        assertEquals(1,m.visible(session,true).size)
        assertTrue(m.decide(p,p.content,true) is MemoryUiAck.Refused)
    }
    @Test fun memoryExpiredOrRevokedConsentCannotConfirm()=runTest {
        var now=0L;val m=LaboratoryMemoryConsentUiPort({now});val session=UUID.randomUUID().toString();m.visible(session,true)
        val p=m.propose(session,"Exemple public",UiMemoryScope.GENERAL);now=90_000
        assertTrue(m.decide(p,p.content,true) is MemoryUiAck.Refused)
        val q=m.propose(session,"Exemple public 2",UiMemoryScope.GENERAL);m.revoke(session)
        assertTrue(m.decide(q,q.content,true) is MemoryUiAck.Refused);assertTrue(m.visible(session,true).isEmpty())
    }
    @Test fun exportNeedsExactDigestAndCannotSurviveSuspensionOrRevocation()=runTest {
        val m=LaboratoryMemoryConsentUiPort();val session=UUID.randomUUID().toString();m.visible(session,true)
        val p=m.propose(session,"Mémoire publique de test",UiMemoryScope.GENERAL);m.decide(p,p.content,true)
        val r=m.visible(session,true).single();val preview=m.previewExport(session,setOf(r.reference))
        fails { m.approveExport(preview,"0".repeat(64)) };m.suspendRecord(session,r.reference,true)
        fails { m.approveExport(preview,preview.digest) }
        val suspended=m.visible(session,true).single();m.suspendRecord(session,suspended.reference,false)
        val current=m.visible(session,true).single();val next=m.previewExport(session,setOf(current.reference));m.revoke(session)
        fails { m.approveExport(next,next.digest) }
    }
    @Test fun voluntaryExportIsReadOnlyAndDoesNotImportMemories()=runTest {
        val m=LaboratoryMemoryConsentUiPort();val session=UUID.randomUUID().toString();m.visible(session,true)
        val p=m.propose(session,"Exemple public d’export",UiMemoryScope.GENERAL);m.decide(p,p.content,true)
        val before=m.visible(session,true);val preview=m.previewExport(session,before.map { it.reference }.toSet())
        val data=m.approveExport(preview,preview.digest).toString(Charsets.UTF_8)
        assertTrue(data.contains("UNTRUSTED_IMPORT_REQUIRES_FRESH_CONSENT"));assertEquals(before,m.visible(session,true))
        fails { m.approveExport(preview,preview.digest) }
    }
    @Test fun staleMemoryReferenceCannotDeleteOrSuspendNewerRecord()=runTest {
        val m=LaboratoryMemoryConsentUiPort();val session=UUID.randomUUID().toString();m.visible(session,true)
        val p=m.propose(session,"Exemple public",UiMemoryScope.GENERAL);m.decide(p,p.content,true)
        val ref=m.visible(session,true).single().reference;m.suspendRecord(session,ref,true)
        assertTrue(m.delete(session,ref) is MemoryUiAck.Refused);assertEquals(1,m.visible(session,true).size)
    }
}
