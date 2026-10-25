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
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.nebula.vpn.core.model.ButtonShape
import app.nebula.vpn.core.model.ThemeConfig
import app.nebula.vpn.core.model.ThemeMode

val LocalThemeConfig = compositionLocalOf { ThemeConfig() }

object NebulaTheme {
    val colors: NebulaColors
        @Composable
        @ReadOnlyComposable
        get() = LocalNebulaColors.current

    val spacing: NebulaSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalNebulaSpacing.current

    val shapes: NebulaShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalNebulaShapes.current

    val motion: NebulaMotion
        @Composable
        @ReadOnlyComposable
        get() = LocalNebulaMotion.current

    val typography: NebulaTypographyTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalNebulaTypography.current
}

fun ThemeConfig.getCardShape(): CornerBasedShape = when (buttonShape) {
    ButtonShape.ROUNDED -> RoundedCornerShape(24.dp)
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

    // Build NebulaColors tokens according to the 3 mandatory presets + custom hexes
    val nebulaColors = when (config.presetId) {
        "oled_crimson" -> NebulaColors(
            background = if (isAmoled) Color(0xFF000000) else Color(0xFF0A070B),
            surface = Color(0xFF120E15),
            surfaceElevated = Color(0xFF1B1420),
            primary = CrimsonRed,
            onPrimary = PureWhite,
            secondary = ElectricViolet,
            accent = Color(0xFFFF3366),
            textPrimary = PureWhite,
            textSecondary = Color(0xFF9CA3AF),
            textMuted = Color(0xFF6B7280),
            statusOk = StatusGreen,
            statusWarning = StatusYellow,
            statusError = StatusRed,
            borderSubtle = if (config.neonBordersEnabled) CrimsonRed.copy(alpha = 0.35f) else Color(0x1FFFFFFF),
            borderActive = CrimsonRed,
            scrim = Color(0xCC000000),
            orbGlow = CrimsonRed
        )
        "cyber_violet" -> NebulaColors(
            background = if (isAmoled) Color(0xFF000000) else Color(0xFF0D0E14),
            surface = Color(0xFF151221),
            surfaceElevated = Color(0xFF1E1A30),
            primary = ElectricViolet,
            onPrimary = PureWhite,
            secondary = Color(0xFF00FFFF),
            accent = Color(0xFF00FFFF),
            textPrimary = PureWhite,
            textSecondary = Color(0xFF9CA3AF),
            textMuted = Color(0xFF6B7280),
            statusOk = StatusGreen,
            statusWarning = StatusYellow,
            statusError = StatusRed,
            borderSubtle = if (config.neonBordersEnabled) ElectricViolet.copy(alpha = 0.35f) else Color(0x1FFFFFFF),
            borderActive = ElectricViolet,
            scrim = Color(0xCC000000),
            orbGlow = ElectricViolet
        )
        "monochrome" -> NebulaColors(
            background = if (isAmoled) Color(0xFF000000) else Color(0xFF0B0D10),
            surface = Color(0xFF14171C),
            surfaceElevated = Color(0xFF1D222A),
            primary = PureWhite,
            onPrimary = DeepBlack,
            secondary = Color(0xFF718096),
            accent = Color(0xFF94A3B8),
            textPrimary = PureWhite,
            textSecondary = Color(0xFF94A3B8),
            textMuted = Color(0xFF64748B),
            statusOk = StatusGreen,
            statusWarning = StatusYellow,
            statusError = StatusRed,
            borderSubtle = Color(0x1FFFFFFF),
            borderActive = PureWhite,
            scrim = Color(0xCC000000),
            orbGlow = PureWhite
        )
        else -> { // Default Cyber Emerald / Custom
            val primary = parseColor(config.primaryColorHex, EmeraldGreen)
            val accent = parseColor(config.accentColorHex, MintTeal)
            val background = if (isAmoled) Color(0xFF000000) else parseColor(config.backgroundColorHex, DeepBlack)
            val surface = if (isAmoled) Color(0xFF090E14) else parseColor(config.surfaceColorHex, SlateGrey)
            val surfaceElevated = if (isAmoled) Color(0xFF101822) else SlateGreyLight

            NebulaColors(
                background = background,
                surface = surface,
                surfaceElevated = surfaceElevated,
                primary = primary,
                onPrimary = PureWhite,
                secondary = accent,
                accent = accent,
                textPrimary = PureWhite,
                textSecondary = Color(0xFF94A3B8),
                textMuted = Color(0xFF64748B),
                statusOk = StatusGreen,
                statusWarning = StatusYellow,
                statusError = StatusRed,
                borderSubtle = if (config.neonBordersEnabled) primary.copy(alpha = 0.45f) else BorderSubtle,
                borderActive = primary,
                scrim = Color(0xCC000000),
                orbGlow = primary
            )
        }
    }

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = nebulaColors.primary,
            secondary = nebulaColors.secondary,
            background = nebulaColors.background,
            surface = nebulaColors.surface,
            surfaceVariant = nebulaColors.surfaceElevated,
            onPrimary = nebulaColors.onPrimary,
            onSecondary = nebulaColors.onPrimary,
            onBackground = nebulaColors.textPrimary,
            onSurface = nebulaColors.textPrimary,
            outline = nebulaColors.borderSubtle
        )
    } else {
        lightColorScheme(
            primary = nebulaColors.primary,
            secondary = nebulaColors.secondary,
            background = PureWhite,
            surface = Color(0xFFF4F4F6),
            onPrimary = PureWhite,
            onSecondary = PureWhite,
            onBackground = Color(0xFF101014),
            onSurface = Color(0xFF101014),
            outline = Color(0xFFDCDCE2)
        )
    }

    val typographyTokens = NebulaTypographyTokens()
    val shapes = createNebulaShapes(config.buttonShape)

    CompositionLocalProvider(
        LocalThemeConfig provides config,
        LocalNebulaColors provides nebulaColors,
        LocalNebulaSpacing provides NebulaSpacing(),
        LocalNebulaShapes provides shapes,
        LocalNebulaMotion provides NebulaMotion(),
        LocalReduceMotion provides config.reduceMotion,
        LocalNebulaTypography provides typographyTokens
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = Shapes(
                small = shapes.chip,
                medium = shapes.card,
                large = RoundedCornerShape(
                    when (config.buttonShape) {
                        ButtonShape.ROUNDED -> 28.dp
                        ButtonShape.SQUIRCLE -> 18.dp
                        ButtonShape.SHARP -> 4.dp
                    }
                )
            ),
            typography = typographyTokens.toMaterial3(),
            content = content
        )
    }
}

fun parseColor(hex: String, default: Color): Color {
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
