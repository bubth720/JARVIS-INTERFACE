package com.stark.jarvislocal

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.*
import com.jarvis.interfaceapp.ui.chat.core.CoreTextLink
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

class LabActivity : Activity() {
    private val screenScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val owner get() = application as LabApplication
    lateinit var status: TextView
        private set
    lateinit var response: TextView
        private set
    lateinit var question: EditText
        private set
    lateinit var submit: Button
        private set
    lateinit var stop: Button
        private set
    private lateinit var enable: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        fun label(text: String) = TextView(this).apply { this.text = text; textSize = 17f; layout.addView(this) }
        label("JARVIS · LAB/SYNTHETIC").textSize = 24f
        label("CORE réel et INTERFACE dans le même processus. Réponses fixes de test.")
        label("LLM indisponible · NEXUS non raccordé · SERVICES, VOICE et EVOLUTION OFF")
        label("Aucun document, modèle, microphone ou stockage personnel.")
        val scenario = runCatching { LabCoreTransport.Scenario.valueOf(intent.getStringExtra("LAB_SCENARIO") ?: "NORMAL") }
            .getOrDefault(LabCoreTransport.Scenario.NORMAL)
        if (scenario != LabCoreTransport.Scenario.NORMAL) label("Injection de panne synthétique : ${scenario.name}")
        label("Session RAM · activation volontaire").contentDescription = "epoch:${owner.epoch}"
        status = label("OFF · aucune session démarrée").apply { contentDescription = "lab-status" }
        enable = Button(this).apply { text = "Activer le laboratoire"; tag = "activate-lab"; layout.addView(this) }
        question = EditText(this).apply {
            hint = "Texte de test (ignoré par le simulateur)"
            contentDescription = "lab-question"
            maxLines = 3
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            isSaveEnabled = false
            layout.addView(this)
        }
        submit = Button(this).apply { text = "Envoyer"; contentDescription = "lab-submit"; isEnabled = false; layout.addView(this) }
        stop = Button(this).apply { text = "Stop"; isEnabled = false; layout.addView(this) }
        response = label("").apply { contentDescription = "lab-response"; isSaveEnabled = false }
        Button(this).apply {
            text = "Fermer et effacer la session RAM"
            tag = "close-lab"
            setOnClickListener { owner.session?.close(); question.text.clear() }
            layout.addView(this)
        }
        setContentView(ScrollView(this).apply { addView(layout) })
        enable.contentDescription = "lab-activate"
        enable.setOnClickListener { attach(owner.enable(scenario)) }
        submit.setOnClickListener {
            if (owner.session?.client?.submit(question.text.toString()) == true) question.text.clear()
        }
        stop.setOnClickListener { owner.session?.client?.cancel() }
        owner.session?.let(::attach)
    }

    private fun attach(session: LabSession) {
        enable.isEnabled = false
        screenScope.launch {
            session.client.state.collectLatest { state ->
                status.text = when {
                    session.closed -> "FERMÉ · RAM effacée · relancer le processus pour un nouveau banc"
                    state.link == CoreTextLink.UNKNOWN -> "UNKNOWN · nouvel envoi bloqué · redémarrage du processus requis"
                    session.transport.coordinator.state.value.phase == SessionPhase.ERROR -> "ERROR · échec de l'opération synthétique CORE"
                    state.link != CoreTextLink.READY -> "Préparation du laboratoire · aucun LLM"
                    state.busy -> "CORE réel · ACK ${session.transport.acknowledgements} · opération de test active"
                    else -> "CORE réel · ACK ${session.transport.acknowledgements} · laboratoire disponible"
                }
                response.text = state.text
                submit.isEnabled = !session.closed && state.link == CoreTextLink.READY && !state.busy
                stop.isEnabled = !session.closed && state.busy && state.link != CoreTextLink.UNKNOWN
            }
        }
    }

    override fun onDestroy() {
        screenScope.cancel()
        super.onDestroy() // Process owner survives rotation; no second coordinator is created.
    }
}
