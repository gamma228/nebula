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

    // Build NebulaColors tokens with distinct AMOLED (pitch black #000000) vs Dark (charcoal slate #141920) vs Light
    val nebulaColors = when (config.presetId) {
        "oled_crimson" -> NebulaColors(
            background = when {
                isAmoled -> Color(0xFF000000)
                isDark -> Color(0xFF191216)
                else -> Color(0xFFFFF5F7)
            },
            surface = when {
                isAmoled -> Color(0xFF10070B)
                isDark -> Color(0xFF261B21)
                else -> Color(0xFFFFFFFF)
            },
            surfaceElevated = when {
                isAmoled -> Color(0xFF1A0C13)
                isDark -> Color(0xFF33242D)
                else -> Color(0xFFFFEBF0)
            },
            primary = CrimsonRed,
            onPrimary = PureWhite,
            secondary = ElectricViolet,
            accent = Color(0xFFFF3366),
            textPrimary = if (isDark) PureWhite else Color(0xFF1F0A12),
            textSecondary = if (isDark) Color(0xFF9CA3AF) else Color(0xFF7A4A58),
            textMuted = if (isDark) Color(0xFF6B7280) else Color(0xFFA0838E),
            statusOk = StatusGreen,
            statusWarning = StatusYellow,
            statusError = StatusRed,
            borderSubtle = when {
                config.neonBordersEnabled -> CrimsonRed.copy(alpha = if (isDark) 0.45f else 0.35f)
                isAmoled -> Color(0x2BFFFFFF)
                isDark -> Color(0xFF382732)
                else -> Color(0xFFFFD1DC)
            },
            borderActive = CrimsonRed,
            scrim = Color(0xCC000000),
            orbGlow = CrimsonRed
        )
        "cyber_violet" -> NebulaColors(
            background = when {
                isAmoled -> Color(0xFF000000)
                isDark -> Color(0xFF161426)
                else -> Color(0xFFF7F5FF)
            },
            surface = when {
                isAmoled -> Color(0xFF0C0917)
                isDark -> Color(0xFF221F3A)
                else -> Color(0xFFFFFFFF)
            },
            surfaceElevated = when {
                isAmoled -> Color(0xFF161127)
                isDark -> Color(0xFF2E2A4E)
                else -> Color(0xFFF0ECFF)
            },
            primary = ElectricViolet,
            onPrimary = PureWhite,
            secondary = Color(0xFF00FFFF),
            accent = Color(0xFF00FFFF),
            textPrimary = if (isDark) PureWhite else Color(0xFF170E2E),
            textSecondary = if (isDark) Color(0xFF9CA3AF) else Color(0xFF655685),
            textMuted = if (isDark) Color(0xFF6B7280) else Color(0xFF9284B3),
            statusOk = StatusGreen,
            statusWarning = StatusYellow,
            statusError = StatusRed,
            borderSubtle = when {
                config.neonBordersEnabled -> ElectricViolet.copy(alpha = if (isDark) 0.45f else 0.35f)
                isAmoled -> Color(0x2BFFFFFF)
                isDark -> Color(0xFF373256)
                else -> Color(0xFFDDD5FF)
            },
            borderActive = ElectricViolet,
            scrim = Color(0xCC000000),
            orbGlow = ElectricViolet
        )
        "monochrome" -> NebulaColors(
            background = when {
                isAmoled -> Color(0xFF000000)
                isDark -> Color(0xFF161920)
                else -> Color(0xFFF8FAFC)
            },
            surface = when {
                isAmoled -> Color(0xFF0E1116)
                isDark -> Color(0xFF222731)
                else -> Color(0xFFFFFFFF)
            },
            surfaceElevated = when {
                isAmoled -> Color(0xFF171B22)
                isDark -> Color(0xFF2E3542)
                else -> Color(0xFFF1F5F9)
            },
            primary = if (isDark) PureWhite else Color(0xFF111827),
            onPrimary = if (isDark) DeepBlack else PureWhite,
            secondary = Color(0xFF718096),
            accent = Color(0xFF94A3B8),
            textPrimary = if (isDark) PureWhite else Color(0xFF0F172A),
            textSecondary = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569),
            textMuted = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8),
            statusOk = StatusGreen,
            statusWarning = StatusYellow,
            statusError = StatusRed,
            borderSubtle = when {
                isAmoled -> Color(0x2BFFFFFF)
                isDark -> Color(0xFF363E4E)
                else -> Color(0xFFE2E8F0)
            },
            borderActive = if (isDark) PureWhite else Color(0xFF111827),
            scrim = Color(0xCC000000),
            orbGlow = PureWhite
        )
        else -> { // Default Cyber Emerald / Custom
            val primary = parseColor(config.primaryColorHex, EmeraldGreen)
            val accent = parseColor(config.accentColorHex, MintTeal)
            val background = when {
                isAmoled -> Color(0xFF000000)
                isDark -> Color(0xFF131A1F)
                else -> Color(0xFFF4FAF7)
            }
            val surface = when {
                isAmoled -> Color(0xFF090E14)
                isDark -> Color(0xFF1D2631)
                else -> Color(0xFFFFFFFF)
            }
            val surfaceElevated = when {
                isAmoled -> Color(0xFF101822)
                isDark -> Color(0xFF273342)
                else -> Color(0xFFE8F6F0)
            }

            NebulaColors(
                background = background,
                surface = surface,
                surfaceElevated = surfaceElevated,
                primary = primary,
                onPrimary = PureWhite,
                secondary = accent,
                accent = accent,
                textPrimary = if (isDark) PureWhite else Color(0xFF0A1F16),
                textSecondary = if (isDark) Color(0xFF94A3B8) else Color(0xFF426053),
                textMuted = if (isDark) Color(0xFF64748B) else Color(0xFF759787),
                statusOk = StatusGreen,
                statusWarning = StatusYellow,
                statusError = StatusRed,
                borderSubtle = when {
                    config.neonBordersEnabled -> primary.copy(alpha = if (isDark) 0.45f else 0.35f)
                    isAmoled -> Color(0x2BFFFFFF)
                    isDark -> Color(0xFF2C3A4B)
                    else -> Color(0xFFD3EBE0)
                },
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
            background = nebulaColors.background,
            surface = nebulaColors.surface,
            surfaceVariant = nebulaColors.surfaceElevated,
            onPrimary = nebulaColors.onPrimary,
            onSecondary = nebulaColors.onPrimary,
            onBackground = nebulaColors.textPrimary,
            onSurface = nebulaColors.textPrimary,
            outline = nebulaColors.borderSubtle
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
