package fr.jarvis.v3

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import fr.jarvis.v1.Phase
import fr.jarvis.v3.conv.ConvApp
import fr.jarvis.v3.model.CommandModule
import fr.jarvis.v3.model.PaletteCommand

/** Command Palette (Command Center V2 §8): search, then run a REAL command; unavailable ones say why. */
@Composable
fun PaletteSheet(app: ConvApp, ui: V3UiState, actions: SceneActions) {
    var q by remember { mutableStateOf("") }
    val s = ui.session?.takeIf { !it.closed }
    BasicTextField(q, { q = it }, singleLine = true, textStyle = Holo.body, cursorBrush = SolidColor(Holo.cyan),
        modifier = Modifier.fillMaxWidth().glass(active = true, corner = 12.dp).padding(12.dp).testTag("palette-input"),
        decorationBox = { inner -> Box { if (q.isEmpty()) Text("Rechercher une commande…", style = Holo.body.copy(color = Holo.muted)); inner() } })
    Spacer(Modifier.height(8.dp))
    val latest = remember { runCatching { app.journal.latest() }.getOrNull() }
    for (c in PaletteCommand.search(q)) {
        val why: String? = when (c) {
            PaletteCommand.TALK -> if (s == null) "démarre d'abord une session" else if (!s.voiceInputAvailable) (s.sttUnavailable ?: "micro indisponible") else if (s.phase != Phase.READY) "JARVIS est occupé" else null
            PaletteCommand.INTERRUPT -> if (s?.busy != true) "rien en cours" else null
            PaletteCommand.TOGGLE_VOICE -> if (s == null) "démarre d'abord une session" else null
            PaletteCommand.RESUME_LAST -> if (latest == null) "aucune conversation enregistrée" else null
            else -> null
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill(c.label, "palette-${c.name.lowercase()}", enabled = why == null) { if (why == null) run(c, app, ui, actions, latest?.id) }
            if (why != null) { Spacer(Modifier.width(8.dp)); Text("indisponible : $why", style = Holo.small) }
        }
    }
}

private fun run(c: PaletteCommand, app: ConvApp, ui: V3UiState, actions: SceneActions, latestId: String?) {
    val s = ui.session
    fun module(m: CommandModule) { ui.commandTab = m; ui.panel = Panel.COMMAND }
    ui.panel = Panel.NONE
    when (c) {
        PaletteCommand.TALK -> actions.requestMicThenTalk()
        PaletteCommand.INTERRUPT -> s?.interrupt()
        PaletteCommand.NEW_CONVERSATION -> { ui.openCard = null; app.closeSession(); ui.session = null; ui.resumeRequest = ConvApp.NEW_CONVERSATION }
        PaletteCommand.RESUME_LAST -> latestId?.let { ui.openCard = null; app.closeSession(); ui.session = null; ui.resumeRequest = it }
        PaletteCommand.IMPORT -> actions.importFiles()
        PaletteCommand.LIBRARY -> ui.panel = Panel.KNOWLEDGE
        PaletteCommand.MEMORY_VAULT -> module(CommandModule.MEMORY)
        PaletteCommand.COMMAND_CENTER -> ui.panel = Panel.COMMAND
        PaletteCommand.AI_CORE -> module(CommandModule.AI_CORE)
        PaletteCommand.VOICE_CENTER -> module(CommandModule.VOICE)
        PaletteCommand.NEXUS -> module(CommandModule.NEXUS)
        PaletteCommand.SECURITY -> module(CommandModule.SECURITY)
        PaletteCommand.BLUEPRINT -> module(CommandModule.BLUEPRINT)
        PaletteCommand.TOGGLE_VOICE -> s?.let { it.voiceOutputEnabled = !it.voiceOutputEnabled }
        PaletteCommand.TOGGLE_AVATAR -> { ui.showAvatar = !ui.showAvatar
            app.getSharedPreferences("ui", android.content.Context.MODE_PRIVATE).edit().putBoolean("avatar", ui.showAvatar).apply() }
        PaletteCommand.SETTINGS -> ui.panel = Panel.SETTINGS
    }
}
