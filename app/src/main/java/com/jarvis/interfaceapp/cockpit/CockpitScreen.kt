package com.jarvis.interfaceapp.cockpit

import android.content.Context
import android.os.PowerManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val Cyan = Color(0xFF68E3EF)
private val Ink = Color(0xFF060B17)
private val modules = listOf("Système", "Administratif", "Finance", "Projets", "Écriture", "Création", "Technique", "Sécurité")
private val accents = listOf(Cyan, Color(0xFFB69BFF), Color(0xFF7BDDA5), Color(0xFFFFBD78), Cyan, Color(0xFFD6A3FF), Color(0xFF85BEFF), Color(0xFFFF8798))

@Composable
fun CockpitScreen(avatar: @Composable () -> Unit = { Text("Avatar · emplacement réservé", color = Cyan) }) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ui-01", Context.MODE_PRIVATE) }
    var screen by rememberSaveable { mutableStateOf("Accueil") }
    var intensity by remember { mutableFloatStateOf(prefs.getFloat("intensity", .35f).coerceIn(0f, 1f)) }
    var economy by remember { mutableStateOf(prefs.getBoolean("economy", false)) }
    var glow by remember { mutableStateOf(prefs.getBoolean("glow", true)) }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var active by remember { mutableStateOf(false) }
    var systemEconomy by remember { mutableStateOf(false) }
    val observation = remember(now) { LocalWeatherStore(context).lastKnown() }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            active = true
            try {
                while (true) {
                    now = LocalDateTime.now()
                    systemEconomy = context.getSystemService(PowerManager::class.java).isPowerSaveMode
                    delay(30_000)
                }
            } finally { active = false }
        }
    }
    BackHandler(screen != "Accueil") { screen = "Accueil" }
    val animate = active && !economy && !systemEconomy && intensity > 0f
    Scaffold(containerColor = Ink) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("J A R V I S", color = Cyan, style = MaterialTheme.typography.titleLarge)
                    Text("INTERFACE LOCALE", fontSize = 10.sp, color = Color.LightGray)
                }
                Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), color = Cyan)
                TextButton(onClick = { screen = "Paramètres" }, modifier = Modifier.testTag("open-settings")) { Text("Paramètres") }
            }
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.width(56.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                    listOf("Accueil" to "⌂", "Supervision" to "◎", "Avatar" to "◇").forEach { (route, symbol) ->
                        TextButton(onClick = { screen = route }, contentPadding = PaddingValues(0.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(symbol, fontSize = 24.sp)
                                Text(route, fontSize = 8.sp)
                            }
                        }
                    }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (screen) {
                        "Accueil" -> {
                            BoxWithConstraints(Modifier.fillMaxWidth()) {
                                val wide = maxWidth >= 800.dp
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    if (wide) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        OfficeScene(now, animate, intensity, glow, Modifier.weight(2f), observation)
                                        Supervision(now, Modifier.weight(1f), observation)
                                    } else {
                                        OfficeScene(now, animate, intensity, glow, Modifier.fillMaxWidth(), observation)
                                        Supervision(now, Modifier.fillMaxWidth(), observation)
                                    }
                                    ModuleGrid(if (wide) 8 else 4) { screen = it }
                                }
                            }
                        }
                        "Paramètres" -> {
                            Pane("Paramètres graphiques") {
                                Text("Intensité des animations : ${(intensity * 100).toInt()} %")
                                Slider(value = intensity, onValueChange = {
                                    intensity = it
                                }, onValueChangeFinished = { prefs.edit().putFloat("intensity", intensity).apply() })
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(economy, { economy = it; prefs.edit().putBoolean("economy", it).apply() }, modifier = Modifier.testTag("economy-switch"))
                                    Text("Économie d’énergie", Modifier.padding(start = 8.dp))
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(glow, { glow = it; prefs.edit().putBoolean("glow", it).apply() })
                                    Text("Éclairage décoratif", Modifier.padding(start = 8.dp))
                                }
                                Text("Animations suspendues en arrière-plan et en économie d’énergie Android.")
                            }
                        }
                        "Supervision" -> Supervision(now, observation = observation)
                        "Avatar" -> Pane("Avatar JARVIS", Modifier.testTag("avatar-placeholder")) { avatar() }
                        else -> Pane(screen, Modifier.testTag("module-screen")) {
                            Text("Non connecté", color = Cyan)
                            Text("Écran $screen disponible. Fonctions métier indisponibles ; aucun module externe raccordé.")
                            if (screen == "Système") Text("Android ${android.os.Build.VERSION.RELEASE} · API ${android.os.Build.VERSION.SDK_INT}\nSource : android.os.Build")
                        }
                    }
                    if (screen != "Accueil") Button(onClick = { screen = "Accueil" }, modifier = Modifier.testTag("close-settings")) { Text("Retour au tableau de bord") }
                    Text("Hors ligne · aucune connexion aux services JARVIS", color = Color.LightGray, fontSize = 11.sp, modifier = Modifier.testTag("local-status"))
                }
            }
        }
    }
}

