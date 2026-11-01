package app.nebula.vpn.desktop.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.nebula.vpn.core.model.ThemeMode

val EmeraldPrimary = Color(0xFF00D084)
val EmeraldOnPrimary = Color(0xFF003820)
val PurpleAccent = Color(0xFF8A2BE2)
val ErrorRed = Color(0xFFFF5252)
val WarningYellow = Color(0xFFFFB74D)

val DarkColorScheme = darkColorScheme(
    primary = EmeraldPrimary,
    onPrimary = EmeraldOnPrimary,
    secondary = PurpleAccent,
    background = Color(0xFF0F172A),
    surface = Color(0xFF1E293B),
    surfaceVariant = Color(0xFF334155),
    onBackground = Color(0xFFF8FAFC),
    onSurface = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF94A3B8),
    error = ErrorRed
)

val OledColorScheme = darkColorScheme(
    primary = EmeraldPrimary,
    onPrimary = EmeraldOnPrimary,
    secondary = PurpleAccent,
    background = Color(0xFF000000),
    surface = Color(0xFF121212),
    surfaceVariant = Color(0xFF1E1E1E),
    onBackground = Color(0xFFFFFFFF),
    onSurface = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFFA0AEC0),
    error = ErrorRed
)

val LightColorScheme = lightColorScheme(
    primary = Color(0xFF008955),
    onPrimary = Color(0xFFFFFFFF),
    secondary = PurpleAccent,
    background = Color(0xFFF8FAFC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF1F5F9),
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFF64748B),
    error = ErrorRed
)

@Composable
fun DesktopTheme(
    mode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val colors = when (mode) {
        ThemeMode.DARK -> DarkColorScheme
        ThemeMode.AMOLED -> OledColorScheme
        ThemeMode.LIGHT -> LightColorScheme
        ThemeMode.SYSTEM -> if (isSystemInDarkTheme()) DarkColorScheme else LightColorScheme
    }

    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}
