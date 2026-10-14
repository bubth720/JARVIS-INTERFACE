package com.jarvis.interfaceapp.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TacticalColors = darkColorScheme(
    primary = Color(0xFF62E6DF),
    onPrimary = Color(0xFF003734),
    background = Color(0xFF080E18),
    surface = Color(0xFF101C2B),
    onSurface = Color(0xFFE4EEF8),
    outline = Color(0xFF496078),
)

@Composable
fun JarvisTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = TacticalColors, content = content)
}
