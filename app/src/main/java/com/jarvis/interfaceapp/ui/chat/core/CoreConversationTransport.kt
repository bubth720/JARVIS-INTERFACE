package com.jarvis.interfaceapp.ui.chat.core

import java.nio.charset.CodingErrorAction
import java.nio.CharBuffer
import java.util.UUID
import kotlinx.coroutines.flow.*

/** LOCAL PROPOSAL ONLY. Does not extend CORE-02/UNIFIED wire contracts or authorize IPC. */
enum class CoreTextLink { DISABLED, UNAVAILABLE, READY, UNKNOWN }
enum class CoreTextOrigin { NONE, LABORATORY }
internal fun canonicalUuid(value:String) { require(UUID.fromString(value).toString()==value) }
internal fun textBytes(value:String,limit:Int):Int {
    require(value.length in 1..limit && '\u0000' !in value)
    val bytes=Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value)).remaining()
    require(bytes in 1..limit);return bytes
}
data class ContentKey(val sessionId:String,val requestId:String,val generation:Long) {
    init { canonicalUuid(sessionId);canonicalUuid(requestId);require(generation>=0) }
}
data class SubmitUserText(val submissionId:String,val text:String) {
    init { canonicalUuid(submissionId);textBytes(text,16*1024) }
}
data class ContentAck(val submissionId:String,val key:ContentKey) {
    init { canonicalUuid(submissionId) }
}
sealed interface ContentBody {
    data class AssistantTextChunk(val text:String):ContentBody { init { textBytes(text,16*1024) } }
    data object Completed:ContentBody
    data object Cancelled:ContentBody
    data class Failure(val code:ContentFailure):ContentBody
    data class ToolPending(val publicToolId:String):ContentBody {
        init { require(publicToolId.matches(Regex("[A-Za-z0-9._-]{1,80}"))) }
    }
}
enum class ContentFailure { OWNER_FAILURE, RESOURCE_UNCONFIRMED, CONTRACT_REFUSED }
data class CoreMessageEnvelope(val eventId:String,val key:ContentKey,val sequence:Long,val body:ContentBody,
    val proposalVersion:Int=1) {
    init { canonicalUuid(eventId);require(sequence>0 && proposalVersion==1) }
}
interface CoreConversationTransport {
    val origin:CoreTextOrigin
    val connection:StateFlow<CoreTextLink>
    val updates:Flow<CoreMessageEnvelope>
    suspend fun submit(value:SubmitUserText):ContentAck?
    /** True means logical request accepted, never native unload or a resource lease. */
    suspend fun stop(key:ContentKey):Boolean
    fun close() {}
}
class UnavailableCoreConversationTransport:CoreConversationTransport {
    override val origin=CoreTextOrigin.NONE
    override val connection=MutableStateFlow(CoreTextLink.UNAVAILABLE).asStateFlow()
    override val updates=emptyFlow<CoreMessageEnvelope>()
    override suspend fun submit(value:SubmitUserText):ContentAck?=null
    override suspend fun stop(key:ContentKey)=false
}
