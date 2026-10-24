package app.nebula.vpn.ui.appearance

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nebula.vpn.core.model.ButtonShape
import app.nebula.vpn.core.model.ThemeConfig
import app.nebula.vpn.core.model.ThemeMode
import app.nebula.vpn.data.repository.ThemeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class AppearanceViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val themeRepository: ThemeRepository
) : ViewModel() {

    val activeTheme: StateFlow<ThemeConfig> = themeRepository.activeTheme.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ThemeRepository.PRESET_OLED_CRIMSON
    )

    fun applyPreset(presetId: String) {
        viewModelScope.launch {
            val preset = when (presetId) {
                "cyber_violet" -> ThemeRepository.PRESET_CYBER_VIOLET
                "monochrome" -> ThemeRepository.PRESET_MONOCHROME
                else -> ThemeRepository.PRESET_OLED_CRIMSON
            }
            themeRepository.setActiveTheme(preset)
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

    fun applyWallpaper(which: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val wm = WallpaperManager.getInstance(context)
                val width = wm.desiredMinimumWidth.coerceAtLeast(1080)
                val height = wm.desiredMinimumHeight.coerceAtLeast(1920)

                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                val paint = Paint()

                // Draw gradient matching current theme
                paint.color = android.graphics.Color.parseColor(activeTheme.value.backgroundColorHex)
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    wm.setBitmap(bitmap, null, true, which)
                } else {
                    wm.setBitmap(bitmap)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
