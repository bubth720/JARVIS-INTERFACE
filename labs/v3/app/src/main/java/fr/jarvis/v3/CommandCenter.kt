package fr.jarvis.v3

import android.Manifest
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Debug
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import fr.jarvis.v1.Phase
import fr.jarvis.v3.conv.ActionGuard
import fr.jarvis.v3.conv.ConvApp
import fr.jarvis.v3.conv.LocalKnowledge
import fr.jarvis.v3.model.Blueprint
import fr.jarvis.v3.model.CommandModule
import fr.jarvis.v3.model.ModuleStatus
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay

/**
 * Holographic Command Center V2 (PDF §6–§9): status bar + ten functional screens. Real data only (session,
 * engine metrics, Android measures, NEXUS state); modules that this build does not run are shown "à connecter"
 * with their reason. Static content: no decorative animation here (performance rule, voice and LLM first).
 */
@Composable
fun CommandCenter(app: ConvApp, gov: PerformanceGovernor, ui: V3UiState, actions: SceneActions) {
    var tab by remember { mutableStateOf(ui.commandTab) }
    LaunchedEffect(tab) { ui.commandTab = tab }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(2_000); tick++ } }   // live gauges, 0.5 Hz, only while this panel is shown
    val measure = remember(tick) { PerformanceGovernor.measure(app) }
    val s = ui.session?.takeIf { !it.closed }
    val offline = remember(tick) { (app.getSystemService(ConnectivityManager::class.java)).activeNetwork == null }
    // Top bar (§6): date/time, local/offline, battery, AI state.
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date()), style = Holo.tabular, modifier = Modifier.weight(1f))
        Text((if (offline) "HORS LIGNE" else "RÉSEAU ACTIF") + " · " + (measure.batteryPercent?.let { "$it %" } ?: "batterie ?") +
            " · IA " + (s?.phase?.name ?: "ARRÊTÉE"), style = Holo.label, modifier = Modifier.testTag("cc-status"))
    }
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (m in CommandModule.entries) Pill(m.label + if (m.status == ModuleStatus.TO_CONNECT) " ◌" else "", "cc-tab-${m.name.lowercase()}", active = tab == m) { tab = m }
    }
    Text("${tab.label} · ${tab.status.label.uppercase()}", style = Holo.label.copy(color = when (tab.status) {
        ModuleStatus.OPERATIONAL -> Holo.cyan; ModuleStatus.PARTIAL -> Holo.gold; ModuleStatus.TO_CONNECT -> Holo.muted }), modifier = Modifier.testTag("cc-module-status"))
    Text(tab.note, style = Holo.small)
    Spacer(Modifier.height(8.dp))
    when (tab) {
        CommandModule.AI_CORE -> AiCore(app, s, measure)
        CommandModule.VOICE -> VoiceCenter(s)
        CommandModule.MEMORY -> MemoryVault(app, ui)
        CommandModule.NEXUS -> NexusScreen(app, s, actions)
        CommandModule.SECURITY -> Security(app, measure, offline, ui)
        CommandModule.EVOLUTION -> EvolutionLab(s, gov)
        CommandModule.BLUEPRINT -> BlueprintScreen(app)
        CommandModule.MISSIONS, CommandModule.AGENTS, CommandModule.AUTOMATIONS -> ToConnect(tab)
    }
}

@Composable private fun Line(k: String, v: String, tag: String? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(k, style = Holo.small, modifier = Modifier.weight(0.45f))
        Text(v, style = Holo.tabular, modifier = Modifier.weight(0.55f).let { if (tag != null) it.testTag(tag) else it })
    }
}

/** Horizontal gauge: value/max, real figures only. */
@Composable private fun Gauge(label: String, value: Float, max: Float, text: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row { Text(label, style = Holo.small, modifier = Modifier.weight(1f)); Text(text, style = Holo.tabular) }
        Canvas(Modifier.fillMaxWidth().height(6.dp)) {
            drawRect(Holo.surface, size = size)
            val f = if (max > 0) (value / max).coerceIn(0f, 1f) else 0f
            drawRect(if (f > 0.85f) Holo.amber else Holo.cyan, size = Size(size.width * f, size.height))
        }
    }
}

