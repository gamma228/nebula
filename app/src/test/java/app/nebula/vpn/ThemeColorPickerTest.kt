package app.nebula.vpn

import app.nebula.vpn.core.model.ThemeConfig
import app.nebula.vpn.core.model.ThemeMode
import app.nebula.vpn.ui.components.CURATED_SWATCHES
import app.nebula.vpn.ui.components.hexToHsv
import app.nebula.vpn.ui.components.hsvToColor
import app.nebula.vpn.ui.components.hsvToHex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeColorPickerTest {

    @Test
    fun testHexToHsvAndBackConversion() {
        // Test primary green
        val greenHsv = hexToHsv("#00D084")
        assertTrue("Hue should be around green (140..170)", greenHsv.hue in 140f..170f)
        assertTrue("Saturation should be high", greenHsv.saturation > 0.8f)
        assertTrue("Value should be high", greenHsv.value > 0.7f)

        val reconstructedHex = hsvToHex(greenHsv.hue, greenHsv.saturation, greenHsv.value)
        assertEquals("#00D084", reconstructedHex.uppercase())

        // Test crimson red
        val redHsv = hexToHsv("#E60049")
        assertTrue("Hue should be near red/magenta (330..360)", redHsv.hue in 330f..360f)
        val reconstructedRed = hsvToHex(redHsv.hue, redHsv.saturation, redHsv.value)
        assertEquals("#E60049", reconstructedRed.uppercase())

        // Test white
        val whiteHsv = hexToHsv("#FFFFFF")
        assertEquals(0f, whiteHsv.saturation, 0.01f)
        assertEquals(1f, whiteHsv.value, 0.01f)
        assertEquals("#FFFFFF", hsvToHex(whiteHsv.hue, whiteHsv.saturation, whiteHsv.value).uppercase())
    }

    @Test
    fun testCuratedSwatchesAllValidHex() {
        assertEquals("Should have 12 curated swatches", 12, CURATED_SWATCHES.size)
        for (swatch in CURATED_SWATCHES) {
            assertTrue("Swatch must start with #", swatch.startsWith("#"))
            assertEquals("Swatch must be 7 characters (#RRGGBB)", 7, swatch.length)
            val hsv = hexToHsv(swatch)
            val color = hsvToColor(hsv.hue, hsv.saturation, hsv.value)
            assertEquals("Color should have full alpha", 1f, color.alpha, 0.01f)
        }
    }

    @Test
    fun testThemeModeAmoledVsDarkDistinction() {
        val amoledConfig = ThemeConfig(mode = ThemeMode.AMOLED)
        val darkConfig = ThemeConfig(mode = ThemeMode.DARK)

        assertNotEquals("AMOLED and Dark modes must be distinct enum values", amoledConfig.mode, darkConfig.mode)
        assertEquals(ThemeMode.AMOLED, amoledConfig.mode)
        assertEquals(ThemeMode.DARK, darkConfig.mode)
    }
}
