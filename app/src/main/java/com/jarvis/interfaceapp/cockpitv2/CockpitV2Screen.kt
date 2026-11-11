package com.jarvis.interfaceapp.cockpitv2

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/** Experimental host. Slots can be replaced without changing navigation or layout storage. */
@Composable
fun CockpitV2Screen(
    state: CockpitState,
    session: CockpitSession,
    onDashboard: () -> Unit,
    onSettings: () -> Unit,
    avatar: @Composable (Modifier) -> Unit = { StaticAvatarPreview(it) },
    sphere: @Composable (Modifier) -> Unit = { StaticKnowledgeSphere(it) },
) {
    BackHandler(onBack = session::back)
    Surface(color = CockpitInk, contentColor = Color(0xFFE4EEF8)) {
    BoxWithConstraints(Modifier.fillMaxSize()
        .background(Brush.verticalGradient(listOf(Color(0xFF10182D), CockpitInk)))
        .windowInsetsPadding(WindowInsets.safeDrawing).imePadding().padding(12.dp)
        .testTag("cockpit-v2").semantics { testTagsAsResourceId = true; isTraversalGroup = true }) {
        val wide = maxWidth >= 840.dp && maxWidth > maxHeight
        val pageScrolls = maxHeight < 480.dp || LocalDensity.current.fontScale > 1.4f
        val scroll = rememberScrollState()
        val pageScroll = rememberScrollState()
        val pageHeight = maxHeight
        LaunchedEffect(state.knowledgeOpen, scroll.maxValue, wide, pageScrolls) {
            if (!state.knowledgeOpen) scroll.scrollTo(0)
            else if (!wide && !pageScrolls) scroll.scrollTo(scroll.maxValue)
        }
        // Keep the same scroll/focus ancestor when the IME changes the viewport.
        // A bounded inner column preserves the anchored footer in normal windows.
        Column(Modifier.fillMaxSize().verticalScroll(pageScroll)) {
        Column(if (pageScrolls) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().height(pageHeight),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("JARVIS · COCKPIT V2", color = CockpitCyan,
                        style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    Text("EXPÉRIMENTAL · HORS LIGNE", style = MaterialTheme.typography.labelMedium,
                        color = CockpitViolet)
                }
                TextButton(onDashboard, Modifier.heightIn(min = 48.dp).testTag("v2-return")) { Text("Retour") }
            }
            val deckModifier = if (pageScrolls) Modifier.fillMaxWidth() else
                Modifier.weight(1f).fillMaxWidth()
            BoxWithConstraints(deckModifier.testTag("v2-deck")) {
            val deckHeight = if (pageScrolls) 400.dp else maxHeight
            val visualHeight = when {
                wide -> (deckHeight - 160.dp).coerceIn(100.dp, 420.dp)
                state.knowledgeOpen && !pageScrolls -> (deckHeight - 260.dp).coerceIn(80.dp, 180.dp)
                else -> 180.dp
            }
            Column(Modifier.fillMaxWidth().then(if (pageScrolls) Modifier else Modifier.verticalScroll(scroll)), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    VisualPanel("AVATAR", Modifier.weight(if (wide) .28f else .38f).testTag("v2-avatar")) {
                        avatar(Modifier.fillMaxWidth().height(visualHeight))
                        Text("Visuel féminin provisoire · statique", style = MaterialTheme.typography.bodySmall)
                        Text("Moteur non chargé", color = CockpitViolet, style = MaterialTheme.typography.labelSmall)
                    }
                    VisualPanel("CONNAISSANCES", Modifier.weight(if (wide) .44f else .62f).testTag("v2-sphere")) {
                        sphere(Modifier.fillMaxWidth().height(visualHeight))
                        Text("Illustration 2D statique · aucun moteur 3D", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(session::showKnowledge, Modifier.heightIn(min = 48.dp).testTag("v2-demo-open")) {
                            Text("Voir une fiche de démonstration")
                        }
                    }
                    if (wide) {
                        if (state.knowledgeOpen) KnowledgeCard(session::hideKnowledge, Modifier.weight(.28f).heightIn(max = deckHeight).verticalScroll(rememberScrollState()))
                        else Spacer(Modifier.weight(.28f))
                    }
                }
                if (!wide && state.knowledgeOpen) KnowledgeCard(session::hideKnowledge, Modifier.fillMaxWidth()
                    .heightIn(max = (deckHeight - visualHeight - 190.dp).coerceIn(120.dp, 280.dp)).verticalScroll(rememberScrollState()))
            }
            }
            CommandBar(state, session, onDashboard, onSettings)
        }
        }
    }
    }
}

@Composable
private fun VisualPanel(title: String, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier, color = Color(0xFF0E192B), shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, CockpitCyan.copy(alpha = .3f))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = CockpitCyan,
                modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