/** Mini histogram of measured values (e.g. LLM latency per answer). */
@Composable private fun Bars(values: List<Long>, tag: String) {
    if (values.isEmpty()) { Text("Aucune mesure dans cette session.", style = Holo.small); return }
    val max = values.max().coerceAtLeast(1)
    Canvas(Modifier.fillMaxWidth().height(48.dp).testTag(tag)) {
        val w = size.width / values.size
        values.forEachIndexed { i, v -> val h = size.height * v / max
            drawRect(Holo.cyan.copy(alpha = 0.7f), topLeft = Offset(i * w + 1, size.height - h), size = Size(w - 2, h)) }
    }
}

@Composable private fun AiCore(app: ConvApp, s: fr.jarvis.v3.conv.ConvSession?, m: fr.jarvis.v3.model.SystemMeasure) {
    val entry = remember { app.assets.llm.current() }
    Line("Modèle", entry?.let { "Qwen3-0.6B Q4_K_M · ${it.sizeBytes / (1 shl 20)} Mio" } ?: "non installé", "cc-model")
    Line("Empreinte SHA-256", entry?.sha256?.take(16)?.let { "$it…" } ?: "—")
    Line("Moteur", "A02 scellé · contexte 768 · 1 thread · sortie 128")
    Line("État", s?.phase?.name ?: "session arrêtée")
    Line("Personnalité", app.personas.load().let { "${it.profileName} · ${if (it.tutoiement) "tutoiement" else "vouvoiement"}" })
    val metrics = s?.transport?.lastMetrics?.collectAsState()?.value
    if (metrics != null) {
        Line("Dernier tour : jetons d'entrée", "${metrics.promptTokens} (${metrics.historyPairsSent} échange(s) relu(s))")
        Line("Premier jeton / total", "${metrics.firstTokenMs} ms / ${metrics.totalMs} ms")
        Line("Vitesse de génération", "${metrics.decodeTokensPerSecond} jetons/s", "cc-tps")
    } else Text("Aucun tour mesuré pour l'instant.", style = Holo.small)
    Line("Instruction du dernier tour", s?.model?.lastInstruction?.let { "${it.length} caractères" } ?: "—")
    val pssMb = remember(m) { Debug.getPss() / 1024 }
    Gauge("Mémoire de l'application (PSS)", pssMb.toFloat(), (m.ramTotalMb ?: 0L).toFloat(), "$pssMb Mio")
    Gauge("Mémoire disponible du système", (m.ramAvailMb ?: 0L).toFloat(), (m.ramTotalMb ?: 0L).toFloat(), "${m.ramAvailMb ?: "?"} / ${m.ramTotalMb ?: "?"} Mio")
    Line("Thermique", m.thermal)
}

@Composable private fun VoiceCenter(s: fr.jarvis.v3.conv.ConvSession?) {
    if (s == null) { Text("Démarre une session pour la voix.", style = Holo.small); return }
    Line("Reconnaissance (whisper.cpp)", if (s.voiceInputAvailable) "prête" else s.sttUnavailable ?: "indisponible", "cc-stt")
    Line("Synthèse (Pocket TTS, voix alba)", s.ttsUnavailable ?: "prête")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Réponses parlées", style = Holo.body, modifier = Modifier.weight(1f))
        Switch(s.voiceOutputEnabled, { s.voiceOutputEnabled = it }, modifier = Modifier.testTag("cc-voice-output"), colors = SwitchDefaults.colors(checkedTrackColor = Holo.cyan))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Réponse d'attente (après 1,2 s)", style = Holo.body, modifier = Modifier.weight(1f))
        var q by remember { mutableStateOf(s.quickReactionEnabled) }
        Switch(q, { q = it; s.quickReactionEnabled = it }, modifier = Modifier.testTag("cc-quick-reaction"), colors = SwitchDefaults.colors(checkedTrackColor = Holo.cyan))
    }
    val voice = s.exchanges.filter { it.viaVoice }.takeLast(8)
    Line("Dernière écoute / transcription", voice.lastOrNull()?.let { "${it.captureMs} ms / ${it.sttMs} ms" } ?: "—")
    Line("Premier son de JARVIS", if (s.lastFirstSoundMs >= 0) "${s.lastFirstSoundMs} ms après la demande" else "—", "cc-first-sound")
    Text("Durée de transcription des derniers tours vocaux (ms)", style = Holo.small)
    Bars(voice.map { it.sttMs }, "cc-voice-bars")
    if (s.busy) Pill("Interrompre", "cc-interrupt") { s.interrupt() }
}

