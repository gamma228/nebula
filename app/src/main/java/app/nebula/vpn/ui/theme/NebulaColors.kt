package app.nebula.vpn.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class NebulaColors(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val accent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val statusOk: Color,
    val statusWarning: Color,
    val statusError: Color,
    val borderSubtle: Color,
    val borderActive: Color,
    val scrim: Color,
    val orbGlow: Color
)

val LocalNebulaColors = staticCompositionLocalOf {
    NebulaColors(
        background = Color(0xFF000000),
        surface = Color(0xFF12161F),
        surfaceElevated = Color(0xFF1A2230),
        primary = Color(0xFF8A2BE2),
        onPrimary = Color(0xFFFFFFFF),
        secondary = Color(0xFFE60049),
        accent = Color(0xFF00FFFF),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFF94A3B8),
        textMuted = Color(0xFF64748B),
        statusOk = Color(0xFF00D084),
        statusWarning = Color(0xFFFFB703),
        statusError = Color(0xFFFF4D4D),
        borderSubtle = Color(0x1FFFFFFF),
        borderActive = Color(0xFF8A2BE2),
        scrim = Color(0xCC000000),
        orbGlow = Color(0xFF8A2BE2)
    )
}
