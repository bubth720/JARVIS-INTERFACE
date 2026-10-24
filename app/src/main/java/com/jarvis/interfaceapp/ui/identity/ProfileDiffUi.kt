package com.jarvis.interfaceapp.ui.identity

data class ProfileDifference(val field: String, val before: String, val after: String)
object ProfileDiffUi {
    fun compare(before: ProfileDraft, after: ProfileDraft): List<ProfileDifference> = buildList {
        if(before.name!=after.name) add(ProfileDifference("name",before.name,after.name))
        if(before.preset!=after.preset) add(ProfileDifference("preset",before.preset.name,after.preset.name))
        if(before.defaults!=after.defaults) add(ProfileDifference("defaults",before.defaults.toString(),after.defaults.toString()))
        for(context in PersonaContext.entries) {
            val a=before.overrides[context];val b=after.overrides[context]
            if(a!=b) add(ProfileDifference("override:${context.name}",a?.toString()?:"INHERIT_DEFAULTS",b?.toString()?:"INHERIT_DEFAULTS"))
            if(before.parameters(context)!=after.parameters(context)) add(ProfileDifference(context.name,before.parameters(context).toString(),after.parameters(context).toString()))
        }
        if(before.interests!=after.interests) add(ProfileDifference("simulated_interests",before.interests.toString(),after.interests.toString()))
        if(before.rules!=after.rules) add(ProfileDifference("language_rules",before.rules.toString(),after.rules.toString()))
    }
}
data class ReadOnlyPersonalityPreview(val context: PersonaContext, val before: String, val after: String,
    val effective: PersonaParameters, val inherited: Boolean,
    val label: String = "Modèle de formulation / pas une réponse LLM") {
    companion object {
        fun create(draft: ProfileDraft, context: PersonaContext): ReadOnlyPersonalityPreview {
            val p=draft.parameters(context)
            val effective=when(context) {
                PersonaContext.DAILY -> p
                PersonaContext.WORK -> p.copy(humour=minOf(p.humour,30))
                PersonaContext.URGENT -> p.copy(humour=0,initiative=minOf(p.initiative,40))
            }
            val before=when(context) {
                PersonaContext.DAILY -> "Bonjour. Merci pour cette question. Explorons cette idée ensemble."
                PersonaContext.WORK -> "Bonjour. Merci pour ces éléments. Vérifions les faits avant de conclure."
                PersonaContext.URGENT -> "Bonjour. Concentrons-nous sur le risque immédiat et les étapes utiles."
            }
            val rules=draft.rules.associateBy { it.source.text }
            val after=if(context==PersonaContext.URGENT) before else Regex("(?<![\\p{L}\\p{N}_])(?:Bonjour|Salut|Merci|Volontiers)(?![\\p{L}\\p{N}_])")
                .replace(before) { rules[it.value]?.target?.text ?: it.value }
            return ReadOnlyPersonalityPreview(context,before,after,effective,context !in draft.overrides)
        }
    }
}
