package com.turntable.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF818CF8),
    secondary = Color(0xFFA78BFA),
    tertiary = Color(0xFFF59E0B),
    background = Color(0xFF0B0B1A),
    surface = Color(0xFF16162E),
    surfaceVariant = Color(0xFF222244),
    onPrimary = Color(0xFF0B0B1A),
    onSecondary = Color(0xFF0B0B1A),
    onTertiary = Color(0xFF0B0B1A),
    onBackground = Color(0xFFE2E2F0),
    onSurface = Color(0xFFE2E2F0),
    onSurfaceVariant = Color(0xFF9999BB),
    outline = Color(0xFF33335A),
    error = Color(0xFFF87171),
    errorContainer = Color(0xFF3B1717),
    onError = Color.White,
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF4F46E5),
    secondary = Color(0xFF6D28D9),
    tertiary = Color(0xFFB45309),
    background = Color(0xFFF5F5F5),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE8E8ED),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1A1A1A),
    onSurface = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFF555555),
    outline = Color(0xFFC0C0C8),
    error = Color(0xFFDC2626),
    errorContainer = Color(0xFFFEE2E2),
    onError = Color.White,
)

@Composable
fun TurntableTheme(
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit
) {
    val useDarkTheme = darkTheme ?: isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkColorScheme else LightColorScheme,
        content = content
    )
}
