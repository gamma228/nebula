package app.nebula.vpn.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CornerBasedShape
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

fun ThemeConfig.getCardShape(): CornerBasedShape = when (buttonShape) {
    ButtonShape.ROUNDED -> RoundedCornerShape(26.dp)
    ButtonShape.SQUIRCLE -> RoundedCornerShape(16.dp)
    ButtonShape.SHARP -> RoundedCornerShape(4.dp)
}

fun ThemeConfig.getChipShape(): CornerBasedShape = when (buttonShape) {
    ButtonShape.ROUNDED -> RoundedCornerShape(50)
    ButtonShape.SQUIRCLE -> RoundedCornerShape(10.dp)
    ButtonShape.SHARP -> RoundedCornerShape(2.dp)
}

fun ThemeConfig.getButtonShape(): CornerBasedShape = when (buttonShape) {
    ButtonShape.ROUNDED -> RoundedCornerShape(50)
    ButtonShape.SQUIRCLE -> RoundedCornerShape(14.dp)
    ButtonShape.SHARP -> RoundedCornerShape(4.dp)
}

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

    val isAmoled = config.mode == ThemeMode.AMOLED

    val primary = parseColor(config.primaryColorHex, EmeraldGreen)
    val accent = parseColor(config.accentColorHex, MintTeal)

    // TRUE PITCH BLACK FOR AMOLED (#000000)
    val background = if (isAmoled) {
        Color(0xFF000000)
    } else if (isDark) {
        parseColor(config.backgroundColorHex, DeepBlack)
    } else {
        PureWhite
    }

    val surface = if (isAmoled) {
        Color(0xFF040608)
    } else if (isDark) {
        parseColor(config.surfaceColorHex, SlateGrey)
    } else {
        Color(0xFFF4F4F6)
    }

    val surfaceVariant = if (isAmoled) {
        Color(0xFF0A0E14)
    } else if (isDark) {
        SlateGreyLight
    } else {
        Color(0xFFE5E7EB)
    }

    val outline = if (isAmoled) {
        if (config.neonBordersEnabled) primary.copy(alpha = 0.55f) else Color(0xFF161F2C)
    } else if (isDark) {
        if (config.neonBordersEnabled) primary.copy(alpha = 0.45f) else BorderSubtle
    } else {
        Color(0xFFDCDCE2)
    }

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = primary,
            secondary = accent,
            background = background,
            surface = surface,
            surfaceVariant = surfaceVariant,
            onPrimary = PureWhite,
            onSecondary = PureWhite,
            onBackground = PureWhite,
            onSurface = PureWhite,
            outline = outline
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

    val shapes = Shapes(
        small = config.getChipShape(),
        medium = config.getCardShape(),
        large = RoundedCornerShape(
            when (config.buttonShape) {
                ButtonShape.ROUNDED -> 32.dp
                ButtonShape.SQUIRCLE -> 22.dp
                ButtonShape.SHARP -> 6.dp
            }
        )
    )

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
