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
fun JarvisTheme(preferences:com.jarvis.interfaceapp.workspace.VisualPreferences=com.jarvis.interfaceapp.workspace.VisualPreferences(),content: @Composable () -> Unit) {
    val density=androidx.compose.ui.platform.LocalDensity.current
    val colors=when(preferences.theme) {
        com.jarvis.interfaceapp.workspace.LocalTheme.NAVY->TacticalColors
        com.jarvis.interfaceapp.workspace.LocalTheme.GRAPHITE->TacticalColors.copy(background=Color(0xFF111416),surface=Color(0xFF202629),primary=Color(0xFF83E9E1))
        com.jarvis.interfaceapp.workspace.LocalTheme.DEEP_BLUE->TacticalColors.copy(background=Color(0xFF050B21),surface=Color(0xFF101F40),primary=Color(0xFF80D5FF))
    }
    androidx.compose.runtime.CompositionLocalProvider(com.jarvis.interfaceapp.avatar.LocalAvatarMotionEnabled provides !preferences.reduceMotion,androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(
        density.density*preferences.elementPercent/100f,density.fontScale*preferences.textPercent/100f)) {
        MaterialTheme(colorScheme=colors,content=content)
    }
}