@Composable private fun MemoryVault(app: ConvApp, ui: V3UiState) {
    val s = ui.session?.takeIf { !it.closed }
    Text("MÉMOIRE TEMPORAIRE (cette conversation)", style = Holo.label)
    val facts = s?.let { remember(it.exchanges.size) { it.context.facts(it.transport.history.snapshot(), "").facts() } }.orEmpty()
    if (facts.isEmpty()) Text("Aucun fait énoncé dans cette conversation.", style = Holo.small)
    for (f in facts) Row(verticalAlignment = Alignment.CenterVertically) {
        Text("${f.kind.name.lowercase()} : ${f.value}", style = Holo.tabular, modifier = Modifier.weight(1f).testTag("cc-fact-${f.key}"))
        Pill("Mémoriser…", "cc-remember-${f.key}") { s?.proposeMemory(factSentence(f)) }
    }
    Text("Ces faits viennent uniquement de ce que tu as dit ; ils disparaissent avec la conversation.", style = Holo.small)
    s?.memoryProposal?.let { (_, shown) -> MemoryProposalCard(shown, { s.decideMemory(true) }, { s.decideMemory(false) }) }
    s?.memoryOutcome?.let { Text(it, style = Holo.small.copy(color = Holo.ice)) }
    Spacer(Modifier.height(8.dp)); Text("MÉMOIRE DURABLE (confirmée par toi)", style = Holo.label)
    var rev by remember { mutableIntStateOf(0) }
    val records = remember(rev, s?.memoryOutcome) { app.memory.list() }
    if (records.isEmpty()) Text("Aucun souvenir confirmé. Dis « souviens-toi que … » ou utilise « Mémoriser… ».", style = Holo.small)
    for (r in records) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("cc-memory")) {
        Text("« ${r.content} »", style = Holo.body, modifier = Modifier.weight(1f))
        Pill("Supprimer", "cc-memory-delete") { app.memory.delete(r); rev++ }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Utiliser mes souvenirs dans les réponses", style = Holo.body, modifier = Modifier.weight(1f))
        var on by remember { mutableStateOf(app.memory.recallEnabled) }
        Switch(on, { on = it; app.memory.recallEnabled = it }, modifier = Modifier.testTag("cc-memory-recall"), colors = SwitchDefaults.colors(checkedTrackColor = Holo.cyan))
    }
    Text("Stockage isolé, exclu des sauvegardes ; export chiffré : à connecter.", style = Holo.small)
    Spacer(Modifier.height(8.dp)); Text("HISTORIQUE LOCAL", style = Holo.label)
    val n = remember(s?.exchanges?.size) { runCatching { app.journal.list().size }.getOrDefault(0) }
    Line("Conversations enregistrées", "$n", "cc-journal-count")
    Text("Reprendre ou supprimer : Réglages › Historique local.", style = Holo.small)
}

/** The fact as the user's own sentence: this exact text is what the user confirms. */
private fun factSentence(f: fr.jarvis.v3.conv.WorkingMemory.Fact): String = when (f.kind) {
    fr.jarvis.v3.conv.WorkingMemory.Kind.NAME -> "Je m'appelle ${f.value}."
    fr.jarvis.v3.conv.WorkingMemory.Kind.AGE -> "J'ai ${f.value}."
    fr.jarvis.v3.conv.WorkingMemory.Kind.RELATIVE -> f.value.split(", ").let { v ->
        (if (f.key in setOf("sœur", "soeur", "fille", "mère", "mere", "femme", "amie")) "Ma" else "Mon") + " ${f.key} s'appelle ${v[0]}" + (v.getOrNull(1)?.let { " et a $it" } ?: "") + "." }
    fr.jarvis.v3.conv.WorkingMemory.Kind.LIKE -> "J'aime ${f.value}."
    fr.jarvis.v3.conv.WorkingMemory.Kind.APPOINTMENT -> "Rendez-vous : ${f.value}."
    fr.jarvis.v3.conv.WorkingMemory.Kind.PLACE -> "Lieu évoqué : ${f.value}."
}

