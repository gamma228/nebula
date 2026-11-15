package app.nebula.vpn.desktop

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.nebula.vpn.core.model.ThemeMode
import app.nebula.vpn.desktop.i18n.AppLanguage
import app.nebula.vpn.desktop.theme.DesktopTheme
import app.nebula.vpn.desktop.theme.DesktopThemePreset
import app.nebula.vpn.desktop.ui.components.FullCustomColorPicker
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Test
import java.io.File

class ColorPickerScreenshotTest {

    @Test
    fun renderColorPickerPreview() {
        val scene = ImageComposeScene(width = 680, height = 380)
        scene.setContent {
            DesktopTheme(mode = ThemeMode.DARK, preset = DesktopThemePreset.CUSTOM, customAccentHex = "#00D084") {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    FullCustomColorPicker(
                        currentHex = "#00D084",
                        onColorChanged = {},
                        language = AppLanguage.RU,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
        val img = scene.render()
        val bytes = img.encodeToData(EncodedImageFormat.PNG)!!.bytes

        val outFile = File("docs/screenshots/color_picker_redesign.png")
        outFile.parentFile.mkdirs()
        outFile.writeBytes(bytes)

        // Also copy to artifacts directory
        val artifactDir = File("C:/Users/User/.gemini/antigravity/brain/733adc2f-8452-4988-8393-aacf6cc68a82")
        if (artifactDir.exists()) {
            File(artifactDir, "color_picker_redesign.png").writeBytes(bytes)
        }
    }
}
