package com.jarvis.interfaceapp.avatar.v2

/** Typed events remain local; no assumptions about CORE/VOICE IPC or phoneme timing. */
sealed interface ExpressionEvent {
    data class Select(val key: PremiumKey) : ExpressionEvent
    data class State(val value: PremiumState) : ExpressionEvent
    data class Expression(val value: PremiumExpression) : ExpressionEvent
    data class Perspective(val value: PremiumPerspective) : ExpressionEvent
    data object Reset : ExpressionEvent
}
data class ExpressionResult(val accepted: Boolean, val key: PremiumKey, val issue: String? = null)
class AvatarExpressionStateMachine(private val manifest: PremiumAvatarManifest) {
    var current: PremiumKey = PremiumKey(); private set
    fun dispatch(event: ExpressionEvent): ExpressionResult {
        val next = when (event) {
            is ExpressionEvent.Select -> event.key
            is ExpressionEvent.State -> manifest.frames.firstOrNull { it.key.state == event.value && it.key.perspective == current.perspective && it.key.expression == PremiumExpression.NEUTRAL }?.key
                ?: manifest.frames.firstOrNull { it.key.state == event.value && it.key.perspective == current.perspective }?.key
            is ExpressionEvent.Expression -> current.copy(expression = event.value)
            is ExpressionEvent.Perspective -> current.copy(perspective = event.value)
            ExpressionEvent.Reset -> PremiumKey()
        }
        if (next == null || manifest.resolve(next) == null) return ExpressionResult(false, current, "ART_ASSET_UNAVAILABLE")
        current = next
        return ExpressionResult(true, current)
    }
}
/** Optional future seam. No implementation, microphone, service or claimed phonetic sync. */
fun interface VoiceVisemePort { fun submit(cue: VoiceVisemeCue): Boolean }
data class VoiceVisemeCue(val viseme: String, val startMillis: Long, val durationMillis: Long)
