package app.nebula.vpn.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nebula.vpn.R
import app.nebula.vpn.ui.theme.NebulaTheme
import kotlin.math.abs

data class HsvColor(val hue: Float, val saturation: Float, val value: Float)

fun hexToHsv(hex: String): HsvColor {
    val clean = hex.removePrefix("#")
    val rgb = clean.toLongOrNull(16) ?: 0x00D084L
    val r = (((rgb shr 16) and 0xFF) / 255f).coerceIn(0f, 1f)
    val g = (((rgb shr 8) and 0xFF) / 255f).coerceIn(0f, 1f)
    val b = ((rgb and 0xFF) / 255f).coerceIn(0f, 1f)

    val max = maxOf(r, maxOf(g, b))
    val min = minOf(r, minOf(g, b))
    val delta = max - min

    var h = 0f
    if (delta > 0.0001f) {
        h = when (max) {
            r -> 60f * (((g - b) / delta) % 6f)
            g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }
        if (h < 0f) h += 360f
    }

    val s = if (max <= 0.0001f) 0f else delta / max
    val v = max

    return HsvColor(h.coerceIn(0f, 360f), s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))
}

fun hsvToColor(h: Float, s: Float, v: Float): Color {
    val c = v * s
    val x = c * (1f - abs(((h / 60f) % 2f) - 1f))
    val m = v - c

    val (rPrime, gPrime, bPrime) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }

    val r = ((rPrime + m) * 255f).toInt().coerceIn(0, 255)
    val g = ((gPrime + m) * 255f).toInt().coerceIn(0, 255)
    val b = ((bPrime + m) * 255f).toInt().coerceIn(0, 255)

    return Color(0xFF000000 or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong())
}

fun hsvToHex(h: Float, s: Float, v: Float): String {
    val c = v * s
    val x = c * (1f - abs(((h / 60f) % 2f) - 1f))
    val m = v - c

    val (rPrime, gPrime, bPrime) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }

    val r = ((rPrime + m) * 255f).toInt().coerceIn(0, 255)
    val g = ((gPrime + m) * 255f).toInt().coerceIn(0, 255)
    val b = ((bPrime + m) * 255f).toInt().coerceIn(0, 255)

    return String.format("#%02X%02X%02X", r, g, b)
}

val CURATED_SWATCHES = listOf(
    "#00D084", // Cyber Emerald
    "#00E5FF", // Neon Cyan
    "#8A2BE2", // Electric Violet
    "#E60049", // Crimson Red
    "#FF007F", // Neon Pink
    "#FF6B00", // Sunset Orange
    "#FFB300", // Solar Amber
    "#76FF03", // Cyber Lime
    "#2979FF", // Azure Blue
    "#00E676", // Mint Teal
    "#FF5252", // Ultra Coral
    "#FFFFFF"  // Pure White
)

