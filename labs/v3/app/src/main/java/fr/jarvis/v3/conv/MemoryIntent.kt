package fr.jarvis.v3.conv

/** Pure detection of an explicit memorisation request; the result is only a PROPOSAL shown to the user. */
object MemoryIntent {
    private val REMEMBER = Regex("(?i)^\\s*(?:jarvis,?\\s*)?(?:souviens[- ]toi|rappelle[- ]toi|retiens|n'oublie pas|mémorise|memorise)\\s+(?:bien\\s+)?(?:que\\s+|qu'|de\\s+|:\\s*)?(.{3,300})$")

    /** "souviens-toi que …", "retiens que …", "n'oublie pas que …" → the sentence to propose, else null. */
    fun rememberRequest(text: String): String? = REMEMBER.find(text.trim())?.groupValues?.get(1)?.trim()?.trimEnd('.', '!', ' ')
        ?.takeIf { it.length >= 3 }?.replaceFirstChar { it.uppercaseChar() }?.let { "$it." }
}
