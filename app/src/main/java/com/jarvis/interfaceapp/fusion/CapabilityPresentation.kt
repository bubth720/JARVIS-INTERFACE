package com.jarvis.interfaceapp.fusion

import java.util.UUID

enum class CapabilityId { CORE, NEXUS_RAG, SERVICES_CSV, SERVICES_EXPORT, LLM, VOICE, AVATAR }
enum class CapabilityStatus { READY, UNSUPPORTED, UNKNOWN, BLOCKED }
enum class HostOrigin { REAL_HOST, LAB }
data class CapabilityEvidence(val id:CapabilityId,val status:CapabilityStatus,val code:String) {
    init {require(code.matches(Regex("[A-Z0-9_]{1,80}")))}
}
/** Local presentation proposal /2, not an extension of UNIFIED v1 or an Android authority. */
data class HostCapabilityReport(val epoch:String,val generation:Long,val sequence:Long,
    val observedAtMs:Long,val origin:HostOrigin,val capabilities:List<CapabilityEvidence>,val version:Int=2)
fun interface HostEvidenceVerifier {
    /** Future composition must authenticate the exact report plus UID/certificate/grant out of band. */
    fun verify(report:HostCapabilityReport):Boolean
}
data class CapabilityPresentation(val id:CapabilityId,val status:CapabilityStatus,
    val connected:Boolean,val detail:String)

/** Stateless: callers must own the epoch and replay watermark. Production has no verifier/transport. */
object CapabilityProjection {
    fun disconnected()=CapabilityId.entries.map {CapabilityPresentation(it,CapabilityStatus.UNKNOWN,false,"NON_CONNECTÉ · aucune preuve hôte")}
    fun evaluate(report:HostCapabilityReport,verifier:HostEvidenceVerifier,epoch:String,generation:Long,
        afterSequence:Long,nowMs:Long):List<CapabilityPresentation> {
        val valid=report.version==2 && runCatching {UUID.fromString(report.epoch).toString()==report.epoch}.getOrDefault(false) &&
            report.epoch==epoch && report.generation==generation && generation>=0 && afterSequence>=0 && report.sequence>afterSequence &&
            report.sequence>0 && report.observedAtMs>=0 && report.observedAtMs<=nowMs && nowMs-report.observedAtMs<=30_000 &&
            report.origin==HostOrigin.REAL_HOST && report.capabilities.size<=CapabilityId.entries.size &&
            report.capabilities.map {it.id}.distinct().size==report.capabilities.size &&
            runCatching {verifier.verify(report)}.getOrDefault(false)
        if(!valid)return disconnected()
        return CapabilityId.entries.map {id->
            val evidence=report.capabilities.singleOrNull {it.id==id}
            CapabilityPresentation(id,evidence?.status ?: CapabilityStatus.UNKNOWN,true,evidence?.code ?: "NOT_REPORTED")
        }
    }
}
