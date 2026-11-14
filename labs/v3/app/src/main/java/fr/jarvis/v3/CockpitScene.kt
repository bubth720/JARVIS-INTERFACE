package fr.jarvis.v3

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import fr.jarvis.m55d.CockpitState
import fr.jarvis.m55d.RenderMode
import fr.jarvis.v1.Exchange
import fr.jarvis.v1.Phase
import fr.jarvis.v3.conv.ConvApp
import fr.jarvis.v3.conv.ConvSession
import fr.jarvis.v3.conv.PersonaSettings
import fr.jarvis.v3.model.*
import kotlinx.coroutines.delay

/** A typed device question answered from a real Android measurement (never by the language model). */
data class SystemNote(val afterExchanges: Int, val question: String, val answer: String, val cardId: String)

enum class Panel { NONE, KNOWLEDGE, SETTINGS, COMMAND }

/** Process-scoped UI state: survives rotation and activity recreation (§03 "orientation changée"). */
class V3UiState {
    var session by mutableStateOf<ConvSession?>(null)
    val cards = mutableStateListOf<ContextCard>()
    var openCard by mutableStateOf<String?>(null)
    var expanded by mutableStateOf(false)
    val pinned = mutableStateListOf<String>()
    val notes = mutableStateListOf<SystemNote>()
    var panel by mutableStateOf(Panel.NONE)
    var menuOpen by mutableStateOf(false)
    var typed by mutableStateOf("")
    var pending by mutableStateOf<String?>(null)
    var message by mutableStateOf("")
    var errorSince by mutableStateOf<Long?>(null)
    /** When the last written answer appeared without speech (drives the short answer animation). */
    var answeredAt by mutableStateOf<Long?>(null)
    var anchor by mutableStateOf(Offset.Zero)
    /** Conversation to resume once the previous native engine is released (one engine per process). */
    var resumeRequest by mutableStateOf<String?>(null)
    /** Last Command Center screen (kept across rotation). */
    var commandTab by mutableStateOf(fr.jarvis.v3.model.CommandModule.AI_CORE)
    /** Persona avatar shown (owner setting, persisted in app preferences). */
    var showAvatar by mutableStateOf(true)
    private var seq = 0
    fun nextId() = "c${++seq}"
    fun card(id: String?) = cards.firstOrNull { it.id == id }
    fun show(card: ContextCard) { if (cards.none { it.id == card.id }) cards += card; openCard = card.id; expanded = false; panel = Panel.NONE }
}

object V3Runtime {
    val ui = V3UiState()
    val motion = SphereMotion()
    @Volatile var governor: PerformanceGovernor? = null
}

