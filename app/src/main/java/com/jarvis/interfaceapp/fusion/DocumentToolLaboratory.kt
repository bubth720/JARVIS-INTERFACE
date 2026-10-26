package com.jarvis.interfaceapp.fusion

import java.math.BigDecimal
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal fun digest(text:String)=MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") {"%02x".format(it)}
enum class LabPhase { EMPTY, SOURCE, PREVIEW, PREPARED, DISPATCHED, COMMITTED, REFUSED, UNKNOWN }
data class LabCitation(val uri:String,val version:Int,val page:Int,val start:Int,val end:Int,val hash:String)
data class LabReview(val token:String,val generation:Long,val payload:String,val digest:String,val destination:String,val expiresAtMs:Long)
data class LabState(val enabled:Boolean=false,val phase:LabPhase=LabPhase.EMPTY,val generation:Long=0,
    val citation:LabCitation?=null,val total:String?=null,val review:LabReview?=null,
    val receiptId:String?=null,val detail:String="NON_CONNECTÉ · laboratoire fermé")

/** Synthetic RAM-only interaction model. No ContentResolver, SERVICES worker, grant or file write. */
class DocumentToolLaboratory(private val now:()->Long={System.nanoTime()/1_000_000}) {
    private val mutable=MutableStateFlow(LabState());val state=mutable.asStateFlow()
    private var granted=false
    private val consumed=mutableSetOf<String>()
    companion object {
        const val SOURCE_URI="content://lab.synthetic/source/fixture.txt"
        const val DOCUMENT="Document synthétique LAB. Aucun renseignement personnel."
        const val CSV="amount\n0.10\n0.20\n"
    }
    @Synchronized fun open() {reset(true)}
    @Synchronized fun close() {reset(false)}
    private fun reset(enabled:Boolean) {
        granted=false;consumed.clear();mutable.value=LabState(enabled=enabled,generation=mutable.value.generation+1,
            detail=if(enabled) "LAB · aucune connexion CORE, aucun fichier réel" else "NON_CONNECTÉ · laboratoire fermé")
    }
    @Synchronized fun chooseSyntheticSource():Boolean {
        val s=mutable.value;if(!s.enabled || s.phase!=LabPhase.EMPTY)return false
        granted=true
        mutable.value=s.copy(phase=LabPhase.SOURCE,citation=LabCitation(SOURCE_URI,1,1,0,DOCUMENT.length,digest(DOCUMENT)),
            detail="LAB · sélection volontaire simulée ; URI fictive non ouvrable, SAF réel non exécuté")
        return true
    }
    fun citationResolvesInFixture():Boolean {
        val c=mutable.value.citation ?: return false
        return granted && c.uri==SOURCE_URI && c.version==1 && c.page==1 && c.start==0 && c.end==DOCUMENT.length && c.hash==digest(DOCUMENT)
    }
    @Synchronized fun preview():Boolean {
        val s=mutable.value;if(s.phase!=LabPhase.SOURCE || !citationResolvesInFixture())return false
        val total=CSV.lineSequence().drop(1).filter {it.isNotBlank()}.map {BigDecimal(it)}.fold(BigDecimal.ZERO,BigDecimal::add).setScale(2).toPlainString()
        mutable.value=s.copy(phase=LabPhase.PREVIEW,total=total,detail="LAB · calcul décimal local illustratif ; vrais outils SERVICES non exécutés")
        return true
    }
    @Synchronized fun prepare():LabReview? {
        val s=mutable.value;if(s.phase!=LabPhase.PREVIEW || !granted)return null
        val token=UUID.randomUUID().toString();val destination="lab://new-export/$token.csv"
        val payload="sum\n${s.total}\n"
        val hash=digest("lab.csv.sum/2\n${s.citation!!.hash}\n$payload\n$destination\n${s.generation}")
        val review=LabReview(token,s.generation,payload,hash,destination,now()+30_000)
        mutable.value=s.copy(phase=LabPhase.PREPARED,review=review,detail="LAB · décision exacte à usage unique, aucun droit réel")
        return review
    }
    @Synchronized fun cancelReview() {
        val s=mutable.value;if(s.phase==LabPhase.PREPARED)mutable.value=s.copy(phase=LabPhase.PREVIEW,review=null,detail="LAB · confirmation annulée, aucun effet")
    }
    @Synchronized fun confirm(review:LabReview,loseAck:Boolean=false):Boolean {
        val s=mutable.value
        if(s.phase!=LabPhase.PREPARED || s.review!=review || !granted || review.generation!=s.generation || now()>=review.expiresAtMs || !consumed.add(review.token)) {
            if(s.phase==LabPhase.PREPARED)mutable.value=s.copy(phase=LabPhase.REFUSED,review=null,detail="LAB · confirmation périmée ou refusée, aucun effet")
            return false
        }
        val receipt=UUID.randomUUID().toString()
        mutable.value=s.copy(phase=LabPhase.DISPATCHED,receiptId=receipt,review=null)
        // Illustrate the owner receipt only. No destination is opened or written by this UI.
        mutable.value=mutable.value.copy(phase=if(loseAck)LabPhase.UNKNOWN else LabPhase.COMMITTED,
            detail=if(loseAck) "LAB · ACK perdu : UNKNOWN, aucun retry automatique" else "LAB · reçu COMMITTED simulé, zéro fichier écrit")
        return true
    }
    @Synchronized fun revoke() {
        granted=false;val s=mutable.value
        mutable.value=if(s.phase in setOf(LabPhase.COMMITTED,LabPhase.UNKNOWN))s.copy(generation=s.generation+1,detail="LAB · révocation après engagement ; aucun rollback promis")
        else s.copy(phase=LabPhase.REFUSED,generation=s.generation+1,review=null,detail="LAB · grant révoqué avant engagement, aucun effet")
    }
}
