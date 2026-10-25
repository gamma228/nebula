package app.nebula.vpn.ui.appearance

import android.app.WallpaperManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nebula.vpn.R
import app.nebula.vpn.core.model.ButtonShape
import app.nebula.vpn.core.model.ThemeMode

@Composable
fun AppearanceScreen(
    viewModel: AppearanceViewModel,
    modifier: Modifier = Modifier
) {
    val theme by viewModel.activeTheme.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.appearance_title),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Theme Presets
        item {
            Text(
                text = stringResource(R.string.theme_preset),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PresetCard(
                    title = "Emerald",
                    color = Color(0xFF00D084),
                    isSelected = theme.presetId == "emerald_cyber",
                    onClick = { viewModel.applyPreset("emerald_cyber") },
                    modifier = Modifier.weight(1f)
                )
                PresetCard(
                    title = "Crimson",
                    color = Color(0xFFE60049),
                    isSelected = theme.presetId == "oled_crimson",
                    onClick = { viewModel.applyPreset("oled_crimson") },
                    modifier = Modifier.weight(1f)
                )
                PresetCard(
                    title = "Violet",
                    color = Color(0xFF8A2BE2),
                    isSelected = theme.presetId == "cyber_violet",
                    onClick = { viewModel.applyPreset("cyber_violet") },
                    modifier = Modifier.weight(1f)
                )
                PresetCard(
                    title = "Mono",
                    color = Color(0xFFFFFFFF),
                    isSelected = theme.presetId == "monochrome",
                    onClick = { viewModel.applyPreset("monochrome") },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Theme Mode
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Display Mode",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ThemeModeChip(
                            title = stringResource(R.string.theme_mode_amoled),
                            isSelected = theme.mode == ThemeMode.AMOLED,
                            onClick = { viewModel.setThemeMode(ThemeMode.AMOLED) }
                        )
                        ThemeModeChip(
                            title = stringResource(R.string.theme_mode_dark),
                            isSelected = theme.mode == ThemeMode.DARK,
                            onClick = { viewModel.setThemeMode(ThemeMode.DARK) }
                        )
                        ThemeModeChip(
                            title = stringResource(R.string.theme_mode_light),
                            isSelected = theme.mode == ThemeMode.LIGHT,
                            onClick = { viewModel.setThemeMode(ThemeMode.LIGHT) }
                        )
                    }
                }
            }
        }

        // Button Shape & Neon Borders
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Neon Gradient Borders",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Switch(
                            checked = theme.neonBordersEnabled,
                            onCheckedChange = { viewModel.toggleNeonBorders() },
                            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Стиль углов (Форма интерфейса)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ShapeChip("Squircle (16dp)", theme.buttonShape == ButtonShape.SQUIRCLE) {
                            viewModel.setButtonShape(ButtonShape.SQUIRCLE)
                        }
                        ShapeChip("Capsule (Пилл)", theme.buttonShape == ButtonShape.ROUNDED) {
                            viewModel.setButtonShape(ButtonShape.ROUNDED)
                        }
                        ShapeChip("Sharp (Кибер)", theme.buttonShape == ButtonShape.SHARP) {
                            viewModel.setButtonShape(ButtonShape.SHARP)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Live Interactive Preview Box of Current Shape
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when (theme.buttonShape) {
                                ButtonShape.SQUIRCLE -> "✦ Squircle: Плавный суперэллипс (16dp)"
                                ButtonShape.ROUNDED -> "● Rounded: Полная капсула / Пилл (26dp+)"
                                ButtonShape.SHARP -> "■ Sharp: Высокоточный кибернетический срез (4dp)"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Device Wallpaper Applier
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Set System Wallpaper",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.applyWallpaper(WallpaperManager.FLAG_SYSTEM) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Home", fontSize = 12.sp)
                        }
                        Button(
                            onClick = { viewModel.applyWallpaper(WallpaperManager.FLAG_LOCK) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Lock", fontSize = 12.sp)
                        }
                        Button(
                            onClick = { viewModel.applyWallpaper(WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Both", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Launcher Icon Variant
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.launcher_icon_title),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconChip("Crimson") { viewModel.switchLauncherIcon("Crimson") }
                        IconChip("Violet") { viewModel.switchLauncherIcon("Violet") }
                        IconChip("Mono") { viewModel.switchLauncherIcon("Monochrome") }
                        IconChip("Stealth") { viewModel.switchLauncherIcon("Stealth") }
                        IconChip("Notes") { viewModel.switchLauncherIcon("Notes") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetCard(
    title: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) color.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(color)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ThemeModeChip(title: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ShapeChip(title: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun IconChip(title: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(text = title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}
