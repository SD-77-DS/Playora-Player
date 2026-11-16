package com.playora.app.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.playora.app.data.Settings

val Emerald = Color(0xFF02601F)

/** Named accent options shown in Theme settings. */
val AccentOptions = listOf(
    "Emerald" to Color(0xFF02601F), "Blue" to Color(0xFF2563EB), "Purple" to Color(0xFF9333EA),
    "Orange" to Color(0xFFF97316), "Red" to Color(0xFFDC2626), "Pink" to Color(0xFFEC4899),
    "Teal" to Color(0xFF0D9488), "Amber" to Color(0xFFF59E0B),
)

/** Pick black/white content colour that stays readable on [bg]. */
fun contentOn(bg: Color) = if (bg.luminance() > 0.45f) Color(0xFF111827) else Color.White

@Composable
fun isDarkTheme(): Boolean = when (Settings.themeMode) {
    1 -> false
    2 -> true
    else -> isSystemInDarkTheme()
}

@Composable
fun PlayoraTheme(content: @Composable () -> Unit) {
    val dark = isDarkTheme()
    val accent = Color(Settings.accent)
    // Lighter accent variant for dark surfaces keeps contrast; darker for light surfaces.
    val primary = accent
    val scheme = if (dark) darkColorScheme(
        primary = primary, onPrimary = contentOn(primary),
        primaryContainer = primary.copy(alpha = 0.25f), onPrimaryContainer = Color(0xFFF9FAFB),
        secondary = primary, onSecondary = contentOn(primary),
        background = Color(0xFF101510), onBackground = Color(0xFFF9FAFB),
        surface = Color(0xFF1C241F), onSurface = Color(0xFFF9FAFB),
        surfaceVariant = Color(0xFF263029), onSurfaceVariant = Color(0xFFB6C2BA),
        surfaceContainer = Color(0xFF1C241F), surfaceContainerHigh = Color(0xFF232C26),
        outline = Color(0xFF4B5A50),
    ) else lightColorScheme(
        primary = primary, onPrimary = contentOn(primary),
        primaryContainer = primary.copy(alpha = 0.18f), onPrimaryContainer = Color(0xFF111827),
        secondary = primary, onSecondary = contentOn(primary),
        background = Color(0xFFF8FAFC), onBackground = Color(0xFF111827),
        surface = Color(0xFFFFFFFF), onSurface = Color(0xFF111827),
        surfaceVariant = Color(0xFFEAF0EC), onSurfaceVariant = Color(0xFF4B5563),
        surfaceContainer = Color(0xFFFFFFFF), surfaceContainerHigh = Color(0xFFF1F5F2),
        outline = Color(0xFFC3CCC6),
    )
    MaterialTheme(colorScheme = scheme, content = content)
}