interface SceneActions {
    fun importFiles(); fun searchImportFolder(); fun requestMicThenTalk(); fun openGallery()
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CockpitScene(app: ConvApp, gov: PerformanceGovernor, actions: SceneActions) {
    val ui = V3Runtime.ui
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val resumed = lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    LaunchedEffect(resumed) { if (resumed) gov.refresh() }
    val s = ui.session?.takeIf { !it.closed }
    LaunchedEffect(ui.resumeRequest) {
        val id = ui.resumeRequest ?: return@LaunchedEffect
        val until = SystemClock.elapsedRealtime() + 30_000
        while (!app.previousReleased && SystemClock.elapsedRealtime() < until) delay(100)   // one native engine per process
        ui.notes.clear(); ui.resumeRequest = null
        runCatching { app.startSession(id) }.onSuccess { ui.session = it; ui.message = "" }.onFailure { ui.message = it.message ?: "Erreur" }
    }

    // ---- observable engine state -> visual state (the graphics never command the engine) ----
    val cockpitState = s?.cockpit?.state
    LaunchedEffect(cockpitState) { if (cockpitState == CockpitState.ERROR) ui.errorSince = SystemClock.elapsedRealtime() else if (cockpitState != null) ui.errorSince = null }
    var clockTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(ui.errorSince) { if (ui.errorSince != null) { delay(SphereStateAdapter.ERROR_ACCENT_MS + 50); clockTick++ } }
    LaunchedEffect(ui.answeredAt) { if (ui.answeredAt != null) { delay(SphereStateAdapter.REVEAL_MS + 50); clockTick++ } }
    clockTick.let { }
    val now = SystemClock.elapsedRealtime()
    val errorAge = ui.errorSince?.let { now - it }
    val holo = SphereStateAdapter.resolve(s?.phase?.name, s != null && s.phase == Phase.THINKING && s.partial.isNotBlank(), errorAge,
        ui.answeredAt?.let { now - it })
    // Owner rule (14/11): the sphere is still while listening; the microphone level only drives the dock visualiser.
    val micLevel = if (holo == HoloState.LISTENING) (s?.cockpit?.level ?: 0f) else 0f
    val level = if (holo == HoloState.SPEAKING) (s?.cockpit?.level ?: 0f) else 0f
    var pulse by remember { mutableIntStateOf(0) }
    LaunchedEffect(s?.partial) { if (!s?.partial.isNullOrBlank()) pulse++ }
    val last = s?.exchanges?.lastOrNull()
    val keywords = remember(last) { last?.let { Keywords.extract(it.user, it.answer) } ?: emptyList() }

    // A spoken device question: the model is not a measurement, so the real measure is added as a card.
    val exchangeCount = s?.exchanges?.size ?: 0
    LaunchedEffect(exchangeCount) {
        ui.pending = null                                   // the finished turn is now in the history: never shown twice
        val e = s?.exchanges?.lastOrNull() ?: return@LaunchedEffect
        if (exchangeCount <= s.restoredCount) return@LaunchedEffect    // restored from the journal: not a new answer
        if (e.ttsSamples == 0L && e.answer.isNotBlank()) ui.answeredAt = SystemClock.elapsedRealtime()   // written answer: brief animation
        val topic = if (e.viaVoice) Intents.systemTopic(e.user) else null
        if (topic != null) {
            val card = CardFactory.system(ui.nextId(), topic, PerformanceGovernor.measure(app))
            ui.notes += SystemNote(exchangeCount, e.user, card.summary + " (mesure Android ; la réponse vocale du modèle n'est pas une mesure)", card.id)
            ui.show(card)
        }
    }

    fun send() {
        val text = ui.typed.trim(); if (text.isEmpty() || s == null) return
        val topic = Intents.systemTopic(text)
        if (topic != null) {
            val card = CardFactory.system(ui.nextId(), topic, PerformanceGovernor.measure(app))
            ui.notes += SystemNote(s.exchanges.size, text, card.summary, card.id); ui.show(card); ui.typed = ""; return
        }
        if (s.textTurn(text) != null) { ui.pending = text; ui.typed = "" }
    }
    fun cardAction(card: ContextCard, id: String) {
        when (id) {
            "explore" -> { val p = card.explorePrompt; if (p != null && s != null && s.textTurn(p) != null) { ui.pending = p; ui.openCard = null } }
            "back" -> ui.openCard = null
        }
    }
    fun destination(d: Destination) {
        ui.menuOpen = false
        when (d) {
            Destination.COCKPIT -> { ui.openCard = null; ui.panel = Panel.NONE }
            Destination.KNOWLEDGE -> { ui.openCard = null; ui.panel = Panel.KNOWLEDGE }
            Destination.SETTINGS -> { ui.openCard = null; ui.panel = Panel.SETTINGS }
            Destination.MEMORY -> { ui.openCard = null; ui.commandTab = fr.jarvis.v3.model.CommandModule.MEMORY; ui.panel = Panel.COMMAND }
            Destination.COMMAND -> { ui.openCard = null; ui.panel = Panel.COMMAND }
        }
    }
    BackHandler(enabled = !ui.menuOpen && (ui.openCard != null || ui.panel != Panel.NONE)) { if (ui.openCard != null) ui.openCard = null else ui.panel = Panel.NONE }

    val keyboard = LocalSoftwareKeyboardController.current
    val detail = gov.detail
    // V3-FIX-01: still at rest / listening / thinking; animated only while JARVIS answers; never manipulable.
    val animate = gov.animate(resumed) && SphereStateAdapter.animates(holo)
    val sphere: @Composable (Modifier) -> Unit = { m ->
        HologramSphere(holo, level, pulse, keywords, detail, animate, V3Runtime.motion, gov::onVsync,
            fpsCap = SphereStateAdapter.fpsCap(holo, detail.fps), modifier = m)
    }
    val dock: @Composable (Modifier) -> Unit = { m ->
        ConversationDock(ui.typed, { ui.typed = it }, { keyboard?.hide(); send() }, sendEnabled = ui.typed.isNotBlank() && s?.phase == Phase.READY,
            micEnabled = s != null && s.voiceInputAvailable && (s.phase == Phase.READY || s.phase == Phase.SPEAKING), listening = s?.phase == Phase.LISTENING,
            onMic = { if (s != null && s.phase == Phase.LISTENING) s.interrupt() else actions.requestMicThenTalk() }, busy = s?.busy == true, onStop = { s?.interrupt() },
            level = if (holo == HoloState.LISTENING) micLevel else level, menuOpen = ui.menuOpen, onMenu = { ui.menuOpen = !ui.menuOpen }, onAnchor = { ui.anchor = it }, modifier = m)
    }
    val openCard = ui.card(ui.openCard)
    val cardView: @Composable (Modifier) -> Unit = { m ->
        ContextCardHost(openCard, ui.expanded, openCard != null && openCard.id in ui.pinned,
            onAction = { id -> openCard?.let { cardAction(it, id) } },
            onPin = { openCard?.let { c -> if (c.id in ui.pinned) ui.pinned.remove(c.id) else ui.pinned.add(c.id) } },
            onExpand = { ui.expanded = !ui.expanded }, onClose = { ui.openCard = null }, modifier = m)
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Holo.background).semantics { testTagsAsResourceId = true }) {
        Canvas(Modifier.fillMaxSize()) { hudBackdrop() }
        val landscape = maxWidth > maxHeight
        val heroHeight = maxHeight * 0.40f
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 12.dp)) {
            Header(holo, gov, s)
            if (s == null) { Setup(app, actions, sphere, Modifier.weight(1f)); return@Column }
            if (!landscape) {
                Box(Modifier.fillMaxWidth().height(heroHeight)) {
                    sphere(Modifier.fillMaxSize())
                }
                KeywordChips(keywords) { k -> ui.show(CardFactory.concept(ui.nextId(), k, s.exchanges.map { it.answer })) }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    Conversation(s, ui, Modifier.fillMaxSize())
                    cardView(Modifier.align(Alignment.BottomEnd).fillMaxWidth(if (ui.expanded) 1f else 0.94f).fillMaxHeight(if (ui.expanded) 1f else 0.92f))
                }
                dock(Modifier.padding(top = 6.dp))
            } else {
                Row(Modifier.weight(1f)) {
                    Spacer(Modifier.weight(0.16f)) // free region reserved for a future extension (no avatar in V3)
                    Column(Modifier.weight(0.48f).fillMaxHeight()) {
                        Box(Modifier.weight(0.55f).fillMaxWidth()) { sphere(Modifier.fillMaxSize()) }
                        KeywordChips(keywords) { k -> ui.show(CardFactory.concept(ui.nextId(), k, s.exchanges.map { it.answer })) }
                        Conversation(s, ui, Modifier.weight(0.45f).fillMaxWidth())
                        dock(Modifier.padding(top = 4.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(0.36f).fillMaxHeight()) {
                        if (openCard == null) Text("Aucune fiche ouverte", style = Holo.small, modifier = Modifier.align(Alignment.Center))
                        cardView(Modifier.fillMaxSize())
                    }
                }
            }
        }
        if (s != null) {
            PanelSheet(ui.panel == Panel.KNOWLEDGE, "Connaissances de la session", "panel-knowledge", { ui.panel = Panel.NONE }) { KnowledgeList(ui) }
            PanelSheet(ui.panel == Panel.SETTINGS, "Réglages", "panel-settings", { ui.panel = Panel.NONE }) { SettingsPanel(app, gov, ui, actions) }
            PanelSheet(ui.panel == Panel.COMMAND, "Command Center", "panel-command", { ui.panel = Panel.NONE }) { CommandCenter(app, gov, ui, actions) }
        }
        val a11y = remember { (app.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager).isTouchExplorationEnabled }
        RadialMenuOverlay(ui.menuOpen, ui.anchor, a11y, onDismiss = { ui.menuOpen = false }, onSelect = ::destination)
    }
}