@Composable
private fun KnowledgeCard(close: () -> Unit, modifier: Modifier) {
    VisualPanel("FICHE · DÉMONSTRATION", modifier.testTag("v2-knowledge-card")) {
        TextButton(close, Modifier.heightIn(min = 48.dp).testTag("v2-demo-close")) { Text("Fermer la fiche") }
        Text("Explorer une connaissance", style = MaterialTheme.typography.titleMedium)
        Text("Exemple local pour examiner la présentation d’une fiche dans le cockpit. Ce contenu ne provient pas de SERVICES et ne constitue pas un résultat de recherche.")
        HorizontalDivider(color = CockpitViolet.copy(alpha = .4f))
        Text("Informations clés", color = CockpitViolet, modifier = Modifier.semantics { heading() })
        Text("Type : exemple d’interface\nIllustration : facultative, non fournie\nÉtat : démonstration locale")
        Text("Sources : aucune source consultée. Aucune citation ni validation documentaire.",
            modifier = Modifier.testTag("v2-source-status"), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CommandBar(state: CockpitState, session: CockpitSession, dashboard: () -> Unit, settings: () -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    Surface(Modifier.fillMaxWidth().testTag("v2-command-bar"), color = Color(0xFF111D32),
        shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, CockpitViolet.copy(alpha = .35f))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(state.draft, session::editDraft, Modifier.weight(1f).testTag("v2-draft"),
                    label = { Text("Texte local") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); keyboard?.hide() }))
                IconButton({}, Modifier.size(48.dp).testTag("v2-send").semantics {
                    contentDescription = "Envoyer"; stateDescription = "Indisponible : CORE non connecté, aucun envoi."
                }, enabled = false) { CommandSymbol(false) }
                IconButton({}, Modifier.size(48.dp).testTag("v2-microphone").semantics {
                    contentDescription = "Microphone"; stateDescription = "Indisponible : VOICE et CORE non connectés, aucune capture audio."
                }, enabled = false) { CommandSymbol(true) }
            }
            Text("Brouillon en mémoire seulement. Envoi et microphone indisponibles : CORE / VOICE non connectés.",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("v2-command-explanation"))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Aucune conversation enregistrée", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                    color = CockpitViolet)
                Box {
                    FilledTonalIconButton(session::toggleWheel, Modifier.size(56.dp).testTag("v2-wheel").semantics {
                        contentDescription = "Menu du cockpit"; stateDescription = if (state.wheelOpen) "Ouvert" else "Fermé"
                    }, shape = CircleShape) {
                        Canvas(Modifier.size(28.dp).clearAndSetSemantics {}) {
                            drawCircle(CockpitCyan, size.minDimension * .42f, style = Stroke(2f))
                            listOf(Offset(.5f, .12f), Offset(.17f, .7f), Offset(.83f, .7f)).forEach {
                                drawLine(CockpitCyan, center, Offset(size.width * it.x, size.height * it.y), 2f)
                            }
                            drawCircle(CockpitCyan, 3f, center)
                        }
                    }
                    DropdownMenu(state.wheelOpen, session::dismissWheel, Modifier.widthIn(max = 320.dp).testTag("v2-wheel-menu")) {
                        DropdownMenuItem(text = { Text("Tableau de bord historique") }, onClick = dashboard, modifier = Modifier.testTag("v2-go-dashboard"))
                        DropdownMenuItem(text = { Text("Paramètres") }, onClick = settings, modifier = Modifier.testTag("v2-go-settings"))
                        DropdownMenuItem(text = { Text("Fiche de démonstration") }, onClick = session::showKnowledge, modifier = Modifier.testTag("v2-menu-demo"))
                        listOf("CORE", "SERVICES", "VOICE").forEach { name ->
                            DropdownMenuItem(text = { Text("$name · non connecté") }, onClick = {}, enabled = false,
                                modifier = Modifier.testTag("v2-unavailable-$name").semantics { stateDescription = "Indisponible : aucune intégration dans ce prototype." })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandSymbol(microphone: Boolean) {
    Canvas(Modifier.size(24.dp).clearAndSetSemantics {}) {
        val tint = CockpitViolet.copy(alpha = .5f)
        val w = size.width; val h = size.height
        if (microphone) {
            drawRoundRect(tint, Offset(w * .36f, h * .1f), Size(w * .28f, h * .5f), androidx.compose.ui.geometry.CornerRadius(w * .14f), style = Stroke(2f))
            drawArc(tint, 0f, 180f, false, Offset(w * .2f, h * .25f), Size(w * .6f, h * .5f), style = Stroke(2f))
            drawLine(tint, Offset(w * .5f, h * .75f), Offset(w * .5f, h * .95f), 2f)
        } else {
            val path = Path().apply { moveTo(w * .15f, h * .15f); lineTo(w * .9f, h * .5f); lineTo(w * .15f, h * .85f); lineTo(w * .3f, h * .5f); close() }
            drawPath(path, tint, style = Stroke(2f))
        }
    }
}
