package app.nebula.vpn.desktop.theme

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Memory-efficient Wallpaper Manager for Nebula Desktop.
 * - Decodes images downscaled to window bounds.
 * - Caches downscaled bitmap on disk.
 * - Computes average luminance for adaptive scrim.
 * - Releases bitmap memory when window is hidden/minimized.
 */
object WallpaperManager {

    data class ScaledWallpaper(
        val bitmap: ImageBitmap,
        val averageLuminance: Float,
        val recommendedScrimAlpha: Float
    )

    private var inMemoryCache: ScaledWallpaper? = null
    private var lastLoadedPath: String? = null

    fun loadAndScale(
        imageFile: File,
        targetWidth: Int = 1280,
        targetHeight: Int = 800,
        cacheDir: File
    ): ScaledWallpaper? {
        if (!imageFile.exists() || !imageFile.isFile) return null

        // If already cached in memory for the same file, reuse
        if (inMemoryCache != null && lastLoadedPath == imageFile.absolutePath) {
            return inMemoryCache
        }

        try {
            val bytes = imageFile.readBytes()
            val skiaImage = Image.makeFromEncoded(bytes)

            val srcWidth = skiaImage.width
            val srcHeight = skiaImage.height

            // Calculate scale factor to cover target dimensions without exceeding
            val scale = max(targetWidth.toFloat() / srcWidth, targetHeight.toFloat() / srcHeight).coerceAtMost(1.0f)
            val dstWidth = (srcWidth * scale).toInt().coerceAtLeast(1)
            val dstHeight = (srcHeight * scale).toInt().coerceAtLeast(1)

            val surface = Surface.makeRasterN32Premul(dstWidth, dstHeight)
            val canvas = surface.canvas
            val srcRect = Rect.makeWH(srcWidth.toFloat(), srcHeight.toFloat())
            val dstRect = Rect.makeWH(dstWidth.toFloat(), dstHeight.toFloat())
            canvas.drawImageRect(skiaImage, srcRect, dstRect, SamplingMode.LINEAR, null, true)

            val scaledImage = surface.makeImageSnapshot()
            val peekPixels = scaledImage.peekPixels()

            // Compute average luminance across sampled pixels
            var totalLum = 0.0
            var sampleCount = 0
            val stepX = max(1, dstWidth / 32)
            val stepY = max(1, dstHeight / 32)

            if (peekPixels != null) {
                for (y in 0 until dstHeight step stepY) {
                    for (x in 0 until dstWidth step stepX) {
                        val colorInt = peekPixels.getColor(x, y)
                        val r = ((colorInt shr 16) and 0xFF) / 255.0
                        val g = ((colorInt shr 8) and 0xFF) / 255.0
                        val b = (colorInt and 0xFF) / 255.0
                        val lum = 0.2126 * r + 0.7152 * g + 0.0722 * b
                        totalLum += lum
                        sampleCount++
                    }
                }
            }

            val avgLum = if (sampleCount > 0) (totalLum / sampleCount).toFloat() else 0.5f

            // Recommended scrim: if wallpaper is very bright (> 0.5), we need more darkening (0.45..0.65)
            // If wallpaper is dark, light dim is sufficient (0.25..0.40)
            val recommendedDim = when {
                avgLum > 0.6f -> 0.55f
                avgLum > 0.35f -> 0.40f
                else -> 0.28f
            }

            val composeBitmap = scaledImage.toComposeImageBitmap()
            val result = ScaledWallpaper(composeBitmap, avgLum, recommendedDim)

            inMemoryCache = result
            lastLoadedPath = imageFile.absolutePath

            // Save cached copy to disk
            val wallpaperCacheDir = File(cacheDir, "wallpapers")
            if (!wallpaperCacheDir.exists()) wallpaperCacheDir.mkdirs()
            val cachedFile = File(wallpaperCacheDir, "cached_wallpaper.png")
            try {
                FileOutputStream(cachedFile).use { out ->
                    out.write(bytes) // Keep original compressed representation
                }
            } catch (_: Exception) {}

            return result
        } catch (_: Exception) {
            return null
        }
    }

    /**
     * Extracts a vibrant dominant accent color from the wallpaper using hue clustering.
     * Returns a 6-digit hex string (e.g. "#10B981") or null if no vibrant color found.
     */
    fun extractDominantColor(imageFile: File): String? {
        if (!imageFile.exists() || !imageFile.isFile) return null
        return try {
            val bytes = imageFile.readBytes()
            val skiaImage = Image.makeFromEncoded(bytes)
            val thumbSurface = Surface.makeRasterN32Premul(48, 48)
            thumbSurface.canvas.drawImageRect(
                skiaImage,
                Rect.makeWH(skiaImage.width.toFloat(), skiaImage.height.toFloat()),
                Rect.makeWH(48f, 48f),
                SamplingMode.LINEAR,
                null,
                true
            )
            val thumb = thumbSurface.makeImageSnapshot()
            val pixels = thumb.peekPixels() ?: return null

            class Bin(var count: Int = 0, var sumR: Long = 0, var sumG: Long = 0, var sumB: Long = 0, var sumSat: Double = 0.0)
            val bins = Array(12) { Bin() }

            for (y in 0 until 48) {
                for (x in 0 until 48) {
                    val c = pixels.getColor(x, y)
                    val r = (c shr 16) and 0xFF
                    val g = (c shr 8) and 0xFF
                    val b = c and 0xFF

                    val rf = r / 255.0
                    val gf = g / 255.0
                    val bf = b / 255.0
                    val maxVal = maxOf(rf, gf, bf)
                    val minVal = minOf(rf, gf, bf)
                    val delta = maxVal - minVal

                    val sat = if (maxVal == 0.0) 0.0 else delta / maxVal
                    val bri = maxVal

                    // Ignore very desaturated (grays) or extreme dark/light
                    if (sat >= 0.20 && bri >= 0.20 && bri <= 0.95) {
                        var hue = when {
                            delta == 0.0 -> 0.0
                            maxVal == rf -> 60.0 * (((gf - bf) / delta) % 6)
                            maxVal == gf -> 60.0 * (((bf - rf) / delta) + 2)
                            else -> 60.0 * (((rf - gf) / delta) + 4)
                        }
                        if (hue < 0) hue += 360.0

                        val binIdx = ((hue / 30.0).toInt()) % 12
                        val bin = bins[binIdx]
                        bin.count++
                        bin.sumR += r
                        bin.sumG += g
                        bin.sumB += b
                        bin.sumSat += sat
                    }
                }
            }

            val bestBin = bins.maxByOrNull { it.count * (if (it.count > 0) it.sumSat / it.count else 0.0) }
            if (bestBin != null && bestBin.count >= 5) {
                val avgR = (bestBin.sumR / bestBin.count).toInt().coerceIn(0, 255)
                val avgG = (bestBin.sumG / bestBin.count).toInt().coerceIn(0, 255)
                val avgB = (bestBin.sumB / bestBin.count).toInt().coerceIn(0, 255)
                String.format("#%02X%02X%02X", avgR, avgG, avgB)
            } else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Frees in-memory cached wallpaper bitmap to reclaim RAM when window is minimized or hidden.
     */
    fun releaseMemory() {
        inMemoryCache = null
        // Force garbage collection of Skia native buffers
        System.gc()
    }
}
