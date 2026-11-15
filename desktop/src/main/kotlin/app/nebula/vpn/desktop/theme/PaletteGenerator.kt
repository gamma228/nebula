package app.nebula.vpn.desktop.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.nebula.vpn.core.model.SurfaceStyle
import app.nebula.vpn.core.model.ThemeMode
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Curated seed colors with semantic names for Nebula Desktop.
 */
data class CuratedSeed(
    val id: String,
    val nameKey: String,
    val nameEn: String,
    val nameRu: String,
    val hex: String,
    val secondaryHex: String
)

object CuratedPalettes {
    val SEEDS = listOf(
        CuratedSeed("cyber_emerald", "seed_cyber_emerald", "Emerald", "Изумруд", "#00D084", "#06B6D4"),
        CuratedSeed("violet_neon", "seed_violet_neon", "Neon", "Неон", "#A855F7", "#EC4899"),
        CuratedSeed("oled_crimson", "seed_oled_crimson", "Crimson", "Багровый", "#FF3366", "#FF7597"),
        CuratedSeed("nordic_frost", "seed_nordic_frost", "Frost", "Иней", "#00E5FF", "#3B82F6"),
        CuratedSeed("solar_amber", "seed_solar_amber", "Amber", "Янтарь", "#F59E0B", "#EF4444"),
        CuratedSeed("monochrome_titanium", "seed_monochrome_titanium", "Titanium", "Титан", "#94A3B8", "#64748B"),
        CuratedSeed("midnight_sapphire", "seed_midnight_sapphire", "Sapphire", "Сапфир", "#3B82F6", "#6366F1"),
        CuratedSeed("sakura_blossom", "seed_sakura_blossom", "Sakura", "Сакура", "#F43F5E", "#FB7185"),
        CuratedSeed("mint_glade", "seed_mint_glade", "Mint", "Мята", "#10B981", "#34D399"),
        CuratedSeed("electric_indigo", "seed_electric_indigo", "Indigo", "Индиго", "#6366F1", "#8B5CF6"),
        CuratedSeed("sunset_coral", "seed_sunset_coral", "Coral", "Коралл", "#FB923C", "#F43F5E"),
        CuratedSeed("arctic_ice", "seed_arctic_ice", "Arctic", "Арктика", "#38BDF8", "#0284C7"),
        CuratedSeed("golden_honey", "seed_golden_honey", "Gold", "Золото", "#EAB308", "#F97316"),
        CuratedSeed("forest_moss", "seed_forest_moss", "Forest", "Лес", "#22C55E", "#15803D"),
        CuratedSeed("rose_gold", "seed_rose_gold", "Rose", "Роза", "#FB7185", "#E11D48"),
        CuratedSeed("pitch_minimal", "seed_pitch_minimal", "Noir", "Нуар", "#E2E8F0", "#475569")
    )

    fun findById(id: String): CuratedSeed {
        return SEEDS.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: SEEDS[0]
    }
}

/**
 * Pure Kotlin Tonal Palette & Material 3 ColorScheme Generator with WCAG AA Contrast Guard.
 */
object PaletteGenerator {

