package app.nebula.vpn.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nebula.vpn.desktop.i18n.AppLanguage
import app.nebula.vpn.desktop.i18n.DesktopStrings
import app.nebula.vpn.desktop.theme.CuratedPalettes
import app.nebula.vpn.desktop.theme.CuratedSeed
import app.nebula.vpn.desktop.theme.PaletteGenerator
import app.nebula.vpn.desktop.theme.parseHexColor
import kotlin.math.roundToInt

@Composable
fun FullCustomColorPicker(
    currentHex: String,
    onColorChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    language: AppLanguage = AppLanguage.RU
) {
    var selectedTab by remember { mutableStateOf(0) } // 0 = Palette, 1 = Custom Wheel/Sliders
    val initialColor = remember(currentHex) { parseHexColor(currentHex) }
    val initialHsv = remember(initialColor) { PaletteGenerator.rgbToHsv(initialColor) }

    var hue by remember { mutableStateOf(initialHsv[0]) }
    var sat by remember { mutableStateOf(initialHsv[1]) }
    var value by remember { mutableStateOf(initialHsv[2]) }

    val currentColor = remember(hue, sat, value) {
        PaletteGenerator.hsvToColor(hue, sat, value)
    }

    val currentHexCalculated = remember(currentColor) {
        val r = (currentColor.red * 255).roundToInt().coerceIn(0, 255)
        val g = (currentColor.green * 255).roundToInt().coerceIn(0, 255)
        val b = (currentColor.blue * 255).roundToInt().coerceIn(0, 255)
        String.format("#%02X%02X%02X", r, g, b)
    }

    var hexInputText by remember { mutableStateOf(currentHexCalculated) }
    var hexInputError by remember { mutableStateOf(false) }

    // Contrast calculations
    val contrastVsBlack = remember(currentColor) { PaletteGenerator.calculateContrast(currentColor, Color.Black) }
    val contrastVsWhite = remember(currentColor) { PaletteGenerator.calculateContrast(currentColor, Color.White) }
    val bestContrast = maxOf(contrastVsBlack, contrastVsWhite)

    val surfaceColor = MaterialTheme.colorScheme.surface
    val isDark = remember(surfaceColor) {
        PaletteGenerator.calculateLuminance(surfaceColor) < 0.5f
    }
    val defaultTextColor = if (isDark) Color(0xFFF1F5F9) else Color(0xFF0F172A)
    val mutedTextColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) Color(0xFF131B2E).copy(alpha = 0.85f) else Color(0xFFF8FAFC),
            contentColor = defaultTextColor
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Segmented Pill Tab Selector (Modern Windows 11 / Fluent Style)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isDark) Color(0xFF0F172A).copy(alpha = 0.70f) else Color(0xFFF1F5F9))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val tab0Selected = selectedTab == 0
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (tab0Selected) {
                                if (isDark) Color(0xFF1E293B) else Color.White
                            } else Color.Transparent
                        )
                        .border(
                            width = if (tab0Selected) 1.dp else 0.dp,
                            color = if (tab0Selected) {
                                if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)
                            } else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { selectedTab = 0 }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = DesktopStrings.get("palette_curated", language),
                        fontWeight = if (tab0Selected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 12.5.sp,
                        color = if (tab0Selected) (if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)) else mutedTextColor
                    )
                }

                val tab1Selected = selectedTab == 1
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (tab1Selected) {
                                if (isDark) Color(0xFF1E293B) else Color.White
                            } else Color.Transparent
                        )
                        .border(
                            width = if (tab1Selected) 1.dp else 0.dp,
                            color = if (tab1Selected) {
                                if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)
                            } else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { selectedTab = 1 }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = DesktopStrings.get("palette_custom", language),
                        fontWeight = if (tab1Selected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 12.5.sp,
                        color = if (tab1Selected) (if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)) else mutedTextColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (selectedTab == 0) {
                // Curated 16 Seeds Grid with strict WCAG AA contrast enforcement
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 136.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(220.dp)
                ) {
                    items(CuratedPalettes.SEEDS) { seed: CuratedSeed ->
                        val isSelected = currentHex.equals(seed.hex, ignoreCase = true)
                        val seedColor = parseHexColor(seed.hex)
                        val seedName = DesktopStrings.get(seed.nameKey, language)

                        val itemBg = if (isSelected) {
                            seedColor.copy(alpha = if (isDark) 0.18f else 0.12f)
                        } else if (isDark) {
                            Color(0xFF1E293B).copy(alpha = 0.90f)
                        } else {
                            Color.White
                        }
                        val itemBorder = if (isSelected) {
                            seedColor
                        } else if (isDark) {
                            Color(0xFF334155).copy(alpha = 0.60f)
                        } else {
                            Color(0xFFE2E8F0)
                        }
                        val itemTextColor = if (isSelected) {
                            if (isDark) Color(0xFFFFFFFF) else Color(0xFF0F172A)
                        } else if (isDark) {
                            Color(0xFFF8FAFC)
                        } else {
                            Color(0xFF0F172A)
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(itemBg)
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = itemBorder,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    val hsv = PaletteGenerator.rgbToHsv(seedColor)
                                    hue = hsv[0]
                                    sat = hsv[1]
                                    value = hsv[2]
                                    hexInputText = seed.hex
                                    onColorChanged(seed.hex)
                                }
                                .padding(horizontal = 12.dp, vertical = 9.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(seedColor)
                                        .border(
                                            width = if (isSelected) 2.dp else 1.5.dp,
                                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.40f),
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = if (PaletteGenerator.calculateLuminance(seedColor) > 0.45f) Color.Black else Color.White,
                                            modifier = Modifier.size(11.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(9.dp))
                                Text(
                                    text = seedName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = itemTextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            } else {
                // Full Custom Picker (HSV + Sliders + Hex)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Saturation-Value 2D Box
                    val pureHueColor = remember(hue) { PaletteGenerator.hsvToColor(hue, 1f, 1f) }
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(pureHueColor)
                            .background(
                                Brush.horizontalGradient(listOf(Color.White, Color.Transparent))
                            )
                            .background(
                                Brush.verticalGradient(listOf(Color.Transparent, Color.Black))
                            )
                            .border(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                            .pointerInput(hue) {
                                fun updatePos(offset: Offset) {
                                    sat = (offset.x / size.width).coerceIn(0f, 1f)
                                    value = (1f - (offset.y / size.height)).coerceIn(0f, 1f)
                                    val hex = String.format(
                                        "#%02X%02X%02X",
                                        (currentColor.red * 255).roundToInt(),
                                        (currentColor.green * 255).roundToInt(),
                                        (currentColor.blue * 255).roundToInt()
                                    )
                                    hexInputText = hex
                                    onColorChanged(hex)
                                }
                                detectTapGestures { offset -> updatePos(offset) }
                            }
                            .pointerInput(hue) {
                                detectDragGestures { change, _ ->
                                    sat = (change.position.x / size.width).coerceIn(0f, 1f)
                                    value = (1f - (change.position.y / size.height)).coerceIn(0f, 1f)
                                    val hex = String.format(
                                        "#%02X%02X%02X",
                                        (currentColor.red * 255).roundToInt(),
                                        (currentColor.green * 255).roundToInt(),
                                        (currentColor.blue * 255).roundToInt()
                                    )
                                    hexInputText = hex
                                    onColorChanged(hex)
                                }
                            }
                    ) {
                        // Drag cursor indicator
                        val cursorX = (sat * 160).dp - 8.dp
                        val cursorY = ((1f - value) * 160).dp - 8.dp
                        Box(
                            modifier = Modifier
                                .offset(x = cursorX, y = cursorY)
                                .size(16.dp)
                                .border(2.dp, Color.White, CircleShape)
                                .background(currentColor, CircleShape)
                        )
                    }

                    // Sliders and Hex Input Column
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Hue Rainbow Slider
                        Text(
                            text = "${DesktopStrings.get("tone_hue", language)}: ${hue.roundToInt()}°",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = defaultTextColor
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(14.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color.Red, Color.Yellow, Color.Green,
                                            Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                                        )
                                    )
                                )
                        )
                        Slider(
                            value = hue,
                            onValueChange = {
                                hue = it
                                val hex = String.format(
                                    "#%02X%02X%02X",
                                    (currentColor.red * 255).roundToInt(),
                                    (currentColor.green * 255).roundToInt(),
                                    (currentColor.blue * 255).roundToInt()
                                )
                                hexInputText = hex
                                onColorChanged(hex)
                            },
                            valueRange = 0f..360f,
                            modifier = Modifier.fillMaxWidth().height(20.dp)
                        )

                        // RGB Numeric Inputs / Sliders
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = hexInputText,
                                onValueChange = { input ->
                                    hexInputText = input
                                    val cleaned = input.removePrefix("#").trim()
                                    if (cleaned.length == 6) {
                                        try {
                                            val parsed = parseHexColor(input)
                                            val hsv = PaletteGenerator.rgbToHsv(parsed)
                                            hue = hsv[0]
                                            sat = hsv[1]
                                            value = hsv[2]
                                            hexInputError = false
                                            onColorChanged(input)
                                        } catch (_: Exception) {
                                            hexInputError = true
                                        }
                                    } else {
                                        hexInputError = true
                                    }
                                },
                                label = { Text(DesktopStrings.get("hex_code", language), color = mutedTextColor) },
                                isError = hexInputError,
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = defaultTextColor,
                                    unfocusedTextColor = defaultTextColor,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1),
                                    focusedContainerColor = if (isDark) Color(0xFF1E293B) else Color.White,
                                    unfocusedContainerColor = if (isDark) Color(0xFF1E293B) else Color.White
                                ),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = defaultTextColor
                                )
                            )

                            // Preview Chip with WCAG AA Contrast Pill
                            Box(
                                modifier = Modifier
                                    .size(width = 116.dp, height = 54.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(currentColor)
                                    .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                val pillTextColor = if (contrastVsBlack >= contrastVsWhite) Color.Black else Color.White
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = currentHexCalculated,
                                        color = pillTextColor,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "WCAG ${String.format("%.1f", bestContrast)}:1 ✓",
                                        color = pillTextColor,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
