package fr.jarvis.v3.model

/**
 * Holographic Command Center V2 (PDF §7, §9, §13): the functional matrix of the screens. "Aucun faux bouton":
 * a module is OPERATIONAL only when this build really runs it; otherwise it is shown "à connecter" with the
 * reason, never with invented data.
 */
enum class ModuleStatus(val label: String) { OPERATIONAL("opérationnel"), PARTIAL("partiel"), TO_CONNECT("à connecter") }

enum class CommandModule(val label: String, val status: ModuleStatus, val note: String) {
    AI_CORE("AI CORE", ModuleStatus.OPERATIONAL, "Modèle local Qwen3-0.6B vérifié, moteur A02 protégé, personnalité et mémoire de conversation."),
    VOICE("VOICE CENTER", ModuleStatus.OPERATIONAL, "Micro → whisper.cpp → réponse → Pocket TTS, réaction d'attente, interruption."),
    MEMORY("MEMORY VAULT", ModuleStatus.PARTIAL, "Mémoire de conversation, historique local et souvenirs durables confirmés (workflow CORE) ; export chiffré à connecter."),
    NEXUS("NEXUS", ModuleStatus.PARTIAL, "Bibliothèque locale, recherche FTS5 (SQLite embarqué AndroidX) et citations ; Kiwix non configuré."),
    MISSIONS("MISSIONS", ModuleStatus.TO_CONNECT, "Aucun moteur de missions n'est connecté à cette version."),
    AGENTS("AGENTS", ModuleStatus.TO_CONNECT, "Aucun agent n'est disponible : JARVIS n'exécute aucune action."),
    AUTOMATIONS("AUTOMATIONS", ModuleStatus.TO_CONNECT, "Aucune routine ni déclencheur n'existe dans cette version."),
    SECURITY("SECURITY", ModuleStatus.OPERATIONAL, "Permissions, absence d'Internet, gardes thermique, mémoire et actions, effacement local."),
    EVOLUTION("EVOLUTION LAB", ModuleStatus.PARTIAL, "Diagnostic et mesures réels ; expériences et correctifs désactivés (approbation humaine requise)."),
    BLUEPRINT("SYSTEM BLUEPRINT", ModuleStatus.OPERATIONAL, "Carte des modules réellement compilés, versions épinglées et disponibilité."),
}

/** One real module of this build, pinned at an exact commit (same pins as the CI workflow that builds the APK). */
data class BlueprintNode(val name: String, val repository: String, val commit: String, val role: String, val connected: Boolean)

object Blueprint {
    val NODES = listOf(
        BlueprintNode("CORE", "JAVIS-Local-v2", "5da9838", "SessionCoordinator, contrats d'inférence, identité", true),
        BlueprintNode("LLM A02", "JARVIS-LLM", "2e01177", "Moteur llama.cpp scellé (contexte 768, 1 thread)", true),
        BlueprintNode("VOICE", "JARVIS-VOICE", "92d0745", "Pocket TTS (voix alba), règles de réaction rapide", true),
        BlueprintNode("STT", "whisper.cpp", "v1.8.4", "Reconnaissance vocale locale (ggml-base-q5_1)", true),
        BlueprintNode("NEXUS", "JARVIS-NEXUS", "06ccf98", "Connaissances locales, FTS5, citations", true),
        BlueprintNode("INTERFACE", "JARVIS-INTERFACE", "labs/v3", "Cockpit V3, conversation CONVERSATION-01", true),
        BlueprintNode("SERVICES", "JARVIS-SERVICES", "—", "Actions encadrées : non connecté", false),
        BlueprintNode("EVOLUTION", "JARVIS-VOLUTION-", "—", "Désactivé par décision du propriétaire", false),
    )
    /** Dependencies actually used at runtime (from → to). */
    val EDGES = listOf("INTERFACE" to "CORE", "CORE" to "LLM A02", "INTERFACE" to "STT", "INTERFACE" to "VOICE", "INTERFACE" to "NEXUS")
}
