package app.nebula.vpn.desktop

import androidx.compose.ui.ImageComposeScene
import app.nebula.vpn.core.model.SurfaceStyle
import app.nebula.vpn.core.model.ThemeMode
import app.nebula.vpn.desktop.state.DesktopAppState
import app.nebula.vpn.desktop.theme.DesktopThemePreset
import app.nebula.vpn.desktop.ui.DesktopApp
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Test
import java.io.File

class ThemeScreenshotTest {

    @Test
    fun generateThemeScreenshots() {
        val outDir = File("docs/screenshots/themes")
        if (!outDir.exists()) outDir.mkdirs()

        val themes = listOf(
            Triple("theme_emerald_cyber_dark", ThemeMode.DARK, DesktopThemePreset.EMERALD_CYBER),
            Triple("theme_cyber_violet_light", ThemeMode.LIGHT, DesktopThemePreset.CYBER_VIOLET),
            Triple("theme_oled_crimson_amoled", ThemeMode.AMOLED, DesktopThemePreset.OLED_CRIMSON),
            Triple("theme_nordic_frost_glass", ThemeMode.DARK, DesktopThemePreset.NORDIC_FROST)
        )

        for ((filename, mode, preset) in themes) {
            val scene = ImageComposeScene(width = 980, height = 640)
            val state = DesktopAppState()
            state.currentThemeMode = mode
            state.currentThemePreset = preset
            if (filename.contains("glass")) {
                state.surfaceStyle = SurfaceStyle.GLASS
            }

            scene.setContent {
                DesktopApp(state = state)
            }

            val image = scene.render()
            val pngBytes = image.encodeToData(EncodedImageFormat.PNG)?.bytes
            if (pngBytes != null) {
                File(outDir, "$filename.png").writeBytes(pngBytes)
            }
            scene.close()
        }
    }
}
