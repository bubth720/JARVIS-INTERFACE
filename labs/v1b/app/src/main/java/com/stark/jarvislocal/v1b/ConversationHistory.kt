package com.stark.jarvislocal.v1b

import java.util.UUID

/**
 * Local multi-turn session in RAM (V1-B). Turns are stored as (user, assistant) pairs and sent to
 * the engine as real ChatML roles; the window that fits the context is chosen by exact token count
 * in [A02LanguageModel.window], newest first. Previous answers are never re-sent as user text.
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
}
