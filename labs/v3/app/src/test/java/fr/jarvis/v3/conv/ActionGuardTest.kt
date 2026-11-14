package fr.jarvis.v3.conv

import org.junit.Assert.*
import org.junit.Test

class ActionGuardTest {
    @Test fun measuredFalseClaimsAreDetected() {
        // Answers measured in CONVERSATION-01 study runs 43590133529 / 43592731686 (0.6B and 1.7B).
        val q = "Allume la lumière du salon."
        assertNotNull(ActionGuard.requestedAction(q))
        assertTrue(ActionGuard.claimsDone(q, "La lumière du salon est allumée. Qu'en pensez-vous ?"))
        assertTrue(ActionGuard.claimsDone(q, "Je l'ouvre, voici la lumière du salon."))
        assertTrue(ActionGuard.claimsDone("Envoie un e-mail à mon patron pour dire que je serai en retard.", "C'est fait, j'ai envoyé le message à ton patron."))
        assertFalse(ActionGuard.claimsDone(q, "Bonjour ! Je ne peux pas allumer la lumière du salon. Je suis un assistant local."))
        assertFalse(ActionGuard.claimsDone("Envoie un e-mail à mon patron.", "Je ne peux pas envoyer d'e-mails, mais je peux t'aider à l'écrire."))
    }

    @Test fun questionsAreNotActionRequestsAndInstructionIsTargeted() {
        assertNull(ActionGuard.requestedAction("Comment allumer un feu de camp ?"))
        assertNull(ActionGuard.requestedAction("Quelle est la capitale de l'Italie ?"))
        assertFalse(ActionGuard.claimsDone("Quelle est la capitale de l'Italie ?", "C'est fait : Rome."))
        assertTrue(ActionGuard.instruction("Allume la lumière du salon.").contains("Tu ne peux pas l'exécuter"))
        assertEquals("", ActionGuard.instruction("Raconte-moi une blague."))
        val ctx = ConversationContext(PersonaSettings.ATHENA)
        assertTrue(ctx.instructionFor(emptyList(), "Allume la lumière du salon.").contains("sans jamais affirmer qu'elle est faite"))
    }
}
