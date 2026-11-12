package fr.jarvis.m55d

import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay

object Lab {
    @Volatile private var instance: CockpitViewModel? = null
    fun vm(power: PowerManager): CockpitViewModel = instance ?: synchronized(this) {
        instance ?: CockpitViewModel({ power.currentThermalStatus }).also { instance = it }
    }
    fun current(): CockpitViewModel? = instance
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vm = Lab.vm(getSystemService(PowerManager::class.java))
        setContent {
            LaunchedEffect(Unit) { while (true) { vm.tick(); delay(500) } }
            CockpitScreen(vm)
        }
    }
}
