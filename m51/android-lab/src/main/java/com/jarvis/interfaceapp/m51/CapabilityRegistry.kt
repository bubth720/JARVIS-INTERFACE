package com.jarvis.interfaceapp.m51

/** Public technical metadata only. Presence, connection, activation, authority and qualification are independent. */
enum class CapabilityState { ABSENT, PREPARED, AVAILABLE_OFF, LAB_ONLY, ACTIVE_VERIFIED, BLOCKED, ERROR, UNKNOWN }
data class CapabilityDimensions(val codePresent: Boolean, val connected: Boolean, val enabled: Boolean,
    val runtimeReady: Boolean, val userApproved: Boolean, val deviceQualified: Boolean)
data class CapabilityEvidence(val reference: String, val testedCommit: String) {
    init { require(reference.isNotBlank() && testedCommit.matches(Regex("[0-9a-f]{40}"))) }
}
data class Capability(val moduleId: String, val version: String, val sourceSha: String, val contractVersion: String,
    val state: CapabilityState, val authority: String, val resourceProfile: String, val dependencies: List<String>,
    val dimensions: CapabilityDimensions, val explanation: String, val nextAction: String,
    val lastError: String?, val evidence: CapabilityEvidence) {
    init {
        require(moduleId.matches(Regex("[A-Z_]{2,20}")) && sourceSha.matches(Regex("[0-9a-f]{40}")))
        require(lastError == null || lastError.matches(Regex("[A-Z_]{1,80}")))
        if (state == CapabilityState.ACTIVE_VERIFIED) require(dimensions.run { codePresent && connected && enabled && runtimeReady && userApproved && deviceQualified })
        require(!dimensions.enabled || dimensions.codePresent && dimensions.connected && dimensions.runtimeReady && dimensions.userApproved)
    }
}
sealed interface CapabilityAccess {
    data class Refused(val code: String): CapabilityAccess
    data object LocalUi: CapabilityAccess
}
class CapabilityRegistry(val modules: List<Capability>) {
    init { require(modules.map { it.moduleId }.distinct().size == modules.size)
        require(modules.all { row -> row.dependencies.all { dep -> modules.any { it.moduleId == dep } } }) }
    /** Only local UI is implemented. Metadata cannot mint a mandate or turn on another runtime. */
    fun access(moduleId: String): CapabilityAccess = if (moduleId == "INTERFACE" && modules.singleOrNull { it.moduleId == moduleId }?.dimensions?.enabled == true)
        CapabilityAccess.LocalUi else CapabilityAccess.Refused("MODULE_OFF_OR_UNQUALIFIED")
    fun filtered(query: String, blockedOnly: Boolean=false) = modules.filter {
        (query.length <= 40 && it.moduleId.contains(query.trim(),ignoreCase=true)) && (!blockedOnly || it.state == CapabilityState.BLOCKED)
    }
    fun publicSummary(): String = buildString {
        appendLine("JARVIS · jarvis.capabilities/1 · M51 laboratoire")
        appendLine("Aucun runtime CORE/LLM/NEXUS/SERVICES/VOICE/AVATAR/EVOLUTION raccordé.")
        modules.forEach { c ->
            appendLine("${c.moduleId}: ${c.state}; version=${c.version}; source_sha=${c.sourceSha}; contract=${c.contractVersion}")
            appendLine("code_present=${c.dimensions.codePresent}; connected=${c.dimensions.connected}; enabled=${c.dimensions.enabled}; runtime_ready=${c.dimensions.runtimeReady}; user_approved=${c.dimensions.userApproved}; device_qualified=${c.dimensions.deviceQualified}")
            appendLine("authority=${c.authority}; resource_profile=${c.resourceProfile}; dependencies=${c.dependencies.joinToString(",")}; last_error=${c.lastError ?: "NONE"}")
            appendLine("evidence_ref=${c.evidence.reference}; tested_commit=${c.evidence.testedCommit}")
        }
    }
}
fun m51Registry(hostCommit: String, uiMounted: Boolean): CapabilityRegistry {
    require(hostCommit.matches(Regex("[0-9a-f]{40}")))
    val off=CapabilityDimensions(false,false,false,false,false,false)
    fun row(id: String, sha: String, repo: String, state: CapabilityState, reason: String, next: String,
            dependencies: List<String>, error: String?=null) = Capability(id,"git-${sha.take(12)}",sha,"UNKNOWN",state,
        "NONE", "NOT_ADMITTED",dependencies,off,reason,next,error,CapabilityEvidence("https://github.com/bubth720/$repo/tree/$sha",sha))
    return CapabilityRegistry(listOf(
        Capability("INTERFACE","0.51.0-m51-lab",hostCommit,"jarvis.capabilities/1",CapabilityState.LAB_ONLY,
            "USER_M51_LOCAL_UI_ONLY","LOCAL_UI_ONLY",emptyList(),CapabilityDimensions(true,uiMounted,uiMounted,uiMounted,true,false),
            "Cockpit UI-01C réutilisé. Données privées de ce paquet de laboratoire ; qualification de cet appareil non intégrée à l’application.",
            "Consulter les rapports de la PR M51 pour le build exact.",null,
            CapabilityEvidence("runtime:local-cockpit-mounted",hostCommit)),
        row("CORE","5da98383ed12cb5a54e896fbe57560bacc432fb5","JAVIS-Local-v2",CapabilityState.AVAILABLE_OFF,
            "SessionCoordinator audité, absent de cette composition. Aucun mandat CORE émis.","Revue du contrat CORE–LLM.",listOf("INTERFACE")),
        row("LLM","69400fe26d61688262b94cc8a2d5efe20d1da4be","JARVIS-LLM",CapabilityState.BLOCKED,
            "Code source disponible ; génération non raccordée. LAB_CLOUD non appliqué, racine fermée, sûreté native réelle et Android non qualifiés.",
            "Examiner l’audit M51 et corriger les risques sous un nouveau diff à valider.",listOf("CORE"),"LAB_AUTHORITY_NOT_APPROVED"),
        row("NEXUS","be70faa41d202d77eb8cf0cc5c831e10cfb020b5","JARVIS-NEXUS",CapabilityState.PREPARED,
            "Moteur documentaire séparé ; les documents de ce cockpit sont des références locales, sans recherche NEXUS.","Qualifier l’adaptateur en lecture seule.",listOf("CORE")),
        row("SERVICES","8d80d09d6aabf6eac00a7bc6bb4930862bd72812","JARVIS-SERVICES",CapabilityState.PREPARED,
            "Contrats et sources séparés. Les CRUD locaux ne sont pas une connexion SERVICES.","Revue des lectures et confirmations d’écriture.",listOf("CORE")),
        row("VOICE","fe57452fcb33f5b6870070f30ba6c5f1d349c4ba","JARVIS-VOICE",CapabilityState.AVAILABLE_OFF,
            "Prototype séparé ; aucun audio ni modèle vocal chargé, mémoire combinée non qualifiée.","Qualifier les ressources et la lecture avant raccordement.",listOf("CORE")),
        row("AVATAR","0dfeef8ff09d60a68d18398ccb6b2530f3b87df4","JARVIS-INTERFACE",CapabilityState.PREPARED,
            "Contrat minimal et emplacement seulement. Aucun master artistique approuvé ni renderer connecté.","Inventorier les références privées et valider le master séparément.",listOf("INTERFACE")),
        row("EVOLUTION","84ebc2e2b55d48306247fc9045a712294abaf457","JARVIS-VOLUTION-",CapabilityState.PREPARED,
            "Diagnostics et propositions séparés. Aucune auto-modification ou installation autorisée.","Revue des diagnostics en lecture seule.",listOf("CORE")),
        row("UNIFIED","a73c1571f3e1dd0acd50e87b9742fbe2214a93d2","JARVIS-UNIFIED",CapabilityState.PREPARED,
            "Coordination de versions et de preuves, sans runtime Android.","Consulter le manifeste de composition M51.",emptyList())
    ))
}

fun canOpenEvidence(reference: String): Boolean = runCatching {
    val uri=java.net.URI(reference)
    val parts=uri.path.split('/').filter { it.isNotEmpty() }
    uri.scheme == "https" && uri.host == "github.com" && uri.userInfo == null && uri.port == -1 && uri.query == null &&
        parts.size >= 4 && parts[0] == "bubth720" && parts[1] in setOf("JARVIS-INTERFACE","JAVIS-Local-v2","JARVIS-LLM","JARVIS-NEXUS","JARVIS-SERVICES","JARVIS-VOICE","JARVIS-VOLUTION-","JARVIS-UNIFIED") &&
        parts[2] in setOf("tree","blob","commit") && parts[3].matches(Regex("[0-9a-f]{40}"))
}.getOrDefault(false)
