package com.stark.jarvislocal.v1b

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import com.jarvis.interfaceapp.ui.chat.core.CoreTextLink
import com.stark.jarvislocal.SessionPhase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine

/** Minimal V1-B chat (real multi-turn roles, 128-token default ceiling). Every status is real; nothing is pre-filled as if the model had answered. */
class V1bActivity : Activity() {
    private val screenScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val owner get() = application as V1bApplication
    lateinit var status: TextView
    lateinit var modelStatus: TextView
    lateinit var transcript: TextView
    lateinit var pending: TextView
    lateinit var error: TextView
    lateinit var question: EditText
    lateinit var expectedSha: EditText
    lateinit var enable: Button
    lateinit var experimental: CheckBox
    lateinit var metrics: TextView
    lateinit var submit: Button
    lateinit var stop: Button
    private lateinit var importButton: Button
    private var lastQuestion: String? = null
    private var attached: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        fun label(text: String, size: Float = 16f) = TextView(this).apply { this.text = text; textSize = size; layout.addView(this) }
        label("JARVIS V1-B", 24f)
        label("CORE réel · moteur JARVIS-LLM local (A02) · hors ligne. Voix, documents et services : OFF.")
        label("Contexte 768 · 1 thread · réponse ≤ 128 jetons (arrêt naturel en fin de réponse).", 13f)
        modelStatus = label("").apply { contentDescription = "v1b-model" }
        expectedSha = EditText(this).apply {
            hint = "SHA-256 attendu du GGUF (64 caractères)"; contentDescription = "v1b-sha"; maxLines = 2
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS; layout.addView(this)
        }
        importButton = Button(this).apply { text = "Importer un modèle GGUF local"; contentDescription = "v1b-import"; layout.addView(this) }
        experimental = CheckBox(this).apply {
            text = "Expérimental : plafond 256 jetons (plus lent, question plus courte)"; contentDescription = "v1b-experimental-256"; layout.addView(this)
        }
        enable = Button(this).apply { text = "Démarrer JARVIS"; contentDescription = "v1b-enable"; layout.addView(this) }
        status = label("OFF · aucune session").apply { contentDescription = "v1b-status" }
        error = label("").apply { contentDescription = "v1b-error"; setTextColor(0xFFB00020.toInt()) }
        metrics = label("", 12f).apply { contentDescription = "v1b-metrics" }
        transcript = label("").apply { contentDescription = "v1b-transcript"; isSaveEnabled = false }
        pending = label("").apply { contentDescription = "v1b-pending"; isSaveEnabled = false }
        question = EditText(this).apply {
            hint = "Votre message"; contentDescription = "v1b-question"; maxLines = 4; isSaveEnabled = false
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS; layout.addView(this)
        }
        submit = Button(this).apply { text = "Envoyer"; contentDescription = "v1b-submit"; isEnabled = false; layout.addView(this) }
        stop = Button(this).apply { text = "Stop"; contentDescription = "v1b-stop"; isEnabled = false; layout.addView(this) }
        Button(this).apply {
            text = "Fermer la session et décharger le modèle"; contentDescription = "v1b-close"
            setOnClickListener {
                owner.session?.close(); refreshModel()
                // Reload is allowed once the session is closed: a new session loads a fresh engine.
                attached?.cancel(); attached = null
                status.text = "FERMÉ · déchargement après drainage · « Démarrer JARVIS » recharge le modèle"
                submit.isEnabled = false; stop.isEnabled = false
                enable.isEnabled = true; importButton.isEnabled = true; experimental.isEnabled = true
            }
            layout.addView(this)
        }
        setContentView(ScrollView(this).apply { addView(layout) })

