package app.nebula.vpn.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode {
    LIGHT,
    DARK,
    AMOLED,
    SYSTEM
}

@Serializable
enum class ButtonShape {
    ROUNDED,
    SQUIRCLE,
    SHARP
}

@Serializable
enum class BackgroundType {
    SOLID,
    GRADIENT,
    USER_IMAGE
}

@Serializable
data class ThemeConfig(
    val presetId: String = "oled_crimson",
    val mode: ThemeMode = ThemeMode.AMOLED,
    val primaryColorHex: String = "#E60049",   // Crimson Red
    val accentColorHex: String = "#8A2BE2",    // Electric Violet
    val surfaceColorHex: String = "#121217",   // Slate Grey
    val backgroundColorHex: String = "#050508", // Deep Black
    val cornerRadiusDp: Int = 16,
    val buttonShape: ButtonShape = ButtonShape.SQUIRCLE,
    val neonBordersEnabled: Boolean = true,
    val glassBlurEnabled: Boolean = true,
    val reduceMotion: Boolean = false,
    val hapticFeedbackEnabled: Boolean = true,
    val fontScale: Float = 1.0f,
    val backgroundType: BackgroundType = BackgroundType.SOLID,
    val backgroundImageUri: String? = null,
    val backgroundBlurRadius: Int = 0,
    val backgroundDimAlpha: Float = 0.3f
)
