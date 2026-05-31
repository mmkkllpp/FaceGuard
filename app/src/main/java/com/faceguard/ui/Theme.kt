package com.faceguard.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Material 3 主题 — 深色模式，绿色强调色
 */
private val DarkColors = darkColorScheme(
    primary = Color(0xFF00D4AA),
    onPrimary = Color(0xFF00382B),
    primaryContainer = Color(0xFF005043),
    onPrimaryContainer = Color(0xFF5EF8CB),
    secondary = Color(0xFF4DD0E1),
    onSecondary = Color(0xFF003640),
    tertiary = Color(0xFF7C5800),
    background = Color(0xFF1A1A2E),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF1A1A2E),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF2A2A4E),
    onSurfaceVariant = Color(0xFFCAC4D0),
    error = Color(0xFFFF6B6B),
    errorContainer = Color(0xFF4A1A1A),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF006B5A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF5EF8CB),
    onPrimaryContainer = Color(0xFF002019),
    background = Color(0xFFFFFBFE),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFBFE),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
    error = Color(0xFFBA1A1A),
)

@Composable
fun FaceGuardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