@Composable
fun NebulaColorPicker(
    primaryColorHex: String,
    accentColorHex: String,
    onPrimaryColorChanged: (String) -> Unit,
    onAccentColorChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = NebulaTheme.colors
    val spacing = NebulaTheme.spacing
    val shapes = NebulaTheme.shapes
    val typography = NebulaTheme.typography

    var isEditingPrimary by remember { mutableStateOf(true) }
    val activeColorHex = if (isEditingPrimary) primaryColorHex else accentColorHex
    val onActiveColorChanged = if (isEditingPrimary) onPrimaryColorChanged else onAccentColorChanged

    val initialHsv = remember(activeColorHex) { hexToHsv(activeColorHex) }
    var hue by remember(activeColorHex) { mutableFloatStateOf(initialHsv.hue) }
    var saturation by remember(activeColorHex) { mutableFloatStateOf(initialHsv.saturation) }
    var value by remember(activeColorHex) { mutableFloatStateOf(initialHsv.value.coerceAtLeast(0.25f)) }

    val currentColor = remember(hue, saturation, value) {
        hsvToColor(hue, saturation, value)
    }
    val currentHex = remember(hue, saturation, value) {
        hsvToHex(hue, saturation, value)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = shapes.card,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, colors.borderSubtle, shapes.card)
    ) {
        Column(modifier = Modifier.padding(spacing.md)) {
            // Section Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.appearance_custom_color),
                    style = typography.titleMedium,
                    color = colors.textPrimary
                )

                // Current Hex Readout Badge
                Box(
                    modifier = Modifier
                        .clip(shapes.chip)
                        .background(currentColor.copy(alpha = 0.2f))
                        .border(1.dp, currentColor, shapes.chip)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = currentHex,
                        style = typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        ),
                        color = if (value < 0.4f) colors.textPrimary else currentColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.sm))

            // Target Selector: Primary Accent vs Secondary/Glow
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs)
            ) {
                ColorTargetChip(
                    title = stringResource(R.string.appearance_color_target_primary),
                    color = try { Color(0xFF000000 or (primaryColorHex.removePrefix("#").toLong(16))) } catch (_: Exception) { colors.primary },
                    isSelected = isEditingPrimary,
                    onClick = { isEditingPrimary = true },
                    modifier = Modifier.weight(1f)
                )
                ColorTargetChip(
                    title = stringResource(R.string.appearance_color_target_accent),
                    color = try { Color(0xFF000000 or (accentColorHex.removePrefix("#").toLong(16))) } catch (_: Exception) { colors.accent },
                    isSelected = !isEditingPrimary,
                    onClick = { isEditingPrimary = false },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(spacing.md))

            // Live Preview Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shapes.button)
                    .background(colors.surfaceElevated)
                    .border(1.dp, colors.borderSubtle, shapes.button)
                    .padding(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(shapes.chip)
                            .background(currentColor)
                            .border(2.dp, colors.borderSubtle, shapes.chip)
                    )
                    Spacer(modifier = Modifier.width(spacing.sm))
                    Column {
                        Text(
                            text = if (isEditingPrimary) stringResource(R.string.appearance_color_target_primary) else stringResource(R.string.appearance_color_target_accent),
                            style = typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.textPrimary
                        )
                        Text(
                            text = "RGB: ${((currentColor.red * 255).toInt())}, ${((currentColor.green * 255).toInt())}, ${((currentColor.blue * 255).toInt())}",
                            style = typography.bodySmall.copy(fontSize = 11.sp),
                            color = colors.textMuted
                        )
                    }
                }

                // Interactive Mini Sample Button
                Box(
                    modifier = Modifier
                        .clip(shapes.button)
                        .background(currentColor)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Sample",
                        style = typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (value > 0.8f && saturation < 0.35f) Color(0xFF101014) else Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.md))

            // 1. Hue Slider (Rainbow Gradient Track)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.appearance_hue),
                    style = typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = colors.textPrimary
                )
                Text(
                    text = "${hue.toInt()}°",
                    style = typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = colors.textMuted
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                contentAlignment = Alignment.Center
            ) {
                // Rainbow gradient track bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFFFF0000),
                                    Color(0xFFFFFF00),
                                    Color(0xFF00FF00),
                                    Color(0xFF00FFFF),
                                    Color(0xFF0000FF),
                                    Color(0xFFFF00FF),
                                    Color(0xFFFF0000)
                                )
                            )
                        )
                )
                Slider(
                    value = hue,
                    onValueChange = {
                        hue = it
                        onActiveColorChanged(hsvToHex(hue, saturation, value))
                    },
                    valueRange = 0f..360f,
                    colors = SliderDefaults.colors(
                        thumbColor = currentColor,
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(spacing.xs))

            // 2. Saturation Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.appearance_saturation),
                    style = typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = colors.textPrimary
                )
                Text(
                    text = "${(saturation * 100).toInt()}%",
                    style = typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = colors.textMuted
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                contentAlignment = Alignment.Center
            ) {
                val desaturatedColor = remember(hue, value) { hsvToColor(hue, 0f, value) }
                val fullySaturatedColor = remember(hue, value) { hsvToColor(hue, 1f, value) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(
                            Brush.horizontalGradient(listOf(desaturatedColor, fullySaturatedColor))
                        )
                )
                Slider(
                    value = saturation,
                    onValueChange = {
                        saturation = it
                        onActiveColorChanged(hsvToHex(hue, saturation, value))
                    },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = currentColor,
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(spacing.xs))

            // 3. Brightness Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.appearance_brightness),
                    style = typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = colors.textPrimary
                )
                Text(
                    text = "${(value * 100).toInt()}%",
                    style = typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = colors.textMuted
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                contentAlignment = Alignment.Center
            ) {
                val darkColor = remember(hue, saturation) { hsvToColor(hue, saturation, 0.20f) }
                val brightColor = remember(hue, saturation) { hsvToColor(hue, saturation, 1f) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(
                            Brush.horizontalGradient(listOf(darkColor, brightColor))
                        )
                )
                Slider(
                    value = value,
                    onValueChange = {
                        value = it
                        onActiveColorChanged(hsvToHex(hue, saturation, value))
                    },
                    valueRange = 0.20f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = currentColor,
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(spacing.md))

            // Quick Swatches Palette Header
            Text(
                text = stringResource(R.string.appearance_quick_swatches),
                style = typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(spacing.xs))

            // 2 rows of 6 swatches
            val firstRow = CURATED_SWATCHES.take(6)
            val secondRow = CURATED_SWATCHES.drop(6)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                firstRow.forEach { swatchHex ->
                    val swatchColor = Color(0xFF000000 or swatchHex.removePrefix("#").toLong(16))
                    val isSelected = activeColorHex.equals(swatchHex, ignoreCase = true)
                    SwatchCircle(
                        color = swatchColor,
                        isSelected = isSelected,
                        onClick = {
                            val hsv = hexToHsv(swatchHex)
                            hue = hsv.hue
                            saturation = hsv.saturation
                            value = hsv.value
                            onActiveColorChanged(swatchHex)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.xs))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                secondRow.forEach { swatchHex ->
                    val swatchColor = Color(0xFF000000 or swatchHex.removePrefix("#").toLong(16))
                    val isSelected = activeColorHex.equals(swatchHex, ignoreCase = true)
                    SwatchCircle(
                        color = swatchColor,
                        isSelected = isSelected,
                        onClick = {
                            val hsv = hexToHsv(swatchHex)
                            hue = hsv.hue
                            saturation = hsv.saturation
                            value = hsv.value
                            onActiveColorChanged(swatchHex)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorTargetChip(
    title: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = NebulaTheme.colors
    val shapes = NebulaTheme.shapes
    val typography = NebulaTheme.typography

    Box(
        modifier = modifier
            .clip(shapes.button)
            .background(if (isSelected) color.copy(alpha = 0.22f) else colors.surfaceElevated)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) color else colors.borderSubtle,
                shape = shapes.button
            )
            .clickable { onClick() }
            .defaultMinSize(minHeight = 44.dp)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                style = typography.bodySmall.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium),
                color = if (isSelected) colors.textPrimary else colors.textMuted,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SwatchCircle(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = NebulaTheme.colors

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) Color.White else colors.borderSubtle,
                shape = CircleShape
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}
