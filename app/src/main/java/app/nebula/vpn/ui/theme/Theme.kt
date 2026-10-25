package app.nebula.vpn.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.nebula.vpn.core.model.ButtonShape
import app.nebula.vpn.core.model.ThemeConfig
import app.nebula.vpn.core.model.ThemeMode

val LocalThemeConfig = compositionLocalOf { ThemeConfig() }

@Composable
fun NebulaTheme(
    config: ThemeConfig = ThemeConfig(),
    content: @Composable () -> Unit
) {
    val isDark = when (config.mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.AMOLED -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val primary = parseColor(config.primaryColorHex, EmeraldGreen)
    val accent = parseColor(config.accentColorHex, MintTeal)
    val surface = if (config.mode == ThemeMode.AMOLED) Color(0xFF13171C) else parseColor(config.surfaceColorHex, SlateGrey)
    val background = if (config.mode == ThemeMode.AMOLED) Color(0xFF090C0E) else parseColor(config.backgroundColorHex, DeepBlack)

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = primary,
            secondary = accent,
            background = background,
            surface = surface,
            surfaceVariant = SlateGreyLight,
            onPrimary = PureWhite,
            onSecondary = PureWhite,
            onBackground = PureWhite,
            onSurface = PureWhite,
            outline = BorderSubtle
        )
    } else {
        lightColorScheme(
            primary = primary,
            secondary = accent,
            background = PureWhite,
            surface = Color(0xFFF4F4F6),
            onPrimary = PureWhite,
            onSecondary = PureWhite,
            onBackground = Color(0xFF101014),
            onSurface = Color(0xFF101014),
            outline = Color(0xFFDCDCE2)
        )
    }

    val radius = config.cornerRadiusDp.dp
    val shapes = when (config.buttonShape) {
        ButtonShape.ROUNDED -> Shapes(
            small = RoundedCornerShape(radius / 2),
            medium = RoundedCornerShape(radius),
            large = RoundedCornerShape(radius * 1.5f)
        )
        ButtonShape.SQUIRCLE -> Shapes(
            small = RoundedCornerShape(radius / 2),
            medium = RoundedCornerShape(radius),
            large = RoundedCornerShape(radius * 1.5f)
        )
        ButtonShape.SHARP -> Shapes(
            small = RoundedCornerShape(4.dp),
            medium = RoundedCornerShape(6.dp),
            large = RoundedCornerShape(8.dp)
        )
    }

    CompositionLocalProvider(LocalThemeConfig provides config) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = shapes,
            content = content
        )
    }
}

private fun parseColor(hex: String, default: Color): Color {
    return try {
        val clean = hex.removePrefix("#")
        val longVal = clean.toLong(16)
        if (clean.length == 6) {
            Color(0xFF000000 or longVal)
        } else if (clean.length == 8) {
            Color(longVal)
        } else {
            default
        }
    } catch (e: Exception) {
        default
    }
}