@Composable private fun NexusScreen(app: ConvApp, s: fr.jarvis.v3.conv.ConvSession?, actions: SceneActions) {
    val st = app.knowledge.state
    Line("Moteur", when (st) { is LocalKnowledge.State.Ready -> "prêt · ${st.documents} document(s)"; is LocalKnowledge.State.Unavailable -> "indisponible : ${st.reason}" }, "cc-nexus-state")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Utiliser mes documents dans les réponses", style = Holo.body, modifier = Modifier.weight(1f))
        var on by remember { mutableStateOf(app.knowledge.enabled) }
        Switch(on, { on = it; app.knowledge.enabled = it }, modifier = Modifier.testTag("cc-nexus-enabled"), colors = SwitchDefaults.colors(checkedTrackColor = Holo.cyan))
    }
    Pill("Importer des documents (txt, md, csv, docx, xml)…", "cc-nexus-import") { actions.importFiles() }
    for (t in remember(st) { app.knowledge.titles() }) Text("• $t", style = Holo.body)
    s?.lastGrounding?.let { g -> Line("Dernière recherche", if (!g.searched) "non effectuée" else if (g.sources.isEmpty()) "aucun extrait (${g.ms} ms)" else "${g.sources.joinToString()} (${g.ms} ms)", "cc-nexus-last") }
    Text("Kiwix : non configuré.", style = Holo.small)
}

@Composable private fun Security(app: ConvApp, m: fr.jarvis.v3.model.SystemMeasure, offline: Boolean, ui: V3UiState) {
    val mic = app.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    val perms = remember { app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toList() }
    Line("Permission micro", if (mic) "accordée" else "non accordée (demandée au premier usage)", "cc-perm-mic")
    Line("Accès Internet", if (perms.none { it == Manifest.permission.INTERNET }) "aucun (permission absente de l'APK)" else "DÉCLARÉ", "cc-internet")
    Line("Réseau actuel", if (offline) "hors ligne" else "actif (JARVIS n'en a pas l'usage)")
    Line("Garde thermique", "refus d'un tour si l'état est sévère · actuel : ${m.thermal}")
    Line("Garde mémoire", "refus si la mémoire système est basse · libre : ${m.ramAvailMb ?: "?"} Mio")
    Line("Actions", "aucune exécutée ; garde « action non réalisée » active", "cc-action-guard")
    Text("« ${ActionGuard.NOTICE} »", style = Holo.small)
    Line("Sauvegarde Android", "désactivée (allowBackup=false)")
    Spacer(Modifier.height(8.dp))
    var confirm by remember { mutableStateOf(false) }
    if (!confirm) Pill("Effacer tout l'historique local…", "cc-erase") { confirm = true }
    else Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Pill("Confirmer l'effacement", "cc-erase-confirm", active = true) {
            app.closeSession(); ui.session = null
            app.journal.list().forEach { app.journal.delete(it.id) }; confirm = false; ui.message = "Historique local effacé."
        }
        Pill("Annuler", "cc-erase-cancel") { confirm = false }
    }
}

@Composable private fun EvolutionLab(s: fr.jarvis.v3.conv.ConvSession?, gov: PerformanceGovernor) {
    val st = gov.stats.summary()
    Line("Rendu mesuré", "${st["fps"] ?: "—"} images/s · p95 ${st["p95Ms"] ?: "—"} ms")
    Line("Profil de performance", modeLabel(gov.effectiveMode))
    Text("Latence du modèle par réponse (ms), cette session", style = Holo.small)
    Bars(s?.exchanges?.filter { it.llmMs > 0 }?.takeLast(12)?.map { it.llmMs }.orEmpty(), "cc-llm-bars")
    Text("Expériences isolées et correctifs : à connecter. Toute proposition exigera ton approbation avant application.", style = Holo.small)
}

@Composable private fun BlueprintScreen(app: ConvApp) {
    val nexus = app.knowledge.state is LocalKnowledge.State.Ready
    for (n in Blueprint.NODES) {
        val live = n.connected && (n.name != "NEXUS" || nexus)
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).glass(active = live, corner = 12.dp).padding(10.dp).testTag("cc-node-${n.name.lowercase().replace(' ', '-')}")) {
            Text("${n.name} · ${n.repository} @ ${n.commit}", style = Holo.label.copy(color = if (live) Holo.cyan else Holo.muted))
            Text(n.role + if (n.name == "NEXUS" && !nexus) " (moteur indisponible sur cet appareil)" else "", style = Holo.small)
        }
    }
    Text("Liens actifs : " + Blueprint.EDGES.joinToString(" · ") { "${it.first} → ${it.second}" }, style = Holo.small)
}

@Composable private fun ToConnect(m: CommandModule) {
    Column(Modifier.fillMaxWidth().glass(active = false, corner = 12.dp).padding(12.dp).testTag("cc-to-connect")) {
        Text("À CONNECTER", style = Holo.label.copy(color = Holo.gold))
        Text(m.note, style = Holo.body)
        Text("Rien n'est simulé ici : aucune donnée de démonstration n'est présentée comme réelle.", style = Holo.small)
    }
}