@Composable
private fun Pane(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), color = Color(0xE6121C2C), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Cyan.copy(alpha = .3f))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = Cyan, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun Supervision(now: LocalDateTime, modifier: Modifier = Modifier, observation: WeatherObservation? = null) {
    Pane("SUPERVISION", modifier.testTag("dashboard")) {
        Text("Interface · locale")
        Text("${now.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))}\nSource : horloge de l’appareil", fontSize = 12.sp)
        HorizontalDivider(color = Cyan.copy(alpha = .2f))
        listOf("CORE", "LLM", "VOICE", "Sécurité").forEach {
            Text("$it : Non connecté", fontSize = 12.sp)
        }
        Text(observation?.let { "Météo : ${it.weather.name} · dernière observation\nSource : ${it.source}\n${java.time.Instant.ofEpochMilli(it.observedAtEpochMillis).atZone(java.time.ZoneId.systemDefault())}" } ?: "Météo : Non connecté · aucune observation enregistrée.", fontSize = 11.sp, color = Color.LightGray)
    }
}

@Composable
private fun ModuleGrid(columns: Int, open: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        modules.indices.chunked(columns).forEach { indices ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                indices.forEach { i ->
                    OutlinedButton(onClick = { open(modules[i]) }, modifier = Modifier.weight(1f).heightIn(min = 64.dp).testTag("module-$i"),
                        contentPadding = PaddingValues(2.dp), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, accents[i].copy(alpha = .55f))) {
                        Text(modules[i], fontSize = 10.sp, color = accents[i], softWrap = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun OfficeScene(now: LocalDateTime, animate: Boolean, intensity: Float, glow: Boolean, modifier: Modifier, observation: WeatherObservation? = null) {
    val phase = dayPhase(now.toLocalTime())
    val skyTarget = when (phase) {
        DayPhase.SUNRISE -> Color(0xFFB07775)
        DayPhase.MORNING -> Color(0xFF487589)
        DayPhase.AFTERNOON -> Color(0xFF466A84)
        DayPhase.SUNSET -> Color(0xFF9B645E)
        DayPhase.NIGHT -> Color(0xFF102340)
    }
    val sky by androidx.compose.animation.animateColorAsState(skyTarget, tween(if (animate) 2000 else 0), label = "ambiance")
    val pulse = if (animate) {
        val transition = rememberInfiniteTransition(label = "office")
        val value by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(5000), RepeatMode.Reverse), label = "led")
        value
    } else 0f
    Pane("BUREAU · ${phase.name}", modifier) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1.45f).background(Ink, RoundedCornerShape(10.dp))) {
            val w = size.width; val h = size.height
            fun box(x: Float, y: Float, width: Float, height: Float, color: Color) = drawRect(color, Offset(x*w,y*h), Size(width*w,height*h))
            box(.08f,.06f,.84f,.53f,sky)
            when (observation?.weather) {
                Weather.CLOUDS, Weather.RAIN, Weather.STORM -> {
                    for (i in 0..3) drawOval(Color(0xFF536275).copy(alpha=.55f), Offset((.1f+i*.19f)*w,.12f*h),Size(w*.25f,h*.09f))
                    if (observation.weather != Weather.CLOUDS) for (i in 0..22) {
                        val x = .1f+(i%12)*.068f
                        val y = .25f+(i/12)*.15f
                        drawLine(Cyan.copy(alpha=.3f),Offset(x*w,y*h),Offset((x-.01f)*w,(y+.05f)*h),1.5f)
                    }
                    if (observation.weather == Weather.STORM) drawLine(Color(0xFFFFE8AA),Offset(.76f*w,.2f*h),Offset(.72f*w,.3f*h),2f)
                }
                Weather.FOG -> box(.08f,.2f,.84f,.35f,Color.LightGray.copy(alpha=.22f))
                Weather.SNOW -> for (i in 0..30) drawCircle(Color.White.copy(alpha=.6f),w*.003f,Offset((.1f+(i%11)*.07f)*w,(.16f+(i/11)*.14f)*h))
                Weather.CLEAR -> drawCircle(Color(0xFFF9DAB0).copy(alpha=.65f),w*.035f,Offset(.76f*w,.2f*h))
                null -> Unit
            }
            for (i in 0..5) box(.1f+i*.15f,.08f,.007f,.5f,Color(0xFF1B2C3A))
            box(.08f,.57f,.84f,.025f,Cyan.copy(alpha = if (glow) .35f+pulse*intensity*.15f else .06f))
            box(0f,.65f,1f,.35f,Color(0xFF141B26))
            box(.16f,.62f,.69f,.045f,Color(0xFF876952))
            box(.19f,.66f,.025f,.26f,Color(0xFF343C49)); box(.79f,.66f,.025f,.26f,Color(0xFF343C49))
            for (i in 0..2) {
                val x=.27f+i*.17f
                box(x,.29f,.15f,.23f,Cyan.copy(alpha = .09f))
                drawRect(Cyan.copy(alpha = .45f+pulse*intensity*.1f),Offset(x*w,.29f*h),Size(.15f*w,.23f*h),style=androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
                for (j in 0..4) box(x+.018f,.32f+j*.035f,.1f,.004f,Cyan.copy(alpha=.4f))
                box(x+.07f,.52f,.01f,.1f,Color(0xFF435767))
            }
            drawRoundRect(Color(0xFF283345),Offset(.42f*w,.62f*h),Size(.19f*w,.24f*h),androidx.compose.ui.geometry.CornerRadius(w*.035f))
            box(.5f,.84f,.025f,.1f,Color(0xFF65717D))
            drawLine(Color(0xFF65717D),Offset(.43f*w,.95f*h),Offset(.59f*w,.95f*h),4f)
            box(.85f,.71f,.075f,.14f,Color(0xFF957A68))
            for (i in 0..4) {
                val sway = pulse*intensity*.006f
                drawLine(Color(0xFF4A946E),Offset(.89f*w,.73f*h),Offset((.82f+i*.03f+sway)*w,(.43f+(i%2)*.08f)*h),w*.012f)
                drawOval(Color(0xFF47795D),Offset((.8f+i*.03f+sway)*w,(.4f+(i%2)*.08f)*h),Size(w*.045f,h*.09f))
            }
            box(.12f,.37f,.012f,.26f,Color(0xFFD6AE7B))
            drawOval(Color(0xFFE5BC82),Offset(.07f*w,.34f*h),Size(.12f*w,.045f*h))
            box(.69f,.57f,.045f,.05f,Color(0xFFE1D4C3))
            for (i in 0..2) {
                val rise = (pulse*.07f+i*.025f)
                drawCircle(Color.White.copy(alpha=.13f),w*.009f,Offset((.706f+i*.006f)*w,(.55f-rise)*h))
            }
            if (glow) drawRect(Brush.radialGradient(listOf(Cyan.copy(alpha=.06f),Color.Transparent),center=Offset(w*.5f,h*.4f),radius=w*.5f))
        }
        Text(if (observation == null) "Ambiance horaire locale · météo inconnue" else "Météo connue : ${observation.weather.name} · observation passée", color = Color.LightGray, fontSize = 11.sp)
    }
}
