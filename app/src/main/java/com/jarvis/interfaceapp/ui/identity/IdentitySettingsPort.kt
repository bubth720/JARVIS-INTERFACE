package com.jarvis.interfaceapp.ui.identity

import java.security.MessageDigest
import java.util.UUID

enum class IdentityConnection { UNAVAILABLE, LABORATORY, AUTHORIZED_OWNER }
enum class PersonaContext { DAILY, WORK, URGENT }
enum class PersonaPreset { ATHENA, NEUTRAL }
enum class PersonaStyle { ELEGANT, PLAIN }
enum class SimulatedInterest { HISTORY, MYTHOLOGY, PHILOSOPHY, SCIENCE, STRATEGY, ARTS, LITERATURE }
enum class LexicalToken(val text: String) { BONJOUR("Bonjour"), SALUT("Salut"), MERCI("Merci"), VOLONTIERS("Volontiers") }
data class LexicalRule(val source: LexicalToken, val target: LexicalToken) {
    init { require(source != target) }
}
data class PersonaParameters(val frankness: Int, val humour: Int, val initiative: Int,
    val tutoiement: Boolean, val style: PersonaStyle) {
    init { require(frankness in 0..100 && humour in 0..100 && initiative in 0..100) }
}
data class ProfileDraft(val name: String, val preset: PersonaPreset, val defaults: PersonaParameters,
    val overrides: Map<PersonaContext, PersonaParameters> = emptyMap(),
    val interests: Set<SimulatedInterest> = emptySet(), val rules: List<LexicalRule> = emptyList()) {
    init {
        require(name.isNotBlank() && name.toByteArray(Charsets.UTF_8).size <= 120 && name.none { it.isISOControl() })
        require(rules.size <= 4 && rules.map { it.source }.distinct().size == rules.size)
        require(rules.none { r -> rules.any { it.source == r.target } })
    }
    fun parameters(context: PersonaContext): PersonaParameters = overrides[context] ?: defaults
    companion object {
        fun preset(value: PersonaPreset) = if (value == PersonaPreset.ATHENA)
            ProfileDraft("Athéna", value, PersonaParameters(75,60,100,true,PersonaStyle.ELEGANT))
        else ProfileDraft("Neutre", value, PersonaParameters(50,0,0,true,PersonaStyle.PLAIN))
    }
}
data class ProfileReference(val id: String, val revision: Long, val digest: String) {
    init { require(UUID.fromString(id).toString() == id && revision in 1..64 && digest.matches(Regex("[0-9a-f]{64}"))) }
}
data class ProfileView(val reference: ProfileReference, val draft: ProfileDraft)
data class IdentitySnapshot(val epoch: Long, val profiles: List<ProfileView>, val selectedId: String?)
data class ProfileProposal(val id: String, val expected: ProfileReference, val epoch: Long,
    val approvalDigest: String, val draft: ProfileDraft, val differences: List<ProfileDifference>,
    val previews: List<ReadOnlyPersonalityPreview>, val restoreRevision: Long?, val createdElapsedMs: Long, val expiresElapsedMs: Long)
sealed interface IdentityAck {
    data class Applied(val snapshot: IdentitySnapshot, val durableOwnerAck: Boolean) : IdentityAck
    data class Refused(val reason: String) : IdentityAck
    data object Unknown : IdentityAck
}
/** UI projection, not a frozen CORE wire schema. Only the owner can provide a durable ACK. */
interface IdentitySettingsPort {
    val connection: IdentityConnection
    suspend fun read(): IdentitySnapshot
    suspend fun propose(expected: ProfileReference, epoch: Long, replacement: ProfileDraft, restoreRevision: Long? = null): ProfileProposal
    suspend fun approve(id: String, exactDigest: String, epoch: Long): IdentityAck
    suspend fun reject(id: String)
    suspend fun history(id: String): List<ProfileView>
    fun close() {}
}
class UnavailableIdentitySettingsPort : IdentitySettingsPort {
    override val connection = IdentityConnection.UNAVAILABLE
    override suspend fun read() = IdentitySnapshot(0,emptyList(),null)
    override suspend fun propose(expected: ProfileReference, epoch: Long, replacement: ProfileDraft, restoreRevision: Long?): ProfileProposal = error("OWNER_UNAVAILABLE")
    override suspend fun approve(id: String, exactDigest: String, epoch: Long) = IdentityAck.Refused("OWNER_UNAVAILABLE")
    override suspend fun reject(id: String) = Unit
    override suspend fun history(id: String) = emptyList<ProfileView>()
}
internal fun sha256(value: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) }
/** Unambiguous UI-lab digest only; never passed as a CORE codec/consent signature. */
internal fun uiDigest(vararg parts: String): String = sha256(parts.joinToString("") { "${it.toByteArray(Charsets.UTF_8).size}:$it" }.toByteArray(Charsets.UTF_8))
internal fun draftDigest(draft: ProfileDraft): String = uiDigest("jarvis.ui.identity-lab/1",draft.name,draft.preset.name,
    draft.defaults.toString(), PersonaContext.entries.joinToString { "${it.name}:${draft.overrides[it]}" },
    draft.interests.map { it.name }.sorted().joinToString(),draft.rules.joinToString())

internal fun freezeDraft(value: ProfileDraft): ProfileDraft = value.copy(
    overrides=java.util.Collections.unmodifiableMap(LinkedHashMap(value.overrides)),
    interests=java.util.Collections.unmodifiableSet(LinkedHashSet(value.interests)),
    rules=java.util.Collections.unmodifiableList(ArrayList(value.rules)))
