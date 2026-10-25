package com.jarvis.interfaceapp

import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jarvis.interfaceapp.ui.HomeViewModel
import com.jarvis.interfaceapp.ui.JarvisApp
import com.jarvis.interfaceapp.ui.JarvisTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle=androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),navigationBarStyle=androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        val controller = (application as JarvisApplication).dashboard
        val workspace=(application as JarvisApplication).workspace
        setContent {
            val preferences by workspace.state.collectAsStateWithLifecycle()
            JarvisTheme(preferences.value.visual) { JarvisApp(viewModel(factory = HomeViewModel.Factory(controller)),workspace=workspace) }
        }
    }
}
