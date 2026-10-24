package com.jarvis.interfaceapp.ui.memory

import com.jarvis.interfaceapp.ui.identity.uiDigest
import java.util.UUID
import kotlinx.serialization.json.*

enum class UiMemoryScope { GENERAL, PREFERENCE, SENSITIVE }
data class UiMemoryReference(val id:String,val revision:Long,val digest:String)
data class UiMemoryRecord(val reference:UiMemoryReference,val content:String,val scope:UiMemoryScope,
    val suspended:Boolean,val source:String,val confirmedAtMs:Long)
data class UiMemoryCandidate(val id:String,val session:String,val epoch:Long,val content:String,val scope:UiMemoryScope,
    val digest:String,val createdMs:Long,val expiresMs:Long)
data class UiMemoryExport(val id:String,val session:String,val epoch:Long,val json:String,val digest:String,
    val references:Set<UiMemoryReference>,val createdMs:Long,val expiresMs:Long)
sealed interface MemoryUiAck {
    data class LaboratoryApplied(val records:List<UiMemoryRecord>):MemoryUiAck
    data class OwnerApplied(val records:List<UiMemoryRecord>):MemoryUiAck
    data class Refused(val reason:String):MemoryUiAck
    data object Unknown:MemoryUiAck
}
interface MemoryConsentUiPort {
    val laboratory:Boolean
    suspend fun visible(session:String,allowProjection:Boolean):List<UiMemoryRecord>
    suspend fun propose(session:String,exactContent:String,scope:UiMemoryScope):UiMemoryCandidate
    suspend fun decide(candidate:UiMemoryCandidate,displayedContent:String,accept:Boolean):MemoryUiAck
    suspend fun suspendRecord(session:String,expected:UiMemoryReference,suspended:Boolean):MemoryUiAck
    suspend fun delete(session:String,expected:UiMemoryReference):MemoryUiAck
    suspend fun previewExport(session:String,selected:Set<UiMemoryReference>):UiMemoryExport
    suspend fun approveExport(preview:UiMemoryExport,exactDigest:String):ByteArray
    fun revoke(session:String)
    fun close() {}
}
class UnavailableMemoryConsentUiPort:MemoryConsentUiPort {
    override val laboratory=false
    override suspend fun visible(session:String,allowProjection:Boolean)=emptyList<UiMemoryRecord>()
    override suspend fun propose(session:String,exactContent:String,scope:UiMemoryScope):UiMemoryCandidate=error("MEMORY_OWNER_UNAVAILABLE")
    override suspend fun decide(candidate:UiMemoryCandidate,displayedContent:String,accept:Boolean)=MemoryUiAck.Refused("MEMORY_OWNER_UNAVAILABLE")
    override suspend fun suspendRecord(session:String,expected:UiMemoryReference,suspended:Boolean)=MemoryUiAck.Refused("MEMORY_OWNER_UNAVAILABLE")
    override suspend fun delete(session:String,expected:UiMemoryReference)=MemoryUiAck.Refused("MEMORY_OWNER_UNAVAILABLE")
    override suspend fun previewExport(session:String,selected:Set<UiMemoryReference>):UiMemoryExport=error("MEMORY_OWNER_UNAVAILABLE")
    override suspend fun approveExport(preview:UiMemoryExport,exactDigest:String):ByteArray=error("MEMORY_OWNER_UNAVAILABLE")
    override fun revoke(session:String)=Unit
}
/** Volatile fixture only. No constructor creates confirmed memories; every candidate needs explicit consent. */
class LaboratoryMemoryConsentUiPort(private val elapsedMs:()->Long={System.nanoTime()/1_000_000},
    private val wallMs:()->Long=System::currentTimeMillis):MemoryConsentUiPort {
    override val laboratory=true
    private val rows=linkedMapOf<String,UiMemoryRecord>()
    private val candidates=linkedMapOf<String,UiMemoryCandidate>()
    private val exports=linkedMapOf<String,UiMemoryExport>()
    private val sessions=mutableSetOf<String>()
    private var epoch=0L
    private var closed=false
    private fun allowed(session:String) { require(!closed && session in sessions) { "MEMORY_SESSION_SUSPENDED" } }
    private fun now():Long=elapsedMs().also { require(it>=0 && it<=Long.MAX_VALUE-90_000) }
    override suspend fun visible(session:String,allowProjection:Boolean):List<UiMemoryRecord> {
        if(closed) return emptyList()
        require(UUID.fromString(session).toString()==session)
        if(!allowProjection) { revoke(session);return emptyList() }
        sessions.add(session)
        return rows.values.filter { it.scope!=UiMemoryScope.SENSITIVE }.take(200)
    }
    override suspend fun propose(session:String,exactContent:String,scope:UiMemoryScope):UiMemoryCandidate {
        allowed(session);require(scope!=UiMemoryScope.SENSITIVE) { "SENSITIVE_SCOPE_NOT_QUALIFIED" }
        require(exactContent.isNotBlank() && exactContent.length<=1500 && exactContent.toByteArray().size<=6000)
        require(exactContent.none { it.code==127 || it.code<32 && it !in "\n\r\t" })
        require(candidates.size<32 && rows.size<200)
        val id=UUID.randomUUID().toString();val n=now()
        return UiMemoryCandidate(id,session,epoch,exactContent,scope,uiDigest(id,session,epoch.toString(),exactContent,scope.name),n,n+90_000).also { candidates[id]=it }
    }
    override suspend fun decide(candidate:UiMemoryCandidate,displayedContent:String,accept:Boolean):MemoryUiAck {
        if(closed || candidate.session !in sessions) return MemoryUiAck.Refused("MEMORY_SESSION_SUSPENDED")
        val c=candidates[candidate.id]?:return MemoryUiAck.Refused("CANDIDATE_ABSENT_OR_CONSUMED")
        if(c!=candidate || displayedContent!=c.content) return MemoryUiAck.Refused("PREVIEW_MISMATCH")
        val n=now()
        if(n<c.createdMs || n>=c.expiresMs) { candidates.remove(c.id);return MemoryUiAck.Refused("CONSENT_EXPIRED") }
        if(c.epoch!=epoch) { candidates.remove(c.id);return MemoryUiAck.Refused("REVISION_CONFLICT") }
        candidates.remove(c.id)
        if(accept) {
            val r=UiMemoryReference(UUID.randomUUID().toString(),1,uiDigest(c.content))
            rows[r.id]=UiMemoryRecord(r,c.content,c.scope,false,"LABORATORY_USER_CONFIRMED_NOT_CORE",wallMs());epoch++
        }
        return MemoryUiAck.LaboratoryApplied(rows.values.toList())
    }
    override suspend fun suspendRecord(session:String,expected:UiMemoryReference,suspended:Boolean):MemoryUiAck {
        allowed(session);val r=rows[expected.id]?:return MemoryUiAck.Refused("MEMORY_ABSENT")
        if(r.reference!=expected) return MemoryUiAck.Refused("REVISION_CONFLICT")
        rows[expected.id]=r.copy(reference=r.reference.copy(revision=r.reference.revision+1),suspended=suspended);epoch++
        return MemoryUiAck.LaboratoryApplied(rows.values.toList())
    }
    override suspend fun delete(session:String,expected:UiMemoryReference):MemoryUiAck {
        allowed(session);val r=rows[expected.id]?:return MemoryUiAck.Refused("MEMORY_ABSENT")
        if(r.reference!=expected) return MemoryUiAck.Refused("REVISION_CONFLICT")
        rows.remove(expected.id);epoch++;return MemoryUiAck.LaboratoryApplied(rows.values.toList())
    }
    override suspend fun previewExport(session:String,selected:Set<UiMemoryReference>):UiMemoryExport {
        allowed(session);require(selected.size in 1..10 && exports.size<16)
        val selectedRows=selected.sortedBy { it.id }.map { ref ->
            requireNotNull(rows[ref.id]).also { require(it.reference==ref && !it.suspended && it.scope!=UiMemoryScope.SENSITIVE) }
        }
        val id=UUID.randomUUID().toString();val n=now()
        val json=buildJsonObject {
            put("format","jarvis.ui.memory-lab-export/1");put("authority","UNTRUSTED_IMPORT_REQUIRES_FRESH_CONSENT")
            putJsonArray("entries") { selectedRows.forEach { r -> add(buildJsonObject {
                put("id",r.reference.id);put("revision",r.reference.revision);put("content",r.content)
                put("scope",r.scope.name);put("source",r.source);put("confirmedAtMs",r.confirmedAtMs)
            }) } }
        }.toString()
        require(json.toByteArray().size<=65536)
        return UiMemoryExport(id,session,epoch,json,uiDigest(id,session,epoch.toString(),json),selected.toSet(),n,n+90_000).also { exports[id]=it }
    }
    override suspend fun approveExport(preview:UiMemoryExport,exactDigest:String):ByteArray {
        allowed(preview.session);val p=requireNotNull(exports[preview.id]) { "EXPORT_PREVIEW_ABSENT" }
        require(p==preview && p.digest==exactDigest) { "EXPORT_PREVIEW_MISMATCH" }
        val n=now();require(n>=p.createdMs && n<p.expiresMs && p.epoch==epoch) { "EXPORT_STALE" }
        p.references.forEach { ref -> require(rows[ref.id]?.let { it.reference==ref && !it.suspended && it.scope!=UiMemoryScope.SENSITIVE }==true) }
        exports.remove(p.id);return p.json.toByteArray()
    }
    override fun revoke(session:String) {
        sessions.remove(session);candidates.entries.removeAll { it.value.session==session };exports.entries.removeAll { it.value.session==session }
    }
    override fun close() { closed=true;sessions.clear();candidates.clear();exports.clear();rows.clear() }
}
