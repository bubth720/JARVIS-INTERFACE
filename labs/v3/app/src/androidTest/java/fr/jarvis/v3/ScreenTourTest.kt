package fr.jarvis.v3

import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import fr.jarvis.v1.Phase
import fr.jarvis.v3.conv.ConvApp
import fr.jarvis.v3.conv.LocalKnowledge
import fr.jarvis.v3.model.CommandModule
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Owner request 15/11: real emulator screenshots of the current build — main screen with the sphere, Command Center V2
 * and each of its tabs, the Athéna avatar, MEMORY VAULT, NEXUS, palette, settings. Navigation goes through the real UI
 * state (the same fields the buttons set), every capture is a real render of the app. Synthetic content only.
 */
@RunWith(AndroidJUnit4::class)
class ScreenTourTest {
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val args get() = InstrumentationRegistry.getArguments()
    private val app get() = inst.targetContext.applicationContext as ConvApp
    private val ui get() = V3Runtime.ui
    private val dev get() = UiDevice.getInstance(inst)
    private val dir get() = File(inst.targetContext.filesDir, "v3shots").also { it.mkdirs() }
    private val index = JSONArray()
    private fun <T> main(block: () -> T): T = runBlocking(Dispatchers.Main) { block() }
    private fun waitUntil(ms: Long, ok: () -> Boolean) {
        val t0 = SystemClock.elapsedRealtime()
        while (!main(ok)) {
            check(SystemClock.elapsedRealtime() - t0 < ms) { "Screen tour timeout: ${main { ui.session?.status }}" }
            Thread.sleep(100)
        }
    }
    private fun shot(name: String, what: String) {
        SystemClock.sleep(900)
        val b = checkNotNull(inst.uiAutomation.takeScreenshot()) { "Screenshot unavailable: $name" }
        File(dir, "tour-$name.png").outputStream().use { check(b.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        index.put(JSONObject().put("file", "tour-$name").put("screen", what).put("status", "captured"))
    }
    private fun ask(q: String) {
        val s = checkNotNull(main { ui.session }); val n = main { s.exchanges.size }
        main { s.textTurn(q) }; waitUntil(420_000) { s.phase == Phase.READY && s.exchanges.size > n }
    }
    private fun visible(resource: String): UiObject2 {
        dev.wait(Until.findObject(By.res(resource)), 2_000)?.let { return it }
        // Consent cards are extra LazyColumn items, outside the exchange counter's automatic scroll.
        repeat(6) {
            dev.findObject(By.scrollable(true))?.scroll(Direction.DOWN, 0.8f)
            dev.wait(Until.findObject(By.res(resource)), 1_000)?.let { return it }
        }
        error("Screen tour element not visible: $resource")
    }
    private fun command(module: CommandModule) {
        // CommandCenter remembers its selected tab locally. Close it completely before opening the next
        // route, then assert the rendered label: changing ui.commandTab alone could mislabel ten identical images.
        main { ui.menuOpen = false; ui.openCard = null; ui.panel = Panel.NONE }
        assertTrue("Previous Command Center did not close", dev.wait(Until.gone(By.res("panel-command")), 10_000))
        main { ui.commandTab = module; ui.panel = Panel.COMMAND }
        checkNotNull(dev.wait(Until.findObject(By.res("cc-module-status").textStartsWith("${module.label} · ")), 20_000)) {
            "Command Center did not render ${module.label}"
        }
    }

    @Test fun w_screenTour() {
        assumeTrue(args.getString("afterImport") != null)
        try {
            dir.listFiles()?.filter { it.name.startsWith("tour-") }?.forEach { check(it.delete()) }
            check(app.knowledge.open() is LocalKnowledge.State.Ready) { "NEXUS is not ready for the documentary tour" }
            app.knowledge.import("club-astronomie.txt", "Le club d'astronomie se réunit le jeudi 12 mars à 19 heures, à la médiathèque.".toByteArray())
            ActivityScenario.launch(CockpitActivity::class.java).use {
                Configurator.getInstance().setWaitForIdleTimeout(0)
                dev.wait(Until.findObject(By.res("start")), 30_000)?.click()
                waitUntil(360_000) { ui.session?.phase == Phase.READY || ui.session?.phase == Phase.ERROR }
                check(main { ui.session?.phase == Phase.READY }) { "Local session failed to start" }
                main { ui.session?.voiceOutputEnabled = false }
                SystemClock.sleep(2_000); shot("01-ecran-principal-sphere-repos", "Écran principal : sphère au repos, avatar Athéna, dock de conversation")
                ask("Selon mes documents, quand se réunit le club d'astronomie ?")
                visible("answer")
                shot("02-reponse-documentaire-citee", "Réponse documentaire de l'application locale avec source NEXUS")
                // A proposal is not a completed conversation exchange. Wait for its actual consent UI,
                // rather than the exchange counter used for a model/documentary answer.
                main { checkNotNull(ui.session).textTurn("Souviens-toi que je préfère le thé au café.") }
                waitUntil(30_000) { ui.session?.memoryProposal != null && ui.session?.phase == Phase.READY }
                visible("memory-proposal")
                shot("03-proposition-de-souvenir", "MEMORY VAULT : proposition de souvenir soumise au consentement")
                main { ui.session?.decideMemory(true) }
                visible("memory-outcome")
                shot("04-souvenir-confirme", "Souvenir synthétique confirmé par le test de consentement")
                checkNotNull(dev.findObject(By.res("radial"))).click()
                shot("05-menu-radial", "Menu radial (Cockpit, Bibliothèque, Mémoire, Command Center, Réglages)")
                main { ui.panel = Panel.NONE }
                for (m in CommandModule.values()) {
                    command(m)
                    shot("10-command-center-${m.name.lowercase()}", "Command Center V2, onglet ${m.label} (${m.status.label})")
                }
                command(CommandModule.NEXUS)
                shot("20-nexus-bibliotheque", "NEXUS : bibliothèque locale (documents importés, recherche)")
                main { ui.panel = Panel.PALETTE }; shot("21-palette-de-commandes", "Palette de commandes")
                main { ui.panel = Panel.SETTINGS }; shot("22-reglages", "Réglages (personnalité, voix, affichage)")
                main { ui.panel = Panel.NONE }
                assertTrue(dev.wait(Until.gone(By.res("panel-settings")), 10_000))
                val avatar = dev.findObject(By.res("avatar"))
                if (avatar != null) {
                    val a = avatar
                    val b = a.visibleBounds; SystemClock.sleep(700)
                    checkNotNull(inst.uiAutomation.takeScreenshot()).let { full ->
                        val pad = b.width() / 2
                        val x = (b.left - pad).coerceAtLeast(0); val y = (b.top - pad).coerceAtLeast(0)
                        val crop = Bitmap.createBitmap(full, x, y, minOf(b.width() + 2 * pad, full.width - x), minOf(b.height() + 2 * pad, full.height - y))
                        File(dir, "tour-30-avatar-athena-gros-plan.png").outputStream().use { check(crop.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                        index.put(JSONObject().put("file", "tour-30-avatar-athena-gros-plan").put("screen", "Avatar Athéna (rendu procédural 2D, gros plan au repos)").put("status", "captured"))
                    }
                } else {
                    index.put(JSONObject().put("file", "tour-30-avatar-athena-gros-plan").put("screen", "Avatar Athéna")
                        .put("status", "unavailable").put("reason", "Avatar non affiché par cette configuration de l'application"))
                }
                dev.setOrientationLandscape(); shot("40-paysage", "Écran principal en paysage"); dev.setOrientationNatural()
            }
        } finally {
            File(inst.targetContext.filesDir, "audit-tour.json").writeText(JSONObject().put("schema", "jarvis.capture-tour/1")
                .put("api", Build.VERSION.SDK_INT).put("package", inst.targetContext.packageName).put("syntheticFixtures", true).put("captures", index).toString(2))
            main { app.closeSession(); ui.session = null }
        }
    }
}
