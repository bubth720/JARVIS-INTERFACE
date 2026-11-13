package fr.jarvis.v3

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Bottom of the screen (§03): persistent input, microphone, send (or stop while busy) and radial access.
 * The waveform shows only REAL levels (microphone RMS / Pocket PCM) and stays flat otherwise.
 */
@Composable
fun ConversationDock(
    text: String, onText: (String) -> Unit, onSend: () -> Unit, sendEnabled: Boolean,
    micEnabled: Boolean, listening: Boolean, onMic: () -> Unit, busy: Boolean, onStop: () -> Unit,
    level: Float, menuOpen: Boolean, onMenu: () -> Unit, onAnchor: (Offset) -> Unit, modifier: Modifier = Modifier,
) {
    val history = remember { FloatArray(48) }
    var tick by remember { mutableIntStateOf(0) }
    val current by rememberUpdatedState(level)
    LaunchedEffect(Unit) {
        while (true) {
            val v = current
            if (v > 0f || history.any { it > 0f }) { System.arraycopy(history, 1, history, 0, history.size - 1); history[history.size - 1] = v; tick++ }
            delay(60)
        }
    }
    Column(modifier) {
        Row(Modifier.fillMaxWidth().glass(active = true, corner = 28.dp).padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            GlyphButton(if (listening) "Arrêter l'écoute" else if (micEnabled) "Parler à JARVIS" else "Micro indisponible", "talk",
                enabled = micEnabled || listening, tint = if (listening) Holo.cyan else Holo.ice, onClick = onMic) { HoloIcons.mic(this, it) }
            Box(Modifier.weight(1f).padding(horizontal = 6.dp), contentAlignment = Alignment.CenterStart) {
                if (text.isEmpty()) Text("Parlez ou écrivez votre demande…", style = Holo.body.copy(color = Holo.muted))
                BasicTextField(text, onText, Modifier.fillMaxWidth().heightIn(min = 44.dp).wrapContentHeight(Alignment.CenterVertically).testTag("input")
                    .semantics { contentDescription = "Écrire une demande" },
                    textStyle = Holo.body, cursorBrush = SolidColor(Holo.cyan), singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { if (sendEnabled) onSend() }))
            }
            if (busy) GlyphButton("Interrompre JARVIS", "interrupt", onClick = onStop) { c ->
                drawRect(c, Offset(size.width * 0.3f, size.height * 0.3f), Size(size.width * 0.4f, size.height * 0.4f)) }
            else GlyphButton("Envoyer", "send", enabled = sendEnabled, tint = Holo.cyan, onClick = onSend) { HoloIcons.send(this, it) }
            Spacer(Modifier.width(4.dp))
            RadialAnchor(menuOpen, onMenu, onAnchor)
        }
        Canvas(Modifier.fillMaxWidth().height(16.dp).padding(horizontal = 28.dp)) {
            tick.let { }
            val n = history.size; val step = size.width / n; val mid = size.height / 2
            for (i in 0 until n) {
                val h = (history[i] * size.height * 0.95f).coerceAtLeast(1f * density)
                drawLine(Holo.cyan.copy(alpha = 0.25f + 0.6f * history[i]), Offset(i * step + step / 2, mid - h / 2), Offset(i * step + step / 2, mid + h / 2), strokeWidth = 2f * density)
            }
        }
    }
}
