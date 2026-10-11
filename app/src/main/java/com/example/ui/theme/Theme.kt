package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF00363A),
    onPrimaryContainer = NeonCyan,
    secondary = EmeraldSafe,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF00381C),
    onSecondaryContainer = EmeraldSafe,
    tertiary = AmberWarning,
    onTertiary = Color.Black,
    error = CrimsonAlert,
    onError = Color.White,
    background = CyberNavy,
    onBackground = TextPrimaryDark,
    surface = CyberSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = CyberCard,
    onSurfaceVariant = TextSecondaryDark,
    outline = CyberBorder
)

private val NewDarkColorScheme = darkColorScheme(
    primary = Color(0xFFB39DDB), onPrimary = Color(0xFF1A0F33),
    secondary = Color(0xFF80CBC4), onSecondary = Color.Black,
    tertiary = AmberWarning, onTertiary = Color.Black,
    error = CrimsonAlert, onError = Color.White,
    background = Color(0xFF120E1F), onBackground = Color(0xFFEDE7F6),
    surface = Color(0xFF1B1530), onSurface = Color(0xFFEDE7F6),
    surfaceVariant = Color(0xFF261E42), onSurfaceVariant = Color(0xFFCFC5E8)
)

private val NewLightColorScheme = lightColorScheme(
    primary = Color(0xFF4527A0), onPrimary = Color.White,
    secondary = Color(0xFF00695C), onSecondary = Color.White,
    tertiary = AmberWarning, onTertiary = Color.Black,
    error = CrimsonAlert, onError = Color.White,
    background = Color(0xFFF6F3FB), onBackground = Color(0xFF1C1530),
    surface = Color.White, onSurface = Color(0xFF1C1530),
    surfaceVariant = Color(0xFFE9E2F6), onSurfaceVariant = Color(0xFF4A4060)
)

private val LightColorScheme = lightColorScheme(
    primary = NeonCyanDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F7FA),
    onPrimaryContainer = Color(0xFF006064),
    secondary = EmeraldSafeDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8F5E9),
    onSecondaryContainer = Color(0xFF1B5E20),
    tertiary = AmberWarning,
    onTertiary = Color.Black,
    error = CrimsonAlert,
    onError = Color.White,
    background = Color(0xFFF8FAFC),
    onBackground = TextPrimaryLight,
    surface = Color(0xFFFFFFFF),
    onSurface = TextPrimaryLight,
    surfaceVariant = CyberCardLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = CyberBorderLight
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep consistent cyber privacy aesthetic
    content: @Composable () -> Unit
) {
    val colorScheme = com.example.festival.festiveScheme(if (RedesignGate.isOn()) {
        if (darkTheme) NewDarkColorScheme else NewLightColorScheme
    } else if (darkTheme) DarkColorScheme else LightColorScheme)
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
