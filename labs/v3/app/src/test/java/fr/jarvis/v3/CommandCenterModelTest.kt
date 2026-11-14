package fr.jarvis.v3

import fr.jarvis.v3.model.Blueprint
import fr.jarvis.v3.model.CommandModule
import fr.jarvis.v3.model.ModuleStatus
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class CommandCenterModelTest {
    @Test fun everyScreenOfThePdfExistsAndNothingUnbuiltIsDeclaredOperational() {
        assertEquals(listOf("AI CORE", "VOICE CENTER", "MEMORY VAULT", "NEXUS", "MISSIONS", "AGENTS", "AUTOMATIONS", "SECURITY", "EVOLUTION LAB", "SYSTEM BLUEPRINT"),
            CommandModule.entries.map { it.label })
        // No missions, agents or automations engine is compiled in this build: never "operational".
        for (m in listOf(CommandModule.MISSIONS, CommandModule.AGENTS, CommandModule.AUTOMATIONS)) assertEquals(ModuleStatus.TO_CONNECT, m.status)
        assertTrue(CommandModule.entries.all { it.note.isNotBlank() })
    }

    @Test fun blueprintPinsAreTheCommitsTheWorkflowReallyBuilds() {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, ".github/workflows/v3-cockpit.yml").isFile) dir = dir.parentFile
        val wf = File(requireNotNull(dir) { "workflow not found" }, ".github/workflows/v3-cockpit.yml").readText()
        for ((name, repo) in listOf("CORE" to "JAVIS-Local-v2", "LLM A02" to "JARVIS-LLM", "VOICE" to "JARVIS-VOICE", "NEXUS" to "JARVIS-NEXUS")) {
            val pin = Blueprint.NODES.single { it.name == name }.commit
            assertTrue("$name @ $pin must be the commit pinned for $repo", Regex("repository: bubth720/$repo, ref: $pin").containsMatchIn(wf))
        }
        assertFalse(Blueprint.NODES.single { it.name == "SERVICES" }.connected)
        assertTrue(Blueprint.EDGES.all { (a, b) -> Blueprint.NODES.any { it.name == a } && Blueprint.NODES.any { it.name == b } })
    }
}
