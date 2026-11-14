package fr.jarvis.v3

import fr.jarvis.m55d.RenderMode
import fr.jarvis.v3.model.*
import org.junit.Assert.*
import org.junit.Test

class V3ModelTest {
    @Test fun geometryBudgetsGrowWithProfileAndLinksAreStable() {
        val eco = HoloGeometry(HoloDetail.of(RenderMode.ECONOMY)); val cin = HoloGeometry(HoloDetail.of(RenderMode.CINEMATIC))
        assertTrue(eco.nodes.size < cin.nodes.size && eco.shell.size < cin.shell.size)
        assertFalse(HoloDetail.of(RenderMode.ECONOMY).glow)
        for (g in listOf(eco, cin)) {
            assertTrue(g.links.all { (a, b) -> a < b && b < g.nodes.size })
            assertTrue(g.altLinks.none { it in g.links.toSet() })
            assertEquals(6, g.anchors.distinct().size)
            assertTrue(g.links.size >= g.nodes.size) // every node linked at least once on average
        }
        assertEquals(HoloGeometry(HoloDetail.of(RenderMode.BALANCED)).links, HoloGeometry(HoloDetail.of(RenderMode.BALANCED)).links)
    }

    @Test fun audioLevelIsGatedAndSmoothed() {
        assertEquals(0f, HoloGeometry.smoothLevel(0f, 0.05f, 0.016f), 0f)              // background noise: no motion
        val a = HoloGeometry.smoothLevel(0f, 0.8f, 0.016f); assertTrue(a > 0f && a < 0.8f) // smoothed, no jump
        var v = 0f; repeat(60) { v = HoloGeometry.smoothLevel(v, 0.8f, 0.016f) }; assertEquals(0.8f, v, 0.01f)
        assertEquals(4f, HoloGeometry.capVelocity(40f), 0f); assertEquals(-4f, HoloGeometry.capVelocity(-9f), 0f)
        assertTrue((0..100).map { HoloGeometry.breath(it / 10f) }.all { it in 0f..1f })
    }

    @Test fun visualStateDerivesFromEnginePhaseOnly() {
        assertEquals(HoloState.IDLE, SphereStateAdapter.resolve(null, false, null))
        assertEquals(HoloState.LISTENING, SphereStateAdapter.resolve("LISTENING", false, null))
        assertEquals(HoloState.TRANSCRIBING, SphereStateAdapter.resolve("TRANSCRIBING", false, null))
        assertEquals(HoloState.THINKING, SphereStateAdapter.resolve("THINKING", false, null))
        assertEquals(HoloState.STREAMING, SphereStateAdapter.resolve("THINKING", true, null))
        assertEquals(HoloState.SPEAKING, SphereStateAdapter.resolve("SPEAKING", false, null))
        assertEquals(HoloState.ERROR, SphereStateAdapter.resolve("READY", false, 1_000))
        assertEquals(HoloState.IDLE, SphereStateAdapter.resolve("READY", false, 5_000)) // temporary accent, back to stable
        assertFalse(HoloState.entries.map(SphereStateAdapter::status).any { it.contains('%') })
    }

    /** V3-FIX-01: still at rest, listening, transcribing, thinking and on error; animated only while answering. */
    @Test fun sphereAnimatesOnlyWhileJarvisAnswers() {
        val still = listOf(HoloState.IDLE, HoloState.LISTENING, HoloState.TRANSCRIBING, HoloState.THINKING, HoloState.ERROR)
        assertTrue(still.none(SphereStateAdapter::animates))
        assertTrue(listOf(HoloState.STREAMING, HoloState.SPEAKING, HoloState.RESPONDING).all(SphereStateAdapter::animates))
        assertEquals(HoloState.RESPONDING, SphereStateAdapter.resolve("READY", false, null, 1_000))
        assertEquals(HoloState.IDLE, SphereStateAdapter.resolve("READY", false, null, 3_000))    // stops after the answer
        assertEquals(HoloState.ERROR, SphereStateAdapter.resolve("READY", false, 500, 500))       // error accent wins, still
        assertEquals(HoloState.THINKING, SphereStateAdapter.resolve("THINKING", false, null, 100))
        assertEquals(12, SphereStateAdapter.fpsCap(HoloState.STREAMING, 30))                       // light while the LLM writes
        assertEquals(30, SphereStateAdapter.fpsCap(HoloState.SPEAKING, 30))
    }