@Composable
private fun Header(holo: HoloState, gov: PerformanceGovernor, s: ConvSession?) {
    val ui = V3Runtime.ui
    Row(Modifier.fillMaxWidth().height(if (ui.showAvatar) 60.dp else 40.dp), verticalAlignment = Alignment.CenterVertically) {
        // Persona avatar zone (Command Center V2 §2): still unless JARVIS answers; mouth follows the real voice level.
        if (ui.showAvatar) { AvatarView(holo, if (holo == HoloState.SPEAKING) (s?.cockpit?.level ?: 0f) else 0f, 56.dp); Spacer(Modifier.width(8.dp)) }
        Text("J A R V I S", style = Holo.label.copy(color = Holo.ice), modifier = Modifier.weight(1f))
        Box(Modifier.size(8.dp).clip(CircleShape).background(Holo.stateColor(holo)))
        Spacer(Modifier.width(6.dp))
        Text(if (s == null) "LOCAL · HORS LIGNE" else "${holo.label.uppercase()} · LOCAL", style = Holo.label, modifier = Modifier.testTag("state-chip"),
            onTextLayout = { TextFit.report("state-chip", it.hasVisualOverflow) })
    }
}

fun modeLabel(m: RenderMode) = when (m) { RenderMode.ECONOMY -> "Économie"; RenderMode.BALANCED -> "Équilibré"; RenderMode.CINEMATIC -> "Cinématique" }

