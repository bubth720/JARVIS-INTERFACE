package com.jarvis.interfaceapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jarvis.interfaceapp.ui.JarvisApp
import com.jarvis.interfaceapp.ui.JarvisTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { JarvisTheme { JarvisApp(viewModel()) } }
    }
}
