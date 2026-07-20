package com.catshelf.reader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val HaoMaoColors = darkColorScheme(
    primary = Color(0xFFF0A36B),
    onPrimary = Color(0xFF2B160A),
    secondary = Color(0xFFD0B6FF),
    tertiary = Color(0xFF83D8C9),
    background = Color(0xFF111014),
    onBackground = Color(0xFFF4EFF7),
    surface = Color(0xFF19171D),
    onSurface = Color(0xFFF4EFF7),
    surfaceVariant = Color(0xFF25222B),
    onSurfaceVariant = Color(0xFFCFC6D4),
    outline = Color(0xFF746C79),
)

@Composable
fun HaoMaoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HaoMaoColors,
        content = content,
    )
}