@Composable
private fun KeywordChips(keywords: List<String>, onKeyword: (String) -> Unit) {
    if (keywords.isEmpty()) return
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text("SUJETS", style = Holo.label.copy(color = Holo.muted))
        keywords.forEachIndexed { i, k -> Pill(k, "keyword-$i") { onKeyword(k) } }
    }
}

/** Response zone: compact, scrollable, each message shown once (the running answer replaces nothing). */
@Composable
private fun Conversation(s: ConvSession, ui: V3UiState, modifier: Modifier) {
    val items = buildList<Any> {
        s.exchanges.forEachIndexed { i, e -> ui.notes.filter { it.afterExchanges == i }.forEach { add(it) }; add(i to e) }
        ui.notes.filter { it.afterExchanges >= s.exchanges.size }.forEach { add(it) }
    }
    val list = rememberLazyListState()
    val streaming = s.busy && s.partial.isNotBlank()
    LaunchedEffect(items.size, streaming, s.phase) { val n = items.size + (if (s.busy) 1 else 0); if (n > 0) list.animateScrollToItem(n - 1) }
    LazyColumn(modifier.glass(active = false).padding(horizontal = 12.dp, vertical = 8.dp), state = list) {
        itemsIndexed(items) { idx, it ->
            when (it) {
                is SystemNote -> Message(it.question, it.answer, "MESURE ANDROID", idx == items.lastIndex, "Fiche") { ui.openCard = it.cardId; ui.expanded = false }
                is Pair<*, *> -> { val e = it.second as Exchange; val i = it.first as Int; val restored = i < s.restoredCount
                    val cited = s.sources[i].orEmpty()
                    Message(e.user + if (e.viaVoice) "  🎤" else "", e.answer.ifBlank { e.error ?: "(interrompu)" },
                        (if (restored) "JARVIS · échange précédent (historique local)" else
                        "JARVIS · LLM ${"%.1f".format(e.llmMs / 1000.0)} s" + (e.error?.let { " · $it" } ?: "")) +
                        (if (cited.isNotEmpty()) "\nSources locales : " + cited.joinToString(" · ") else "") +
                        (s.notices[i]?.let { "\n⚠ SYSTÈME : $it" } ?: ""), idx == items.lastIndex && !s.busy, "Fiche") {
                        ui.show(CardFactory.fromExchange(ui.nextId(), e.user, e.answer, e.llmMs, e.viaVoice)) } }
            }
        }
        if (s.busy) item {
            // Only the RUNNING turn: the typed question (cleared once in the history) and text streamed in THIS turn.
            // After a voice error V1 keeps the previous `partial`: it is never shown again (V3-FIX-01).
            val live = s.phase == Phase.THINKING && s.partial.isNotBlank()
            Column(Modifier.padding(vertical = 6.dp)) {
                ui.pending?.let { Text(it, style = Holo.small.copy(color = Holo.muted), modifier = Modifier.testTag("pending")) }
                // The vocal waiting phrase is labelled as such: it is never presented as the answer.
                if (!live) s.reaction?.let { Text("Réaction vocale : « $it »", style = Holo.small.copy(color = Holo.muted), modifier = Modifier.testTag("reaction")) }
                Text(if (live) s.partial else s.status, style = Holo.body.copy(color = if (live) Holo.text else Holo.muted),
                    modifier = Modifier.testTag(if (live) "partial" else "busy-status"))
            }
        }
        if (items.isEmpty() && !s.busy && s.memoryProposal == null) item { Text(s.status, style = Holo.small, modifier = Modifier.testTag("status")) }
        s.memoryProposal?.let { (_, shown) -> item { MemoryProposalCard(shown, { s.decideMemory(true) }, { s.decideMemory(false) }) } }
        s.memoryOutcome?.let { o -> item { Text(o, style = Holo.small.copy(color = Holo.ice), modifier = Modifier.testTag("memory-outcome")) } }
    }
}

