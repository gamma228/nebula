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
    val presetId: String = "emerald_cyber",
    val mode: ThemeMode = ThemeMode.AMOLED,
    val primaryColorHex: String = "#00D084",   // Soft Emerald
    val accentColorHex: String = "#06B6D4",    // Cool Mint Cyan
    val surfaceColorHex: String = "#13171C",   // Dark Muted Slate
    val backgroundColorHex: String = "#090C0E", // Pure OLED Dark
    val cornerRadiusDp: Int = 18,
    val buttonShape: ButtonShape = ButtonShape.ROUNDED,
    val neonBordersEnabled: Boolean = false,
    val glassBlurEnabled: Boolean = true,
    val reduceMotion: Boolean = false,
    val hapticFeedbackEnabled: Boolean = true,
    val fontScale: Float = 1.0f,
    val backgroundType: BackgroundType = BackgroundType.SOLID,
    val backgroundImageUri: String? = null,
    val backgroundBlurRadius: Int = 0,
    val backgroundDimAlpha: Float = 0.3f
)
