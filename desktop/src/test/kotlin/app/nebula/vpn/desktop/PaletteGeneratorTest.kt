package app.nebula.vpn.desktop

import androidx.compose.ui.graphics.Color
import app.nebula.vpn.core.model.SurfaceStyle
import app.nebula.vpn.core.model.ThemeMode
import app.nebula.vpn.desktop.theme.CuratedPalettes
import app.nebula.vpn.desktop.theme.PaletteGenerator
import app.nebula.vpn.desktop.theme.parseHexColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteGeneratorTest {

    @Test
    fun testWcagContrastOnAllCuratedSeeds() {
        for (seed in CuratedPalettes.SEEDS) {
            val seedColor = parseHexColor(seed.hex)

            for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.AMOLED)) {
                for (style in listOf(SurfaceStyle.SOLID, SurfaceStyle.GLASS)) {
                    val scheme = PaletteGenerator.generateScheme(seedColor, mode, style)

                    // 1. Text on Surface must meet WCAG AA (>= 4.5:1)
                    val onSurfaceRatio = PaletteGenerator.calculateContrast(scheme.onSurface, scheme.surface)
                    assertTrue(
                        "OnSurface contrast (${seed.nameEn}, $mode, $style) must be >= 4.5:1 but was $onSurfaceRatio",
                        onSurfaceRatio >= 4.5f
                    )

                    // 2. Text on Background must meet WCAG AA (>= 4.5:1)
                    val onBgRatio = PaletteGenerator.calculateContrast(scheme.onBackground, scheme.background)
                    assertTrue(
                        "OnBackground contrast (${seed.nameEn}, $mode, $style) must be >= 4.5:1 but was $onBgRatio",
                        onBgRatio >= 4.5f
                    )

                    // 3. Text on Primary must meet WCAG AA (>= 4.5:1)
                    val onPrimaryRatio = PaletteGenerator.calculateContrast(scheme.onPrimary, scheme.primary)
                    assertTrue(
                        "OnPrimary contrast (${seed.nameEn}, $mode, $style) must be >= 4.5:1 but was $onPrimaryRatio",
                        onPrimaryRatio >= 4.5f
                    )
                }
            }
        }
    }

    @Test
    fun testExtremeCustomSeedColorsMaintainContrast() {
        val extremeSeeds = listOf(
            "#FFFFFF", // Pure white
            "#000000", // Pure black
            "#FFFF00", // High-luminance neon yellow
            "#0000FF", // Low-luminance pure blue
            "#7F7F7F", // Mid-gray (worst case for contrast)
            "#FF00FF", // Magenta
            "#00FFFF", // Cyan
            "#FDE047", // Pastel yellow
            "#1E1B4B"  // Ultra-dark indigo
        )

        for (hex in extremeSeeds) {
            val color = parseHexColor(hex)
            for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.AMOLED)) {
                val scheme = PaletteGenerator.generateScheme(color, mode)

                val onPrimaryRatio = PaletteGenerator.calculateContrast(scheme.onPrimary, scheme.primary)
                assertTrue(
                    "Extreme seed $hex onPrimary contrast in $mode must be >= 4.5:1 but was $onPrimaryRatio",
                    onPrimaryRatio >= 4.5f
                )

                val onSurfaceRatio = PaletteGenerator.calculateContrast(scheme.onSurface, scheme.surface)
                assertTrue(
                    "Extreme seed $hex onSurface contrast in $mode must be >= 4.5:1 but was $onSurfaceRatio",
                    onSurfaceRatio >= 4.5f
                )
            }
        }
    }

    @Test
    fun testHsvRgbRoundTrip() {
        val original = Color(0xFF22C55E) // Forest green
        val hsv = PaletteGenerator.rgbToHsv(original)
        val reconstructed = PaletteGenerator.hsvToColor(hsv[0], hsv[1], hsv[2])

        assertEquals("Red channel must match", original.red, reconstructed.red, 0.02f)
        assertEquals("Green channel must match", original.green, reconstructed.green, 0.02f)
        assertEquals("Blue channel must match", original.blue, reconstructed.blue, 0.02f)
    }

    @Test
    fun testLuminanceLimits() {
        val blackLum = PaletteGenerator.calculateLuminance(Color.Black)
        val whiteLum = PaletteGenerator.calculateLuminance(Color.White)
        assertEquals(0f, blackLum, 0.001f)
        assertEquals(1f, whiteLum, 0.001f)

        val maxContrast = PaletteGenerator.calculateContrast(Color.White, Color.Black)
        assertEquals(21.0f, maxContrast, 0.1f)
    }

    @Test
    fun testTwentyRandomSeedsContrastInLightAndDark() {
        val random = java.util.Random(42L)
        for (i in 0 until 20) {
            val r = random.nextFloat()
            val g = random.nextFloat()
            val b = random.nextFloat()
            val randomColor = Color(r, g, b, 1.0f)

            for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.AMOLED)) {
                val scheme = PaletteGenerator.generateScheme(randomColor, mode)

                val onPrimaryRatio = PaletteGenerator.calculateContrast(scheme.onPrimary, scheme.primary)
                assertTrue(
                    "Random seed #$i ($randomColor) onPrimary contrast in $mode must be >= 4.5:1 but was $onPrimaryRatio",
                    onPrimaryRatio >= 4.5f
                )

                val onSurfaceRatio = PaletteGenerator.calculateContrast(scheme.onSurface, scheme.surface)
                assertTrue(
                    "Random seed #$i ($randomColor) onSurface contrast in $mode must be >= 4.5:1 but was $onSurfaceRatio",
                    onSurfaceRatio >= 4.5f
                )
            }
        }
    }
}
