package com.stark.jarvislocal.v1a

import com.stark.jarvislocal.InferenceCapabilities
import com.stark.jarvislocal.InferenceRequest
import com.stark.jarvislocal.GenerationLimits
import com.stark.jarvislocal.validateInference
import java.util.UUID

/**
 * Local multi-turn session in RAM. Each request carries the most recent turns that fit the
 * CORE contract budget (validateInference), newest first; older turns are dropped, never cut.
 */
class ConversationHistory(val sessionId: String = UUID.randomUUID().toString(), private val maxTurns: Int = 40) {
    data class Turn(val user: String, val assistant: String)
    private val turns = ArrayDeque<Turn>()

    @Synchronized fun snapshot(): List<Turn> = turns.toList()
    @Synchronized fun record(user: String, assistant: String) {
        turns.addLast(Turn(user, assistant))
        while (turns.size > maxTurns) turns.removeFirst()
    }
    @Synchronized fun clear() = turns.clear()

    /** Builds a request accepted by CORE's own validateInference, or throws its InferenceRejected(BUDGET). */
    @Synchronized fun request(question: String, systemPrompt: String, limits: GenerationLimits, capabilities: InferenceCapabilities): InferenceRequest {
        val kept = ArrayList<Turn>()
        for (turn in turns.reversed()) {
            val candidate = build(question, systemPrompt, limits, listOf(turn) + kept)
            if (fits(candidate, capabilities)) kept.add(0, turn) else break
        }
        return build(question, systemPrompt, limits, kept).also { validateInference(it, capabilities) }
    }

    private fun build(question: String, system: String, limits: GenerationLimits, context: List<Turn>) = InferenceRequest(
        sessionId = sessionId, requestId = UUID.randomUUID().toString(), systemPrompt = system, question = question,
        context = context.joinToString("") { "UTILISATEUR: ${it.user}\nASSISTANT: ${it.assistant}\n" }, limits = limits)

    private fun fits(request: InferenceRequest, capabilities: InferenceCapabilities) =
        runCatching { validateInference(request, capabilities) }.isSuccess
}
