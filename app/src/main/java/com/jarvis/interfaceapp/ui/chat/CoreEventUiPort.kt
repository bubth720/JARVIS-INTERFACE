package com.jarvis.interfaceapp.ui.chat

import java.util.UUID
import kotlinx.coroutines.flow.*

/** UI rendering projection of pinned CORE-02 metadata, NOT a replacement wire/authorization contract. */
enum class CoreUiKind { USER_INPUT, THINKING, RESPONSE_CHUNK, RESPONSE_COMPLETED, TOOL_PENDING,
    SPEECH_REQUESTED, CANCEL_REQUESTED, CANCELLED, ERROR }
data class CoreRequestKey(val sessionId: String, val requestId: String) {
    init { listOf(sessionId,requestId).forEach { require(UUID.fromString(it).toString()==it) } }
}
enum class CoreErrorCode { CORE_FAILURE, RESOURCE_UNCONFIRMED, MISSING_RESULT }
data class CoreMetadata(val key: CoreRequestKey, val eventId: String, val kind: CoreUiKind,
                        val generation: Long, val sequence: Long, val version: Int = 1,
                        val chunkIndex: Int? = null, val errorCode: CoreErrorCode? = null, val elapsedMs: Long = 0) {
    init { require(version==1 && generation>=0 && sequence>0 && elapsedMs>=0); require(UUID.fromString(eventId).toString()==eventId)
        require((kind==CoreUiKind.RESPONSE_CHUNK)==(chunkIndex!=null) && (chunkIndex==null || chunkIndex>=0))
        require((kind==CoreUiKind.ERROR)==(errorCode!=null)) }
}
enum class PortConnection { DISABLED, UNAVAILABLE, DEGRADED, CONNECTED, DEMONSTRATION }
sealed interface CoreUiUpdate {
    data class Metadata(val value: CoreMetadata) : CoreUiUpdate
    /** Separate from CORE metadata: only the explicit demo port may emit synthetic display text. */
    data class DemoText(val key: CoreRequestKey, val text: String) : CoreUiUpdate {
        init { require(text.length in 1..4096 && '\u0000' !in text) }
    }
}
sealed interface SubmissionResult {
    data class Accepted(val key: CoreRequestKey, val generation: Long) : SubmissionResult
    data class Refused(val reason: String) : SubmissionResult
}
/** Future host supplies business UUIDs in its ACK; UI must not pretend its local ID is a JNI lease. */
interface CoreEventUiPort {
    val connection: StateFlow<PortConnection>
    val updates: Flow<CoreUiUpdate>
    suspend fun submit(text: String): SubmissionResult
    suspend fun cancel(key: CoreRequestKey): Boolean
    fun close() {}
}
class UnavailableCoreEventUiPort : CoreEventUiPort {
    override val connection = MutableStateFlow(PortConnection.UNAVAILABLE).asStateFlow()
    override val updates: Flow<CoreUiUpdate> = emptyFlow()
    override suspend fun submit(text: String) = SubmissionResult.Refused("CORE NON CONNECTÉ — aucun envoi ni calcul effectué")
    override suspend fun cancel(key: CoreRequestKey) = false
}
