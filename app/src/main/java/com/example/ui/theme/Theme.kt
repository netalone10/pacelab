package com.example.ui.theme

import android.app.Activity
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
    primary = VoltPrimary,
    onPrimary = VoltOnPrimary,
    primaryContainer = VoltContainer,
    onPrimaryContainer = VoltOnContainer,
    secondary = Zone2Green,
    onSecondary = VoltOnPrimary,
    background = AthleticDarkBg,
    onBackground = TextWhite,
    surface = AthleticDarkSurface,
    onSurface = TextWhite,
    surfaceVariant = AthleticDarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = AthleticBorder
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF4A6500),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD4FF00),
    onPrimaryContainer = Color(0xFF141F00),
    secondary = Color(0xFF166534),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1)
)

@Composable
fun PaceLabTheme(
    darkTheme: Boolean = true, // Default to high-contrast athletic dark for running data readability
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
