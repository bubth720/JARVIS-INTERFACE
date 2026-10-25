package com.jarvis.interfaceapp.workspace

import com.jarvis.interfaceapp.domain.*
import com.jarvis.interfaceapp.storage.LayoutCodec
import com.jarvis.interfaceapp.ui.identity.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class WorkspaceTest {
    @get:Rule val temp=TemporaryFolder()
    private fun round(w:Workspace)=WorkspaceCodec.decode(WorkspaceCodec.encode(w))
    private fun refuses(block:()->Unit) { try {block();fail("Invalid data accepted")}catch(_:IllegalArgumentException){} }
    @Test fun defaultsRoundTripWithoutExternalDependencies() {assertEquals(Workspace(),round(Workspace()))}
    @Test fun completePreferencesAndLocalPersonaSurviveEncoding() {
        val p=ProfileDraft.preset(PersonaPreset.NEUTRAL).copy(overrides=mapOf(PersonaContext.WORK to PersonaParameters(90,10,20,false,PersonaStyle.PLAIN)),interests=setOf(SimulatedInterest.SCIENCE),rules=listOf(LexicalRule(LexicalToken.BONJOUR,LexicalToken.SALUT)))
        val w=Workspace(visual=VisualPreferences(LocalTheme.GRAPHITE,140,120,true,false,DisplayMode.EXTENDED,setOf(PanelId.SYSTEM)),persona=LocalPersona(p,80),layouts=mapOf("Travail" to defaultLayouts()),packages=mapOf(ModuleId.CORE to "com.example.core"))
        assertEquals(w,round(w))
    }
    @Test fun layoutCopiesKeepBothProfilesAndLocks() {
        val l=defaultLayouts().mapValues {(_,p)->p.map {it.copy(locked=true)}}
        assertEquals(l,round(Workspace(layouts=mapOf("Verrouillée" to l))).layouts.getValue("Verrouillée"))
    }
    @Test fun importsRejectCollisionWithoutChangingOriginal() {
        val original=WorkspaceCodec.exportLayouts(defaultLayouts())
        val bad=original.toString(Charsets.UTF_8).replaceFirst("\"x\":3","\"x\":0")
        refuses {WorkspaceCodec.importLayouts(bad.toByteArray())}
        assertEquals(defaultLayouts(),WorkspaceCodec.importLayouts(original))
    }
    @Test fun importsRejectFutureVersionAndOversize() {
        refuses {WorkspaceCodec.importLayouts("{\"version\":999}".toByteArray())}
        refuses {WorkspaceCodec.importLayouts(ByteArray(65_537))}
    }
    @Test fun workspaceUnknownVersionDoesNotBecomeDefaults() {
        refuses {WorkspaceCodec.decode(WorkspaceCodec.encode(Workspace()).toString(Charsets.UTF_8).replaceFirst("\"version\":1","\"version\":2").toByteArray())}
    }
    @Test fun duplicatesAndInvalidUtf8AreRefused() {
        refuses {WorkspaceCodec.strict("{\"a\":1,\"a\":2}".toByteArray())}
        try {WorkspaceCodec.strict(byteArrayOf(-1));fail()}catch(_:java.nio.charset.CharacterCodingException){}
    }
    @Test fun duplicatePanelAndMissingProfilesRejected() {
        val root=LayoutCodec.defaults().root!!
        val profiles=root.getValue("profiles").jsonObject
        val rows=profiles.getValue("compact").jsonArray
        val bad=JsonObject(root+("profiles" to JsonObject(profiles+("compact" to JsonArray(rows+rows.first())))))
        refuses {WorkspaceCodec.importLayouts(bad.toString().toByteArray())}
        refuses {validateLayouts(mapOf(DashboardProfile.COMPACT to initialPanels(DashboardProfile.COMPACT)))}
    }
    @Test fun historyRequiresConsentAndBounds() {
        refuses {Workspace(history=listOf(ArchivedMessage("a","private",true,true)))}
        refuses {Workspace(keepHistory=true,history=List(201){ArchivedMessage("$it","x",true,true)})}
        assertEquals(1,round(Workspace(keepHistory=true,history=listOf(ArchivedMessage("a","bonjour",true,true)))).history.size)
    }
    @Test fun visualBoundsAndNonEmptyDashboardEnforced() {
        refuses {VisualPreferences(textPercent=151)};refuses {VisualPreferences(visible=emptySet())};refuses {VisualPreferences(elementPercent=99)}
    }
    @Test fun profileNamesAndPackagesAreBounded() {
        assertFalse(validLayoutName(" "));assertFalse(validLayoutName("x\n"));assertFalse(validPackage("content://evil"));assertTrue(validPackage("com.jarvis.core"))
    }
    @Test fun installedAndSimulatedNeverMeanConnected()=runBlocking {
        ModuleId.entries.forEach {assertNotEquals(ModuleHealth.CONNECTED,UnavailableModuleAdapter(it).observe().health);assertNotEquals(ModuleHealth.CONNECTED,SimulatedModuleAdapter(it).observe().health)}
    }
    @Test fun localPreviewHonoursContextPolicyAndAddress() {
        val p=LocalPersona(ProfileDraft.preset(PersonaPreset.ATHENA).copy(defaults=PersonaParameters(80,100,100,false,PersonaStyle.ELEGANT)),90)
        assertTrue(preview(p,PersonaContext.WORK).contains("Vous"))
        val effective=ReadOnlyPersonalityPreview.create(p.profile,PersonaContext.URGENT).effective
        assertEquals(0,effective.humour);assertEquals(40,effective.initiative)
    }
    private suspend fun <T> store(file:File,block:suspend (WorkspaceRepository)->T):T {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        return try {block(DataStoreWorkspaceRepository({file},scope))}finally {scope.coroutineContext.job.cancelAndJoin()}
    }
    @Test fun datastoreReopenRestoresPersonaLayoutsVisualAndHistory()=runBlocking {
        val f=File(temp.newFolder(),"workspace.json")
        val wanted=Workspace(visual=VisualPreferences(theme=LocalTheme.DEEP_BLUE),persona=LocalPersona(detail=81),layouts=mapOf("Maison" to defaultLayouts()),keepHistory=true,history=listOf(ArchivedMessage("a","test",true,true)))
        store(f){assertEquals(Workspace(),it.load());it.change {wanted}}
        store(f){assertEquals(wanted,it.load())}
    }
    @Test fun corruptedDatastoreNeverOverwritesBytes()=runBlocking {
        val f=File(temp.newFolder(),"workspace.json");f.writeText("broken")
        store(f){try {it.load();fail()}catch(_:androidx.datastore.core.CorruptionException){}
            try {it.change{Workspace()};fail()}catch(_:androidx.datastore.core.CorruptionException){} }
        assertEquals("broken",f.readText())
    }
    @Test fun rootUnknownExtensionsRetained() {
        val raw=WorkspaceCodec.strict(WorkspaceCodec.encode(Workspace()))
        val encoded=WorkspaceCodec.encode(Workspace(persona=LocalPersona(detail=75)),JsonObject(raw+("futureExtension" to JsonPrimitive("keep"))))
        assertEquals("keep",WorkspaceCodec.strict(encoded).getValue("futureExtension").jsonPrimitive.content)
    }
    @Test fun rapidCommandsAreOrderedAndUnacknowledgedWriteNotDurable()=runTest {
        val gate=CompletableDeferred<Unit>();var value=Workspace()
        val repo=object:WorkspaceRepository {
            override suspend fun load()=value
            override suspend fun change(transform:(Workspace)->Workspace):Workspace {gate.await();value=transform(value);return value}
        }
        val c=WorkspaceController(repo,backgroundScope);runCurrent()
        c.change{it.copy(persona=it.persona.copy(detail=1))};c.change{it.copy(persona=it.persona.copy(detail=2))};runCurrent()
        assertTrue(c.state.value.saving);assertFalse(c.state.value.durable);assertEquals(50,c.state.value.value.persona.detail)
        gate.complete(Unit);runCurrent();assertEquals(2,c.state.value.value.persona.detail);assertTrue(c.state.value.durable)
    }
    @Test fun writeErrorPreservesLastAcknowledgedValue()=runTest {
        val c=WorkspaceController(object:WorkspaceRepository {
            override suspend fun load()=Workspace()
            override suspend fun change(transform:(Workspace)->Workspace):Workspace=throw java.io.IOException()
        },backgroundScope);runCurrent();c.change {it.copy(persona=LocalPersona(detail=1))};runCurrent()
        assertEquals(50,c.state.value.value.persona.detail);assertNotNull(c.state.value.error);assertFalse(c.state.value.durable)
    }
    @Test fun archiveIsOptInAndDeleteDoesNotComeBackFromPendingWrite()=runTest {
        var value=Workspace();val c=WorkspaceController(object:WorkspaceRepository {
            override suspend fun load()=value
            override suspend fun change(transform:(Workspace)->Workspace):Workspace {value=transform(value);return value}
        },backgroundScope);runCurrent()
        c.archive(listOf(ArchivedMessage("a","x",true,true)));runCurrent();assertTrue(c.state.value.value.history.isEmpty())
        c.change{it.copy(keepHistory=true)};c.archive(listOf(ArchivedMessage("a","x",true,true)));runCurrent();assertEquals(1,c.state.value.value.history.size)
        c.change{it.copy(keepHistory=false,history=emptyList())};c.archive(listOf(ArchivedMessage("b","late",true,true)));runCurrent();assertTrue(c.state.value.value.history.isEmpty())
    }

    @Test fun restoredLayoutIsNotPublishedBeforeItsWriteSucceeds()=runTest {
        val gate=CompletableDeferred<Unit>()
        val repo=object:LayoutRepository {
            override suspend fun load()=LoadedLayouts(defaultLayouts(),true)
            override suspend fun save(layouts:Layouts) {gate.await()}
            override suspend fun reset()=defaultLayouts()
        }
        val c=com.jarvis.interfaceapp.ui.DashboardController(repo,backgroundScope);runCurrent()
        val target=defaultLayouts().mapValues {(_,panels)->panels.map {it.copy(locked=true)}}
        c.restore(target);runCurrent();assertEquals(defaultLayouts(),c.state.value.home.layouts);assertFalse(c.state.value.storage.durable)
        gate.complete(Unit);runCurrent();assertEquals(target,c.state.value.home.layouts);assertTrue(c.state.value.storage.durable)
    }
    @Test fun invalidOrFailedRestorePreservesPreviousGeometry()=runTest {
        val repo=object:LayoutRepository {
            override suspend fun load()=LoadedLayouts(defaultLayouts(),true)
            override suspend fun save(layouts:Layouts) {throw java.io.IOException()}
            override suspend fun reset()=defaultLayouts()
        }
        val c=com.jarvis.interfaceapp.ui.DashboardController(repo,backgroundScope);runCurrent()
        c.restore(emptyMap());runCurrent();assertEquals(defaultLayouts(),c.state.value.home.layouts)
        c.restore(defaultLayouts().mapValues {(_,panels)->panels.map{it.copy(locked=true)}});runCurrent()
        assertEquals(defaultLayouts(),c.state.value.home.layouts);assertEquals(com.jarvis.interfaceapp.ui.StorageFailure.RESET,c.state.value.storage.failure)
    }

    @Test fun concurrentVisualFieldsDoNotRevertPreviousChoice() {
        val before=VisualPreferences();val theme=before.copy(theme=LocalTheme.GRAPHITE)
        val next=mergeVisual(theme,before,before.copy(reduceMotion=true))
        assertEquals(LocalTheme.GRAPHITE,next.theme);assertTrue(next.reduceMotion)
    }
    @Test fun attributedHistoryRoundTripsWithoutResolvingSources() {
        val sources=listOf(DocumentSource("Document fourni","Extrait public"))
        val w=Workspace(keepHistory=true,history=listOf(ArchivedMessage("a","test",false,true,sources)))
        assertEquals(sources,round(w).history.single().sources)
    }

    @Test fun allStyleControlsAffectTemplatesAndUrgencySuppressesHumour() {
        val base=LocalPersona();val baseline=preview(base,PersonaContext.DAILY)
        val p=base.profile.defaults
        listOf(p.copy(frankness=0),p.copy(humour=0),p.copy(initiative=0),p.copy(tutoiement=false),p.copy(style=PersonaStyle.PLAIN)).forEach {
            assertNotEquals(baseline,preview(base.copy(profile=base.profile.copy(defaults=it)),PersonaContext.DAILY))
        }
        assertNotEquals(baseline,preview(base.copy(detail=90),PersonaContext.DAILY))
        assertFalse(preview(base,PersonaContext.URGENT).contains("circuits"))
    }
}
