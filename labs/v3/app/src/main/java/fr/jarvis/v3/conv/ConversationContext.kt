package fr.jarvis.v3.conv

import com.stark.jarvislocal.v1b.ConversationHistory

/**
 * What JARVIS knows when it answers one turn (CONVERSATION-01 §3–4), rebuilt deterministically from the
 * conversation itself at every turn: the persona the owner chose, the facts the user stated in this
 * conversation (with his corrections), and the durable memories he explicitly confirmed. Nothing here is
 * inferred or stored on its own: the working memory is recomputed from the exchanges, so deleting a
 * conversation deletes its facts, and a resumed conversation recovers them.
 */
class ConversationContext(
    @Volatile var persona: PersonaSettings = PersonaSettings.ATHENA,
    /** Confirmed durable memories relevant to the question (CORE consent workflow); empty by default. */
    private val confirmedMemories: (String) -> String = { "" },
    private val maxFacts: Int = 8,
) {
    /** Facts stated in [history] + [question], corrections applied. */
    fun facts(history: List<ConversationHistory.Turn>, question: String): WorkingMemory =
        WorkingMemory(maxFacts).also { m -> history.forEach { m.observe(it.user) }; m.observe(question) }

    /** Subordinate instruction for this turn (persona routing + facts + confirmed memories). */
    fun instructionFor(history: List<ConversationHistory.Turn>, question: String): String =
        PersonaRenderer.instruction(persona, question, facts(history, question).render(), confirmedMemories(question))
}
