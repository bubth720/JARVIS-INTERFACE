package fr.jarvis.v3.model

/** Command Palette (Command Center V2 §8): every command that really exists in this build; nothing decorative. */
enum class PaletteCommand(val label: String, val keywords: String) {
    TALK("Parler à JARVIS", "micro voix écouter parler"),
    INTERRUPT("Interrompre", "arrêter stop couper silence"),
    NEW_CONVERSATION("Nouvelle conversation", "nouveau recommencer session"),
    RESUME_LAST("Reprendre la dernière conversation", "reprise historique précédente"),
    IMPORT("Importer des documents", "fichier nexus bibliothèque ajouter"),
    LIBRARY("Bibliothèque (fiches)", "connaissances fiches"),
    MEMORY_VAULT("MEMORY VAULT", "souvenirs mémoire oublier supprimer"),
    COMMAND_CENTER("Command Center", "tableau bord supervision modules"),
    AI_CORE("AI CORE", "modèle llm vitesse jetons"),
    VOICE_CENTER("VOICE CENTER", "voix synthèse transcription"),
    NEXUS("NEXUS", "documents recherche citations"),
    SECURITY("SECURITY", "permissions effacer sécurité"),
    BLUEPRINT("SYSTEM BLUEPRINT", "modules versions architecture"),
    TOGGLE_VOICE("Réponses parlées : activer / couper", "voix muet son"),
    TOGGLE_AVATAR("Avatar : afficher / masquer", "athéna portrait"),
    SETTINGS("Réglages", "options paramètres personnalité");

    companion object {
        private fun fold(s: String) = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase()
        /** Commands where every word of [query] starts a word of the label or keywords (accents ignored). */
        fun search(query: String): List<PaletteCommand> {
            val words = fold(query).split(Regex("\\s+")).filter { it.isNotBlank() }
            return entries.filter { c -> val hay = fold(c.label + " " + c.keywords).split(Regex("[^a-z0-9]+")); words.all { w -> hay.any { it.startsWith(w) } } }
        }
    }
}