/** Consent card (CORE workflow): the EXACT text that will be stored, confirmed or refused by the user. */
@Composable
fun MemoryProposalCard(shown: String, onAccept: () -> Unit, onReject: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).glass(active = true, corner = 12.dp).padding(10.dp).testTag("memory-proposal")) {
        Text("SOUVENIR PROPOSÉ · RIEN N'EST ENREGISTRÉ SANS TON ACCORD", style = Holo.label)
        Text("« $shown »", style = Holo.body, modifier = Modifier.testTag("memory-proposal-text"))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill("Mémoriser", "memory-accept", active = true, onClick = onAccept)
            Pill("Ne pas mémoriser", "memory-reject", onClick = onReject)
        }
    }
}

@Composable
private fun Message(question: String, answer: String, meta: String, latest: Boolean, cardLabel: String, onCard: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(question, style = Holo.small.copy(color = Holo.muted))
        Text(answer, style = Holo.body, modifier = if (latest) Modifier.testTag("answer") else Modifier)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(meta, style = Holo.tabular.copy(color = Holo.muted), modifier = Modifier.weight(1f),
                onTextLayout = { TextFit.report("meta", it.hasVisualOverflow) })
            Pill(cardLabel, if (latest) "card-open-latest" else "card-open") { onCard() }
        }
    }
}

@Composable
private fun PanelSheet(visible: Boolean, title: String, tag: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    AnimatedVisibility(visible, enter = slideInVertically(tween(Holo.Motion.CARD)) { it / 3 } + fadeIn(tween(Holo.Motion.CARD)),
        exit = slideOutVertically(tween(Holo.Motion.NORMAL)) { it / 3 } + fadeOut(tween(Holo.Motion.NORMAL))) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp), contentAlignment = Alignment.BottomCenter) {
            Column(Modifier.fillMaxWidth().fillMaxHeight(0.78f).glass(active = true, opaque = true).padding(16.dp).testTag(tag)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = Holo.title, modifier = Modifier.weight(1f))
                    GlyphButton("Fermer le panneau", "$tag-close", onClick = onClose) { HoloIcons.close(this, it) }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { content() }
            }
        }
    }
}

@Composable
private fun KnowledgeList(ui: V3UiState) {
    if (ui.cards.isEmpty()) Text("Aucune fiche dans cette session. Touchez un sujet de la sphère ou « Fiche » sous une réponse.", style = Holo.small)
    for (c in ui.cards.reversed()) {
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).glass(active = false, corner = 12.dp).padding(10.dp)) {
            Text("${c.kind.category.uppercase()}${if (c.id in ui.pinned) " · ÉPINGLÉE" else ""}", style = Holo.label)
            Text(c.title, style = Holo.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(c.provenance, style = Holo.small)
            Pill("Ouvrir", "knowledge-open-${c.id}") { ui.show(c) }
        }
    }
}

