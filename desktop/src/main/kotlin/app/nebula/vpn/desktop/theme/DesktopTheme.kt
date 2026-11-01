package app.nebula.vpn.desktop.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.nebula.vpn.core.model.ThemeMode

enum class DesktopThemePreset(val id: String, val displayNameKey: String, val primaryHex: String, val accentHex: String) {
    EMERALD_CYBER("emerald_cyber", "preset_emerald", "#00D084", "#06B6D4"),
    OLED_CRIMSON("oled_crimson", "preset_crimson", "#FF3366", "#FF7597"),
    CYBER_VIOLET("cyber_violet", "preset_violet", "#A855F7", "#EC4899"),
    NORDIC_FROST("nordic_frost", "preset_frost", "#00E5FF", "#3B82F6"),
    SOLAR_AMBER("solar_amber", "preset_amber", "#F59E0B", "#EF4444"),
    MONOCHROME("monochrome_titanium", "preset_titanium", "#E2E8F0", "#94A3B8"),
    CUSTOM("custom", "preset_custom", "#00D084", "#8A2BE2");

    companion object {
        fun fromId(id: String): DesktopThemePreset {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: EMERALD_CYBER
        }
    }
}

fun parseHexColor(hex: String, fallback: Color = Color(0xFF00D084)): Color {
    val clean = hex.removePrefix("#").trim()
    return try {
        when (clean.length) {
            6 -> Color(clean.toLong(16) or 0xFF000000L)
            8 -> Color(clean.toLong(16))
            3 -> {
                val r = clean[0].toString().repeat(2)
                val g = clean[1].toString().repeat(2)
                val b = clean[2].toString().repeat(2)
                Color("$r$g$b".toLong(16) or 0xFF000000L)
            }
            else -> fallback
        }
    } catch (_: Exception) {
        fallback
    }
}

fun buildColorScheme(
    mode: ThemeMode,
    preset: DesktopThemePreset,
    customAccentHex: String
): ColorScheme {
    val primary = if (preset == DesktopThemePreset.CUSTOM) {
        parseHexColor(customAccentHex, Color(0xFF00D084))
    } else {
        parseHexColor(preset.primaryHex)
    }

    val secondary = parseHexColor(preset.accentHex)
    val errorColor = Color(0xFFFF5252)

    return when (mode) {
        ThemeMode.LIGHT -> {
            lightColorScheme(
                primary = primary,
                onPrimary = Color.White,
                secondary = secondary,
                background = Color(0xFFF8FAFC),
                surface = Color.White,
                surfaceVariant = Color(0xFFF1F5F9),
                onBackground = Color(0xFF0F172A),
                onSurface = Color(0xFF1E293B),
                onSurfaceVariant = Color(0xFF64748B),
                error = errorColor
            )
        }
        ThemeMode.AMOLED -> {
            darkColorScheme(
                primary = primary,
                onPrimary = Color.Black,
                secondary = secondary,
                background = Color(0xFF000000),
                surface = Color(0xFF101010),
                surfaceVariant = Color(0xFF1C1C1E),
                onBackground = Color(0xFFFFFFFF),
                onSurface = Color(0xFFF1F5F9),
                onSurfaceVariant = Color(0xFFA0AEC0),
                error = errorColor
            )
        }
        ThemeMode.DARK, ThemeMode.SYSTEM -> {
            when (preset) {
                DesktopThemePreset.OLED_CRIMSON -> darkColorScheme(
                    primary = primary,
                    onPrimary = Color.Black,
                    secondary = secondary,
                    background = Color(0xFF000000),
                    surface = Color(0xFF121212),
                    surfaceVariant = Color(0xFF1E1E1E),
                    onBackground = Color.White,
                    onSurface = Color(0xFFF1F5F9),
                    onSurfaceVariant = Color(0xFFA0AEC0),
                    error = errorColor
                )
                DesktopThemePreset.CYBER_VIOLET -> darkColorScheme(
                    primary = primary,
                    onPrimary = Color.White,
                    secondary = secondary,
                    background = Color(0xFF0D0A14),
                    surface = Color(0xFF171226),
                    surfaceVariant = Color(0xFF241C3B),
                    onBackground = Color(0xFFF5F3FF),
                    onSurface = Color(0xFFEDE9FE),
                    onSurfaceVariant = Color(0xFFA78BFA),
                    error = errorColor
                )
                DesktopThemePreset.NORDIC_FROST -> darkColorScheme(
                    primary = primary,
                    onPrimary = Color(0xFF051726),
                    secondary = secondary,
                    background = Color(0xFF070F1E),
                    surface = Color(0xFF0F1D33),
                    surfaceVariant = Color(0xFF192C4C),
                    onBackground = Color(0xFFF0F9FF),
                    onSurface = Color(0xFFE0F2FE),
                    onSurfaceVariant = Color(0xFF7DD3FC),
                    error = errorColor
                )
                DesktopThemePreset.SOLAR_AMBER -> darkColorScheme(
                    primary = primary,
                    onPrimary = Color(0xFF261A05),
                    secondary = secondary,
                    background = Color(0xFF120F09),
                    surface = Color(0xFF1F1A12),
                    surfaceVariant = Color(0xFF332B1E),
                    onBackground = Color(0xFFFFFBEB),
                    onSurface = Color(0xFFFEF3C7),
                    onSurfaceVariant = Color(0xFFFCD34D),
                    error = errorColor
                )
                DesktopThemePreset.MONOCHROME -> darkColorScheme(
                    primary = primary,
                    onPrimary = Color(0xFF121212),
                    secondary = secondary,
                    background = Color(0xFF101114),
                    surface = Color(0xFF1E2024),
                    surfaceVariant = Color(0xFF2D3139),
                    onBackground = Color(0xFFF8FAFC),
                    onSurface = Color(0xFFF1F5F9),
                    onSurfaceVariant = Color(0xFF94A3B8),
                    error = errorColor
                )
                else -> darkColorScheme(
                    primary = primary,
                    onPrimary = Color(0xFF003820),
                    secondary = secondary,
                    background = Color(0xFF0F172A),
                    surface = Color(0xFF1E293B),
                    surfaceVariant = Color(0xFF334155),
                    onBackground = Color(0xFFF8FAFC),
                    onSurface = Color(0xFFF1F5F9),
                    onSurfaceVariant = Color(0xFF94A3B8),
                    error = errorColor
                )
            }
        }
    }
}

@Composable
fun DesktopTheme(
    mode: ThemeMode = ThemeMode.DARK,
    preset: DesktopThemePreset = DesktopThemePreset.EMERALD_CYBER,
    customAccentHex: String = "#00D084",
    content: @Composable () -> Unit
) {
    val effectiveMode = if (mode == ThemeMode.SYSTEM) {
        if (isSystemInDarkTheme()) ThemeMode.DARK else ThemeMode.LIGHT
    } else mode

    val colors = buildColorScheme(effectiveMode, preset, customAccentHex)

    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}
