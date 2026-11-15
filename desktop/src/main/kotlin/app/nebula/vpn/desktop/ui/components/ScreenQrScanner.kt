package app.nebula.vpn.desktop.ui.components

import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.awt.GraphicsEnvironment
import java.awt.Robot

object ScreenQrScanner {

    /**
     * Scans all connected monitors for any visible QR code and decodes its payload text.
     * Returns decoded text or null if no QR code found.
     */
    fun scanAllScreens(): String? {
        return try {
            val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
            val screens = ge.screenDevices
            val reader = MultiFormatReader()

            for (screen in screens) {
                val bounds = screen.defaultConfiguration.bounds
                val robot = Robot(screen)
                val capture = robot.createScreenCapture(bounds)

                val width = capture.width
                val height = capture.height
                val pixels = IntArray(width * height)
                capture.getRGB(0, 0, width, height, pixels, 0, width)

                val source = RGBLuminanceSource(width, height, pixels)
                val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

                try {
                    val result = reader.decodeWithState(binaryBitmap)
                    reader.reset()
                    val text = result.text?.trim()
                    if (!text.isNullOrEmpty()) {
                        return text
                    }
                } catch (_: Exception) {
                    reader.reset()
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }
}