@Composable
private fun SettingsPanel(app: ConvApp, gov: PerformanceGovernor, ui: V3UiState, actions: SceneActions) {
    Text("RENDU", style = Holo.label); Spacer(Modifier.height(6.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Pill("Auto", "mode-auto", active = gov.automatic) { gov.auto() }
        for (m in listOf(RenderMode.ECONOMY, RenderMode.BALANCED, RenderMode.CINEMATIC)) Pill(modeLabel(m), "mode-${m.name.lowercase()}", active = !gov.automatic && gov.mode == m) { gov.force(m) }
    }
    Text("Profil effectif : ${modeLabel(gov.effectiveMode)}${if (gov.lowPower) " (économie d'énergie ou batterie faible)" else ""}" +
        (if (gov.reducedMotion) " · animations réduites par Android" else ""), style = Holo.small)
    val st = gov.stats.summary()
    Text("Mesuré sur cet appareil : ${st["fps"] ?: "—"} images/s, p95 ${st["p95Ms"] ?: "—"} ms (${st["frames"]} images)", style = Holo.tabular)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
        Text("Sphère statique (GPU, chaleur, accessibilité)", style = Holo.body, modifier = Modifier.weight(1f))
        Switch(gov.staticMode, { gov.staticMode = it }, modifier = Modifier.testTag("static-mode"), colors = SwitchDefaults.colors(checkedTrackColor = Holo.cyan))
    }
    Spacer(Modifier.height(8.dp)); Text("RESSOURCES LOCALES", style = Holo.label)
    ResourceLines(app)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
        Pill("Importer des fichiers…", "settings-import") { actions.importFiles() }
        Pill("Rechercher", "settings-search") { actions.searchImportFolder() }
    }
    if (ui.message.isNotBlank()) Text(ui.message, style = Holo.small.copy(color = Holo.text))
    Spacer(Modifier.height(8.dp)); Text("PROTOTYPE", style = Holo.label)
    Pill("Galerie des états (exemple, sans données réelles)", "open-gallery") { actions.openGallery() }
    ui.session?.let { cs ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
            Text("Réponses parlées", style = Holo.body, modifier = Modifier.weight(1f))
            Switch(cs.voiceOutputEnabled, { cs.voiceOutputEnabled = it }, modifier = Modifier.testTag("voice-output"), colors = SwitchDefaults.colors(checkedTrackColor = Holo.cyan))
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
        Text("Avatar d'Athéna (portrait 2D original)", style = Holo.body, modifier = Modifier.weight(1f))
        Switch(ui.showAvatar, { ui.showAvatar = it; app.getSharedPreferences("ui", android.content.Context.MODE_PRIVATE).edit().putBoolean("avatar", it).apply() },
            modifier = Modifier.testTag("avatar-toggle"), colors = SwitchDefaults.colors(checkedTrackColor = Holo.cyan))
    }
    Spacer(Modifier.height(8.dp)); PersonaSection(app)
    Spacer(Modifier.height(8.dp)); HistorySection(app, ui)
    Spacer(Modifier.height(8.dp)); Text("SESSION", style = Holo.label)
    Pill("Fermer la session et libérer la mémoire", "close-session") { ui.panel = Panel.NONE; ui.openCard = null; app.closeSession(); ui.session = null }
}

