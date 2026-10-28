package app.nebula.vpn.ui.appearance

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nebula.vpn.core.model.BackgroundType
import app.nebula.vpn.core.model.ButtonShape
import app.nebula.vpn.core.model.ThemeConfig
import app.nebula.vpn.core.model.ThemeMode
import app.nebula.vpn.data.repository.ThemeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppearanceViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val themeRepository: ThemeRepository
) : ViewModel() {

    val activeTheme: StateFlow<ThemeConfig> = themeRepository.activeTheme.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ThemeRepository.PRESET_EMERALD_CYBER
    )

    fun applyPreset(presetId: String) {
        viewModelScope.launch {
            val current = activeTheme.value
            val preset = when (presetId) {
                "emerald_cyber" -> ThemeRepository.PRESET_EMERALD_CYBER
                "cyber_violet" -> ThemeRepository.PRESET_CYBER_VIOLET
                "monochrome" -> ThemeRepository.PRESET_MONOCHROME
                "oled_crimson" -> ThemeRepository.PRESET_OLED_CRIMSON
                else -> ThemeRepository.PRESET_EMERALD_CYBER
            }
            themeRepository.setActiveTheme(preset.copy(mode = current.mode))
        }
    }

    fun setPrimaryColor(hex: String) {
        viewModelScope.launch {
            val current = activeTheme.value
            themeRepository.setActiveTheme(
                current.copy(
                    presetId = "custom",
                    primaryColorHex = hex
                )
            )
        }
    }

    fun setAccentColor(hex: String) {
        viewModelScope.launch {
            val current = activeTheme.value
            themeRepository.setActiveTheme(
                current.copy(
                    presetId = "custom",
                    accentColorHex = hex
                )
            )
        }
    }

    fun setCustomPalette(primaryHex: String, accentHex: String) {
        viewModelScope.launch {
            val current = activeTheme.value
            themeRepository.setActiveTheme(
                current.copy(
                    presetId = "custom",
                    primaryColorHex = primaryHex,
                    accentColorHex = accentHex
                )
            )
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            val current = activeTheme.value
            themeRepository.setActiveTheme(current.copy(mode = mode))
        }
    }

    fun setButtonShape(shape: ButtonShape) {
        viewModelScope.launch {
            val current = activeTheme.value
            themeRepository.setActiveTheme(current.copy(buttonShape = shape))
        }
    }

    fun toggleNeonBorders() {
        viewModelScope.launch {
            val current = activeTheme.value
            themeRepository.setActiveTheme(current.copy(neonBordersEnabled = !current.neonBordersEnabled))
        }
    }

    fun switchLauncherIcon(aliasSuffix: String) {
        val pm = context.packageManager
        val aliases = listOf(
            "MainActivityAliasCrimson",
            "MainActivityAliasViolet",
            "MainActivityAliasMonochrome",
            "MainActivityAliasStealth",
            "MainActivityAliasNotes"
        )

        for (alias in aliases) {
            val component = ComponentName(context.packageName, "${context.packageName}.$alias")
            val targetState = if (alias.endsWith(aliasSuffix)) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            pm.setComponentEnabledSetting(
                component,
                targetState,
                PackageManager.DONT_KILL_APP
            )
        }
    }

    fun setInAppBackgroundImage(uriString: String) {
        viewModelScope.launch {
            val current = activeTheme.value
            themeRepository.setActiveTheme(
                current.copy(
                    backgroundType = BackgroundType.USER_IMAGE,
                    backgroundImageUri = uriString
                )
            )
        }
    }

    fun setBackgroundDim(dimAlpha: Float) {
        viewModelScope.launch {
            val current = activeTheme.value
            themeRepository.setActiveTheme(current.copy(backgroundDimAlpha = dimAlpha))
        }
    }

    fun setBackgroundBlur(blurDp: Int) {
        viewModelScope.launch {
            val current = activeTheme.value
            themeRepository.setActiveTheme(current.copy(backgroundBlurRadius = blurDp))
        }
    }

    fun clearInAppBackground() {
        viewModelScope.launch {
            val current = activeTheme.value
            themeRepository.setActiveTheme(
                current.copy(
                    backgroundType = BackgroundType.SOLID,
                    backgroundImageUri = null
                )
            )
        }
    }
}