    /** V3-FIX-01: the lowest radial item and its label stay above the dock (anchor centre − 30 dp). */
    @Test fun radialMenuNeverCoversTheInputField() {
        val ys = RadialGeometry.positions(5, RadialGeometry.RADIUS_DP).map { it.second - RadialGeometry.LIFT_DP }
        assertTrue(ys.max() + 16f <= -30f)
        assertTrue(RadialGeometry.minChordDp(5, RadialGeometry.RADIUS_DP) >= 64f)
    }

    @Test fun cardsDeclareProvenanceAndNeverInventContent() {
        val c = CardFactory.fromExchange("1", "Quelle est la capitale de la France ?", "La capitale de la France est Paris.", 15_400, false)
        assertEquals(CardFactory.MODEL_PROVENANCE, c.provenance); assertTrue(c.summary.contains("Paris"))
        assertFalse(c.actions.first { it.id == "open" }.enabled)
        val known = CardFactory.concept("2", "Paris", listOf("La capitale de la France est Paris. Elle est grande."))
        assertEquals("La capitale de la France est Paris.", known.summary)
        val unknown = CardFactory.concept("3", "Napoléon", listOf("La capitale de la France est Paris."))
        assertTrue(unknown.summary.startsWith("Source indisponible hors ligne")); assertTrue(unknown.details.isEmpty())
        val u = CardFactory.unavailable("4", "NEXUS", "La consultation de documents")
        assertTrue(u.summary.contains("Aucun contenu n'est inventé"))
        val long = CardFactory.compact("mot ".repeat(200)); assertTrue(long.length <= 242 && long.endsWith("…"))
    }

    @Test fun systemCardShowsOnlyMeasuredValues() {
        val m = SystemMeasure(82, true, 31.5f, "normal", 2048, 6144, null, "à l'instant")
        val b = CardFactory.system("5", SystemTopic.BATTERY, m)
        assertEquals("Batterie : 82 % (en charge).", b.summary); assertEquals(CardFactory.ANDROID_PROVENANCE, b.provenance)
        val s = CardFactory.system("6", SystemTopic.STORAGE, m)
        assertEquals("Stockage libre : indisponible.", s.summary) // unknown stays unknown
        val none = CardFactory.system("7", SystemTopic.BATTERY, m.copy(batteryPercent = null))
        assertEquals("Batterie : indisponible.", none.summary)
    }

    @Test fun systemIntentsAreRecognisedWithoutCatchingOrdinaryQuestions() {
        assertEquals(SystemTopic.BATTERY, Intents.systemTopic("Quelle est la batterie ?"))
        assertEquals(SystemTopic.BATTERY, Intents.systemTopic("Il me reste combien d'autonomie ?"))
        assertEquals(SystemTopic.STORAGE, Intents.systemTopic("Combien d'espace libre sur le stockage ?"))
        assertEquals(SystemTopic.MEMORY, Intents.systemTopic("Quelle mémoire disponible ?"))
        assertEquals(SystemTopic.TEMPERATURE, Intents.systemTopic("Le téléphone est-il chaud ?"))
        assertNull(Intents.systemTopic("Parle-moi de Napoléon"))
        assertNull(Intents.systemTopic("Quelle est la capitale de la France ?"))
    }

    @Test fun keywordsComeFromTheExchange() {
        val k = Keywords.extract("Parle-moi de Napoléon", "Napoléon Bonaparte fut empereur des Français ; la Révolution précéda l'Empire.")
        assertTrue(k.toString(), k.containsAll(listOf("Napoléon", "Bonaparte", "Français", "Révolution", "Empire")))
        assertTrue(Keywords.extract("Bonjour", "Bonjour !").isEmpty())
        assertTrue(Keywords.extract("a", "x ".repeat(50) + "Alpha Bravo Charlie Delta Echo Foxtrot Golf").size <= 6)
    }

    @Test fun radialMenuHasFiveDestinationsAndRoomFor48dpTargets() {
        assertEquals(5, Destination.entries.size)
        assertEquals(listOf(Destination.DOCUMENTS, Destination.SERVICES), Destination.entries.filterNot { it.available })
        val p = RadialGeometry.positions(5, 172f)
        assertEquals(-172f, p.first().first, 0.01f); assertEquals(-172f, p.last().second, 0.01f)
        assertTrue(RadialGeometry.minChordDp(5, 172f) >= 64f) // 48 dp circle + label line, no overlap
    }
}
