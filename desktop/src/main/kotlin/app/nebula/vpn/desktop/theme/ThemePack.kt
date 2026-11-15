package app.nebula.vpn.desktop.theme

import app.nebula.vpn.core.model.SurfaceStyle
import app.nebula.vpn.core.model.ThemeMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class NebulaThemePack(
    val version: Int = 1,
    val id: String,
    val name: String,
    val author: String = "Nebula Team",
    val description: String = "",
    val seedHex: String,
    val secondaryHex: String,
    val mode: String = "DARK", // "LIGHT", "DARK", "AMOLED", "SYSTEM"
    val surfaceStyle: String = "SOLID", // "SOLID", "GLASS", "MICA"
    val cornerRadiusDp: Int = 14,
    val fontScale: Float = 1.0f,
    val backgroundDimAlpha: Float = 0.35f,
    val wallpaperBase64: String? = null
)

object ThemePackManager {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    val BUILTIN_PACKS = listOf(
        NebulaThemePack(
            id = "emerald_cyber",
            name = "Cyber Emerald",
            description = "High-tech terminal neon emerald on deep charcoal slate",
            seedHex = "#00D084",
            secondaryHex = "#06B6D4",
            mode = "DARK",
            surfaceStyle = "GLASS",
            cornerRadiusDp = 14
        ),
        NebulaThemePack(
            id = "oled_crimson",
            name = "OLED Crimson",
            description = "Deep scarlet red accent on true pitch-black AMOLED surfaces",
            seedHex = "#FF3366",
            secondaryHex = "#FF7597",
            mode = "AMOLED",
            surfaceStyle = "SOLID",
            cornerRadiusDp = 16
        ),
        NebulaThemePack(
            id = "violet_neon",
            name = "Electric Violet",
            description = "Cyberpunk purple and hot pink with glowing card borders",
            seedHex = "#A855F7",
            secondaryHex = "#EC4899",
            mode = "DARK",
            surfaceStyle = "GLASS",
            cornerRadiusDp = 18
        ),
        NebulaThemePack(
            id = "nordic_frost",
            name = "Nordic Frost",
            description = "Subzero cyan and arctic blue over glacial deep sea navy",
            seedHex = "#00E5FF",
            secondaryHex = "#3B82F6",
            mode = "DARK",
            surfaceStyle = "GLASS",
            cornerRadiusDp = 12
        ),
        NebulaThemePack(
            id = "solar_amber",
            name = "Solar Amber",
            description = "Warm golden amber energy glow over obsidian slate",
            seedHex = "#F59E0B",
            secondaryHex = "#EF4444",
            mode = "DARK",
            surfaceStyle = "SOLID",
            cornerRadiusDp = 14
        ),
        NebulaThemePack(
            id = "monochrome_titanium",
            name = "Titanium Slate",
            description = "Minimalist matte gray tones engineered for zero distraction",
            seedHex = "#94A3B8",
            secondaryHex = "#64748B",
            mode = "DARK",
            surfaceStyle = "SOLID",
            cornerRadiusDp = 8
        ),
        NebulaThemePack(
            id = "midnight_sapphire",
            name = "Midnight Sapphire",
            description = "Deep cobalt sapphire on pitch OLED with electric indigo glow",
            seedHex = "#3B82F6",
            secondaryHex = "#6366F1",
            mode = "AMOLED",
            surfaceStyle = "GLASS",
            cornerRadiusDp = 16
        ),
        NebulaThemePack(
            id = "sakura_blossom",
            name = "Sakura Blossom",
            description = "Delicate Japanese cherry blossom pink on crisp porcelain light mode",
            seedHex = "#F43F5E",
            secondaryHex = "#FB7185",
            mode = "LIGHT",
            surfaceStyle = "SOLID",
            cornerRadiusDp = 20
        )
    )

    fun exportToFile(pack: NebulaThemePack, destinationFile: File): Boolean {
        return try {
            val content = json.encodeToString(pack)
            destinationFile.writeText(content)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun importFromFile(sourceFile: File): NebulaThemePack {
        return try {
            if (!sourceFile.exists()) return BUILTIN_PACKS[0]
            val content = sourceFile.readText()
            json.decodeFromString<NebulaThemePack>(content)
        } catch (_: Exception) {
            // Safe fallback: never crash on corrupted theme file
            BUILTIN_PACKS[0]
        }
    }

    fun findById(id: String): NebulaThemePack {
        return BUILTIN_PACKS.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: BUILTIN_PACKS[0]
    }
}
