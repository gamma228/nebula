package app.nebula.vpn.data.repository

import app.nebula.vpn.core.model.ButtonShape
import app.nebula.vpn.core.model.ThemeConfig
import app.nebula.vpn.core.model.ThemeMode
import app.nebula.vpn.data.database.dao.ThemeDao
import app.nebula.vpn.data.database.entity.ThemeEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThemeRepository @Inject constructor(
    private val themeDao: ThemeDao,
    private val settingsRepository: SettingsRepository
) {
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        val PRESET_EMERALD_CYBER = ThemeConfig(
            presetId = "emerald_cyber",
            mode = ThemeMode.AMOLED,
            primaryColorHex = "#00D084",
            accentColorHex = "#06B6D4",
            surfaceColorHex = "#13171C",
            backgroundColorHex = "#090C0E",
            buttonShape = ButtonShape.ROUNDED,
            cornerRadiusDp = 18,
            neonBordersEnabled = false
        )

        val PRESET_OLED_CRIMSON = ThemeConfig(
            presetId = "oled_crimson",
            mode = ThemeMode.AMOLED,
            primaryColorHex = "#E60049",
            accentColorHex = "#8A2BE2",
            surfaceColorHex = "#121217",
            backgroundColorHex = "#050508",
            buttonShape = ButtonShape.SQUIRCLE,
            cornerRadiusDp = 16,
            neonBordersEnabled = true
        )

        val PRESET_CYBER_VIOLET = ThemeConfig(
            presetId = "cyber_violet",
            mode = ThemeMode.DARK,
            primaryColorHex = "#8A2BE2",
            accentColorHex = "#00FFFF",
            surfaceColorHex = "#161622",
            backgroundColorHex = "#0B0B12",
            buttonShape = ButtonShape.ROUNDED,
            cornerRadiusDp = 20,
            neonBordersEnabled = true
        )

        val PRESET_MONOCHROME = ThemeConfig(
            presetId = "monochrome",
            mode = ThemeMode.AMOLED,
            primaryColorHex = "#FFFFFF",
            accentColorHex = "#A0A0A0",
            surfaceColorHex = "#1A1A1A",
            backgroundColorHex = "#000000",
            buttonShape = ButtonShape.SHARP,
            cornerRadiusDp = 8,
            neonBordersEnabled = false
        )
    }

    val activeTheme: Flow<ThemeConfig> = settingsRepository.themeConfigJson.map { jsonStr ->
        if (jsonStr != null) {
            try {
                json.decodeFromString<ThemeConfig>(jsonStr)
            } catch (e: Exception) {
                PRESET_EMERALD_CYBER
            }
        } else {
            PRESET_EMERALD_CYBER
        }
    }

    suspend fun setActiveTheme(theme: ThemeConfig) {
        settingsRepository.setThemeConfigJson(json.encodeToString(ThemeConfig.serializer(), theme))
    }

    val customThemes: Flow<List<ThemeConfig>> = themeDao.getAllThemes().map { entities ->
        entities.mapNotNull {
            try {
                json.decodeFromString<ThemeConfig>(it.configJson)
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun saveCustomTheme(name: String, theme: ThemeConfig) {
        val configStr = json.encodeToString(ThemeConfig.serializer(), theme)
        themeDao.insertTheme(
            ThemeEntity(
                id = theme.presetId,
                name = name,
                configJson = configStr,
                isPreset = false
            )
        )
    }
}
