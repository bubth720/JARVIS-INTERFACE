package com.jarvis.interfaceapp.workspace

import com.jarvis.interfaceapp.domain.*
import com.jarvis.interfaceapp.ui.identity.*

enum class LocalTheme { NAVY, GRAPHITE, DEEP_BLUE }
enum class DisplayMode { AUTOMATIC, COMPACT, EXTENDED }
data class VisualPreferences(val theme:LocalTheme=LocalTheme.NAVY,val textPercent:Int=100,
    val elementPercent:Int=100,val reduceMotion:Boolean=false,val transitions:Boolean=true,
    val mode:DisplayMode=DisplayMode.AUTOMATIC,val visible:Set<PanelId> = PanelId.entries.toSet()) {
    init { require(textPercent in 100..150 && elementPercent in 100..125 && visible.isNotEmpty()) }
}
data class LocalPersona(val profile:ProfileDraft=ProfileDraft.preset(PersonaPreset.ATHENA),val detail:Int=50) {
    init { require(detail in 0..100) }
}
data class ArchivedMessage(val id:String,val text:String,val user:Boolean,val simulation:Boolean,val sources:List<DocumentSource> = emptyList()) {
    init { require(sources.size<=8);require(id.length in 1..80 && text.length<=4000 && '\u0000' !in text) }
}
data class Workspace(val visual:VisualPreferences=VisualPreferences(),val persona:LocalPersona=LocalPersona(),
    val layouts:Map<String,Layouts> = emptyMap(),val keepHistory:Boolean=false,
    val history:List<ArchivedMessage> = emptyList(),val packages:Map<ModuleId,String> = emptyMap()) {
    init {
        require(layouts.size<=12 && layouts.keys.all { validLayoutName(it) })
        layouts.values.forEach(::validateLayouts)
        require(history.size<=200 && history.map { it.id }.distinct().size==history.size)
        require(keepHistory || history.isEmpty())
        require(packages.values.all { validPackage(it) })
    }
}
fun validLayoutName(value:String)=value.trim()==value && value.length in 1..40 && value.none { it.isISOControl() }
fun validPackage(value:String)=value.length<=200 && Regex("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+").matches(value)
fun validateLayouts(layouts:Layouts) {
    require(layouts.keys==DashboardProfile.entries.toSet())
    layouts.forEach { (profile,panels) ->
        require(panels.size==4 && panels.map { it.id }.toSet()==PanelId.entries.toSet())
        require(panels.all { it.minWidth==3 && it.minHeight==3 && placementError(profile.grid,panels,it)==null })
    }
}
enum class ModuleId { CORE, VOICE, NEXUS, SERVICES, EVOLUTION }
enum class ModuleHealth { UNCONFIGURED, CONFIGURED_UNVERIFIED, UNOBSERVABLE, INSTALLED_NOT_CONNECTED, CONNECTED, ERROR }
data class ModuleObservation(val id:ModuleId,val health:ModuleHealth=ModuleHealth.UNCONFIGURED,
    val version:String?=null,val declaredCapabilities:Set<String> = emptySet(),val lastError:String?=null)
/** Internal UI contract, not an IPC schema. Installed never means ready. */
interface ModuleUiAdapter {
    val contractVersion:Int get()=1
    val id:ModuleId
    suspend fun observe():ModuleObservation
}
class UnavailableModuleAdapter(override val id:ModuleId):ModuleUiAdapter {
    override suspend fun observe()=ModuleObservation(id)
}
class SimulatedModuleAdapter(override val id:ModuleId):ModuleUiAdapter {
    override suspend fun observe()=ModuleObservation(id,ModuleHealth.UNOBSERVABLE,lastError="SIMULATION — aucune connexion ni capacité attestée")
}
/** Extension catalog only; optional panels use independent destinations, never V1 panel IDs. */
data class PanelDescriptor(val id:String,val title:String,val destination:String)
val panelCatalog=listOf(PanelDescriptor("avatar","Avatar","dashboard"),PanelDescriptor("system","Système","modules"),
    PanelDescriptor("applications","Applications JARVIS","modules"),PanelDescriptor("commands","Commandes","dashboard"),
    PanelDescriptor("conversation","Conversation","chat"),PanelDescriptor("services","Services","modules"),
    PanelDescriptor("knowledge","Connaissances","modules"),PanelDescriptor("tasks","Tâches","tasks"),PanelDescriptor("diagnostics","Diagnostics","modules"))

/** Merge the fields edited against an observed snapshot, preserving concurrent unrelated choices. */
fun mergeVisual(current:VisualPreferences,before:VisualPreferences,next:VisualPreferences)=current.copy(
    theme=if(next.theme!=before.theme)next.theme else current.theme,
    textPercent=if(next.textPercent!=before.textPercent)next.textPercent else current.textPercent,
    elementPercent=if(next.elementPercent!=before.elementPercent)next.elementPercent else current.elementPercent,
    reduceMotion=if(next.reduceMotion!=before.reduceMotion)next.reduceMotion else current.reduceMotion,
    transitions=if(next.transitions!=before.transitions)next.transitions else current.transitions,
    mode=if(next.mode!=before.mode)next.mode else current.mode,
    visible=(current.visible-(before.visible-next.visible))+(next.visible-before.visible))