/** Structured, persistent personality (CONVERSATION-01 §3): the owner's values, applied from the next turn. */
@Composable
private fun PersonaSection(app: ConvApp) {
    var p by remember { mutableStateOf(app.personas.load()) }
    fun set(n: PersonaSettings) { p = n; app.applyPersona(n) }
    Text("PERSONNALITÉ", style = Holo.label); Spacer(Modifier.height(6.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Pill("Athéna (chaleureuse, malicieuse)", "persona-athena", active = p.profileName == PersonaSettings.ATHENA.profileName) { set(PersonaSettings.ATHENA.copy(tutoiement = p.tutoiement)) }
        Pill("Neutre (sobre)", "persona-neutral", active = p.profileName == PersonaSettings.NEUTRAL.profileName) { set(PersonaSettings.NEUTRAL.copy(tutoiement = p.tutoiement)) }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Text("Tutoiement", style = Holo.body, modifier = Modifier.weight(1f))
        Switch(p.tutoiement, { set(p.copy(tutoiement = it)) }, modifier = Modifier.testTag("persona-tutoiement"), colors = SwitchDefaults.colors(checkedTrackColor = Holo.cyan))
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Text("Humour léger", style = Holo.body, modifier = Modifier.weight(1f))
        Switch(p.humour >= 50, { set(p.copy(humour = if (it) 60 else 0)) }, modifier = Modifier.testTag("persona-humour"), colors = SwitchDefaults.colors(checkedTrackColor = Holo.cyan))
    }
    Text("JARVIS garde son nom ; la personnalité règle le ton. Valeurs : franchise ${p.frankness}, humour ${p.humour}, initiative ${p.initiative}.", style = Holo.small)
}

/** Local conversation history: list, resume, delete (app-private files, never exported). */
@Composable
private fun HistorySection(app: ConvApp, ui: V3UiState) {
    var tick by remember { mutableIntStateOf(0) }
    val conversations = remember(tick, ui.session?.exchanges?.size) { runCatching { app.journal.list() }.getOrDefault(emptyList()) }
    Text("HISTORIQUE LOCAL", style = Holo.label)
    if (conversations.isEmpty()) Text("Aucune conversation enregistrée.", style = Holo.small)
    for (c in conversations.take(8)) {
        val current = c.id == ui.session?.conversationId
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).glass(active = current, corner = 12.dp).padding(10.dp)) {
            Text((if (current) "EN COURS · " else "") + "${c.entries.size} échange(s)", style = Holo.label)
            Text(c.entries.firstOrNull()?.user.orEmpty(), style = Holo.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!current) Pill("Reprendre", "history-resume-${c.id}") {
                    ui.openCard = null; app.closeSession(); ui.session = null; ui.panel = Panel.NONE
                    ui.message = "Reprise après libération de la session…"; ui.resumeRequest = c.id
                }
                Pill("Supprimer", "history-delete-${c.id}") {
                    if (current) { ui.openCard = null; app.closeSession(); ui.session = null; ui.panel = Panel.NONE }
                    app.journal.delete(c.id); tick++
                }
            }
        }
    }
}

@Composable
private fun ResourceLines(app: ConvApp) {
    for ((name, ok) in listOf("Modèle de langage (obligatoire)" to app.assets.llmReady(), "Reconnaissance vocale (optionnelle : micro)" to (app.assets.sttFile() != null),
            "Voix Pocket (optionnelle : réponse parlée)" to app.assets.ttsPresent()))
        Text((if (ok) "✓  " else "✗  ") + name, style = Holo.body.copy(color = if (ok) Holo.ice else Holo.muted))
}

@Composable
private fun Setup(app: ConvApp, actions: SceneActions, sphere: @Composable (Modifier) -> Unit, modifier: Modifier) {
    val ui = V3Runtime.ui
    Column(modifier) {
        Box(Modifier.fillMaxWidth().weight(0.45f)) { sphere(Modifier.fillMaxSize()) }
        Column(Modifier.fillMaxWidth().weight(0.55f).glass(active = true).padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("Premier lancement", style = Holo.title)
            Text("Tout fonctionne sur l'appareil, sans Internet. Importez au moins le modèle de langage ; chaque fichier est vérifié par empreinte SHA-256.", style = Holo.small)
            Spacer(Modifier.height(8.dp))
            ResourceLines(app)
            if (app.missing().isEmpty() && app.optionalMissing().isNotEmpty())
                Text("Sans les fichiers optionnels, JARVIS répond par écrit.", style = Holo.small)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                Pill("Importer des fichiers…", "import") { actions.importFiles() }
                Pill("Rechercher", "search") { actions.searchImportFolder() }
            }
            Text("Dossier d'import : ${app.importFolder()}", style = Holo.small)
            if (ui.message.isNotBlank()) Text(ui.message, style = Holo.small.copy(color = Holo.text), modifier = Modifier.testTag("message"))
            Spacer(Modifier.height(8.dp))
            val latest = remember(ui.session) { runCatching { app.journal.latest() }.getOrNull() }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill("Démarrer JARVIS", "start", enabled = app.missing().isEmpty(), active = true) {
                    runCatching { app.startSession() }.onSuccess { ui.session = it; ui.message = "" }.onFailure { ui.message = it.message ?: "Erreur" }
                }
                if (latest != null) Pill("Reprendre la dernière conversation", "resume", enabled = app.missing().isEmpty()) {
                    runCatching { app.startSession(latest.id) }.onSuccess { ui.session = it; ui.message = "" }.onFailure { ui.message = it.message ?: "Erreur" }
                }
            }
            if (latest != null) Text("Dernière : « ${latest.entries.lastOrNull()?.user.orEmpty().take(60)} » (${latest.entries.size} échange(s))", style = Holo.small)
        }
    }
}