        importButton.setOnClickListener {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
            }, REQUEST_IMPORT)
        }
        enable.setOnClickListener {
            error.text = ""
            runCatching { owner.enable(if (experimental.isChecked) V1bPolicy.EXPERIMENTAL_OUTPUT else V1bPolicy.DEFAULT_OUTPUT) }.onSuccess(::attach).onFailure { error.text = if (it.message == "MODEL_ABSENT") "Aucun modèle importé : importer un GGUF vérifié d'abord." else (it.message ?: "Erreur") }
        }
        submit.setOnClickListener {
            val text = question.text.toString().trim()
            if (text.isNotEmpty() && owner.session?.client?.submit(text) == true) { lastQuestion = text; question.text.clear() }
        }
        stop.setOnClickListener { owner.session?.client?.cancel() }
        refreshModel()
        owner.session?.takeIf { !it.closed }?.let(::attach)
    }

    private fun refreshModel() {
        val entry = owner.store.current()
        modelStatus.text = entry?.let { "Modèle importé : ${it.sizeBytes / (1024 * 1024)} Mio · SHA ${it.sha256.take(12)}… (revérifié à chaque chargement)" }
            ?: "Aucun modèle importé"
    }

    @Deprecated("Platform API kept for API 29 without AndroidX")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode != REQUEST_IMPORT || resultCode != RESULT_OK || uri == null) return
        val sha = expectedSha.text.toString()
        error.text = ""; status.text = "Import et vérification SHA-256…"
        screenScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { contentResolver.openInputStream(uri).use { owner.store.import(requireNotNull(it) { "Document inaccessible" }, sha) } }
            }
            result.onSuccess { status.text = "Modèle vérifié et installé dans le stockage privé" }
                .onFailure { error.text = it.message ?: "Import refusé"; status.text = "Import refusé" }
            refreshModel()
        }
    }

    private fun attach(session: V1bSession) {
        enable.isEnabled = false; importButton.isEnabled = false; experimental.isEnabled = false
        attached?.cancel()
        attached = screenScope.launch {
            combine(session.client.state, session.transport.lastError, session.transport.lastMetrics) { a, b, c -> Triple(a, b, c) }.collectLatest { (state, failure, measured) ->
                val phase = session.transport.coordinator.state.value.phase
                status.text = when {
                    session.closed -> "FERMÉ · modèle déchargé après drainage"
                    state.link == CoreTextLink.UNKNOWN -> "UNKNOWN · envoi bloqué · redémarrer l'application"
                    phase == SessionPhase.ERROR -> "ERREUR · voir le détail"
                    state.link != CoreTextLink.READY -> "Chargement A02 du modèle local…"
                    state.busy -> "Génération locale en cours…"
                    else -> "Prêt · hors ligne · ${session.transport.history.snapshot().size} tour(s)"
                }
                error.text = when {
                    failure == null -> ""
                    failure.contains("BUDGET") -> "Question trop longue pour le budget CORE (plafond ${session.model.outputCeiling} jetons) : raccourcis-la."
                    else -> failure
                }
                metrics.text = measured?.let {
                    "Dernier tour : ${it.generatedTokens}/${it.maxTokens} jetons" + (if (it.stoppedNaturally) " (fin naturelle)" else " (plafond atteint)") +
                        " · 1er jeton ${it.firstTokenMs} ms · ${it.decodeTokensPerSecond} jetons/s · total ${it.totalMs} ms · ${it.historyPairsSent} échange(s) de contexte"
                } ?: "Plafond de sortie : ${session.model.outputCeiling} jetons"
                transcript.text = session.transport.history.snapshot().joinToString("\n\n") { "Vous : ${it.user}\nJARVIS : ${it.assistant}" }
                pending.text = if (state.busy) "Vous : ${lastQuestion.orEmpty()}\nJARVIS : ${state.text.ifEmpty { "(génération en cours, aucune réponse encore)" }}" else ""
                submit.isEnabled = !session.closed && state.link == CoreTextLink.READY && !state.busy
                stop.isEnabled = !session.closed && state.busy && state.link != CoreTextLink.UNKNOWN
            }
        }
    }

    override fun onDestroy() { screenScope.cancel(); super.onDestroy() }

    companion object { const val REQUEST_IMPORT = 41 }
}