    /**
     * Calculates W3C Relative Luminance for an sRGB Color.
     * https://www.w3.org/TR/WCAG20/#relativeluminancedef
     */
    fun calculateLuminance(color: Color): Float {
        fun gamma(c: Float): Float {
            return if (c <= 0.03928f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
        }
        val r = gamma(color.red)
        val g = gamma(color.green)
        val b = gamma(color.blue)
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    /**
     * Calculates WCAG Contrast Ratio between two colors: (L1 + 0.05) / (L2 + 0.05).
     * Range is 1.0 to 21.0.
     */
    fun calculateContrast(foreground: Color, background: Color): Float {
        val lum1 = calculateLuminance(foreground)
        val lum2 = calculateLuminance(background)
        val lighter = max(lum1, lum2)
        val darker = min(lum1, lum2)
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    /**
     * Ensures contrast meets target ratio (default 4.5:1 for WCAG AA normal text).
     * If insufficient, lightens or darkens the foreground color until compliant.
     */
    fun ensureContrast(
        foreground: Color,
        background: Color,
        minRatio: Float = 4.5f
    ): Color {
        val currentRatio = calculateContrast(foreground, background)
        if (currentRatio >= minRatio) return foreground

        val bgLum = calculateLuminance(background)
        val wantLighter = bgLum < 0.5f

        var bestColor = foreground
        var bestRatio = currentRatio

        // Step brightness towards extreme until ratio satisfies
        for (i in 1..20) {
            val factor = i / 20f
            val adjusted = if (wantLighter) {
                Color(
                    red = (foreground.red + (1f - foreground.red) * factor).coerceIn(0f, 1f),
                    green = (foreground.green + (1f - foreground.green) * factor).coerceIn(0f, 1f),
                    blue = (foreground.blue + (1f - foreground.blue) * factor).coerceIn(0f, 1f),
                    alpha = 1f
                )
            } else {
                Color(
                    red = (foreground.red * (1f - factor)).coerceIn(0f, 1f),
                    green = (foreground.green * (1f - factor)).coerceIn(0f, 1f),
                    blue = (foreground.blue * (1f - factor)).coerceIn(0f, 1f),
                    alpha = 1f
                )
            }
            val ratio = calculateContrast(adjusted, background)
            if (ratio >= minRatio) {
                return adjusted
            }
            if (ratio > bestRatio) {
                bestRatio = ratio
                bestColor = adjusted
            }
        }
        return if (wantLighter) Color.White else Color.Black
    }

    /**
     * Converts RGB to HSV components: [0]=Hue (0..360), [1]=Saturation (0..1), [2]=Value (0..1).
     */
    fun rgbToHsv(color: Color): FloatArray {
        val r = color.red
        val g = color.green
        val b = color.blue

        val max = max(r, max(g, b))
        val min = min(r, min(g, b))
        val delta = max - min

        var h = 0f
        if (delta > 0.00001f) {
            h = when (max) {
                r -> ((g - b) / delta) % 6f
                g -> ((b - r) / delta) + 2f
                else -> ((r - g) / delta) + 4f
            } * 60f
            if (h < 0f) h += 360f
        }

        val s = if (max <= 0.00001f) 0f else delta / max
        val v = max
        return floatArrayOf(h, s, v)
    }

    /**
     * Converts HSV to Color.
     */
    fun hsvToColor(hue: Float, saturation: Float, value: Float, alpha: Float = 1f): Color {
        val h = (hue % 360f + 360f) % 360f
        val s = saturation.coerceIn(0f, 1f)
        val v = value.coerceIn(0f, 1f)

        val c = v * s
        val x = c * (1f - abs(((h / 60f) % 2f) - 1f))
        val m = v - c

        val (r1, g1, b1) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }

        return Color(
            red = (r1 + m).coerceIn(0f, 1f),
            green = (g1 + m).coerceIn(0f, 1f),
            blue = (b1 + m).coerceIn(0f, 1f),
            alpha = alpha
        )
    }

    /**
     * Generates a complete Material 3 ColorScheme based on a seed color and desired theme mode.
     */
    fun generateScheme(
        seedColor: Color,
        mode: ThemeMode,
        surfaceStyle: SurfaceStyle = SurfaceStyle.SOLID
    ): ColorScheme {
        val hsv = rgbToHsv(seedColor)
        val hue = hsv[0]
        val sat = hsv[1]

        val isLight = mode == ThemeMode.LIGHT
        val isAmoled = mode == ThemeMode.AMOLED

        // Ensure primary has adequate contrast with at least one text candidate (Black or White)
        var primary = seedColor
        var whiteContrast = calculateContrast(Color.White, primary)
        var blackContrast = calculateContrast(Color.Black, primary)
        var maxPrimaryContrast = max(whiteContrast, blackContrast)

        if (maxPrimaryContrast < 4.5f) {
            // Adjust luminance slightly in the direction of greatest contrast
            for (step in 1..20) {
                val factor = step / 20f
                primary = if (whiteContrast >= blackContrast) {
                    // Darken
                    Color(seedColor.red * (1f - factor * 0.4f), seedColor.green * (1f - factor * 0.4f), seedColor.blue * (1f - factor * 0.4f))
                } else {
                    // Lighten
                    Color(
                        seedColor.red + (1f - seedColor.red) * factor * 0.4f,
                        seedColor.green + (1f - seedColor.green) * factor * 0.4f,
                        seedColor.blue + (1f - seedColor.blue) * factor * 0.4f
                    )
                }
                whiteContrast = calculateContrast(Color.White, primary)
                blackContrast = calculateContrast(Color.Black, primary)
                maxPrimaryContrast = max(whiteContrast, blackContrast)
                if (maxPrimaryContrast >= 4.5f) break
            }
        }

        val onPrimary = if (blackContrast >= whiteContrast) {
            ensureContrast(Color.Black, primary, 4.5f)
        } else {
            ensureContrast(Color.White, primary, 4.5f)
        }
        val secondary = hsvToColor((hue + 30f) % 360f, (sat * 0.75f).coerceIn(0.2f, 0.9f), if (isLight) 0.70f else 0.85f)
        val tertiary = hsvToColor((hue + 60f) % 360f, (sat * 0.65f).coerceIn(0.2f, 0.85f), if (isLight) 0.65f else 0.80f)

        // Backgrounds & Surfaces
        val background = when {
            isAmoled -> Color(0xFF000000)
            isLight -> Color(0xFFF8FAFC)
            else -> Color(0xFF0F172A)
        }

        val surface = when {
            isAmoled -> if (surfaceStyle == SurfaceStyle.GLASS) Color(0x99111111) else Color(0xFF101010)
            isLight -> if (surfaceStyle == SurfaceStyle.GLASS) Color(0xCCFFFFFF) else Color(0xFFFFFFFF)
            else -> if (surfaceStyle == SurfaceStyle.GLASS) Color(0xA618202C) else Color(0xFF161D27)
        }

        val surfaceVariant = when {
            isAmoled -> Color(0xFF1A1A1E)
            isLight -> Color(0xFFF1F5F9)
            else -> Color(0xFF202937)
        }

        // On-colors with strict WCAG AA contrast enforcement
        val onBackground = ensureContrast(if (isLight) Color(0xFF0F172A) else Color(0xFFF8FAFC), background, 4.5f)
        val onSurface = ensureContrast(if (isLight) Color(0xFF1E293B) else Color(0xFFF1F5F9), surface, 4.5f)
        val onSurfaceVariant = ensureContrast(if (isLight) Color(0xFF475569) else Color(0xFFCBD5E1), surfaceVariant, 4.5f)

        val primaryContainer = when {
            isAmoled -> hsvToColor(hue, sat, 0.25f)
            isLight -> hsvToColor(hue, sat * 0.35f, 0.95f)
            else -> hsvToColor(hue, sat * 0.50f, 0.30f)
        }
        val onPrimaryContainer = ensureContrast(if (isLight) hsvToColor(hue, 1f, 0.30f) else hsvToColor(hue, 0.3f, 0.95f), primaryContainer, 4.5f)

        val outline = when {
            isLight -> Color(0xFFCBD5E1)
            isAmoled -> Color(0xFF333333)
            else -> Color(0xFF334155)
        }

        val error = Color(0xFFEF4444)
        val onError = Color.White

        return if (isLight) {
            lightColorScheme(
                primary = primary,
                onPrimary = onPrimary,
                primaryContainer = primaryContainer,
                onPrimaryContainer = onPrimaryContainer,
                secondary = secondary,
                onSecondary = if (calculateLuminance(secondary) > 0.45f) Color.Black else Color.White,
                tertiary = tertiary,
                onTertiary = if (calculateLuminance(tertiary) > 0.45f) Color.Black else Color.White,
                background = background,
                onBackground = onBackground,
                surface = surface,
                onSurface = onSurface,
                surfaceVariant = surfaceVariant,
                onSurfaceVariant = onSurfaceVariant,
                outline = outline,
                error = error,
                onError = onError
            )
        } else {
            darkColorScheme(
                primary = primary,
                onPrimary = onPrimary,
                primaryContainer = primaryContainer,
                onPrimaryContainer = onPrimaryContainer,
                secondary = secondary,
                onSecondary = if (calculateLuminance(secondary) > 0.45f) Color.Black else Color.White,
                tertiary = tertiary,
                onTertiary = if (calculateLuminance(tertiary) > 0.45f) Color.Black else Color.White,
                background = background,
                onBackground = onBackground,
                surface = surface,
                onSurface = onSurface,
                surfaceVariant = surfaceVariant,
                onSurfaceVariant = onSurfaceVariant,
                outline = outline,
                error = error,
                onError = onError
            )
        }
    }
}
