package fr.jarvis.v3

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
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
    private fun waitUntil(ms: Long, ok: () -> Boolean) { val t0 = SystemClock.elapsedRealtime(); while (!main(ok) && SystemClock.elapsedRealtime() - t0 < ms) Thread.sleep(100) }
    private fun shot(name: String, what: String) {
        SystemClock.sleep(900)
        inst.uiAutomation.takeScreenshot()?.let { b -> File(dir, "tour-$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        index.put(JSONObject().put("file", "tour-$name").put("screen", what))
    }
    private fun ask(q: String) {
        val s = main { ui.session } ?: return; val n = main { s.exchanges.size }
        main { s.textTurn(q) }; waitUntil(420_000) { s.phase == Phase.READY && s.exchanges.size > n }
    }

    @Test fun w_screenTour() {
        assumeTrue(args.getString("afterImport") != null)
        try {
            if (app.knowledge.open() is LocalKnowledge.State.Ready)
                app.knowledge.import("club-astronomie.txt", "Le club d'astronomie se réunit le jeudi 12 mars à 19 heures, à la médiathèque.".toByteArray())
            ActivityScenario.launch(CockpitActivity::class.java).use {
                Configurator.getInstance().setWaitForIdleTimeout(0)
                dev.wait(Until.findObject(By.res("start")), 30_000)?.click()
                waitUntil(360_000) { ui.session?.phase == Phase.READY || ui.session?.phase == Phase.ERROR }
                main { ui.session?.voiceOutputEnabled = false }
                SystemClock.sleep(2_000); shot("01-ecran-principal-sphere-repos", "Écran principal : sphère au repos, avatar Athéna, dock de conversation")
                ask("Selon mes documents, quand se réunit le club d'astronomie ?")
                shot("02-reponse-documentaire-citee", "Réponse réelle du modèle local avec source NEXUS citée")
                ask("Souviens-toi que je préfère le thé au café.")
                shot("03-proposition-de-souvenir", "MEMORY VAULT : proposition de souvenir soumise au consentement")
                main { ui.session?.decideMemory(true) }; shot("04-souvenir-confirme", "Souvenir confirmé par l'utilisateur")
                dev.findObject(By.res("radial"))?.click(); shot("05-menu-radial", "Menu radial (Cockpit, Bibliothèque, Mémoire, Command Center, Réglages)")
                main { ui.panel = Panel.NONE }
                for (m in CommandModule.values()) {
                    main { ui.openCard = null; ui.commandTab = m; ui.panel = Panel.COMMAND }
                    shot("10-command-center-${m.name.lowercase()}", "Command Center V2, onglet ${m.label} (${m.status.label})")
                }
                main { ui.panel = Panel.KNOWLEDGE }; shot("20-nexus-bibliotheque", "NEXUS : bibliothèque locale (documents importés, recherche)")
                main { ui.panel = Panel.PALETTE }; shot("21-palette-de-commandes", "Palette de commandes")
                main { ui.panel = Panel.SETTINGS }; shot("22-reglages", "Réglages (personnalité, voix, affichage)")
                main { ui.panel = Panel.NONE }
                dev.findObject(By.res("avatar"))?.let { a ->
                    val b = a.visibleBounds; SystemClock.sleep(700)
                    inst.uiAutomation.takeScreenshot()?.let { full ->
                        val pad = b.width() / 2
                        val x = (b.left - pad).coerceAtLeast(0); val y = (b.top - pad).coerceAtLeast(0)
                        val crop = Bitmap.createBitmap(full, x, y, minOf(b.width() + 2 * pad, full.width - x), minOf(b.height() + 2 * pad, full.height - y))
                        File(dir, "tour-30-avatar-athena-gros-plan.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        index.put(JSONObject().put("file", "tour-30-avatar-athena-gros-plan").put("screen", "Avatar Athéna (gros plan, état repos)"))
                    }
                }
                dev.setOrientationLandscape(); shot("40-paysage", "Écran principal en paysage"); dev.setOrientationNatural()
            }
        } finally {
            File(inst.targetContext.filesDir, "audit-tour.json").writeText(JSONObject().put("captures", index).toString(2))
            main { app.closeSession(); ui.session = null }
        }
    }
}
