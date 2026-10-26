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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.defaultMinSize
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
import app.nebula.vpn.ui.theme.NebulaTheme

@Composable
fun AppearanceScreen(
    viewModel: AppearanceViewModel,
    modifier: Modifier = Modifier
) {
    val theme by viewModel.activeTheme.collectAsState()
    val colors = NebulaTheme.colors
    val spacing = NebulaTheme.spacing
    val shapes = NebulaTheme.shapes
    val typography = NebulaTheme.typography

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
        item {
            Text(
                text = stringResource(R.string.appearance_title),
                style = typography.displayLarge.copy(fontSize = 24.sp),
                color = colors.textPrimary
            )
        }

        // Theme Presets
        item {
            Text(
                text = stringResource(R.string.theme_preset),
                style = typography.titleMedium,
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(spacing.xs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs)
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
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                shape = shapes.card,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.borderSubtle, shapes.card)
            ) {
                Column(modifier = Modifier.padding(spacing.md)) {
                    Text(
                        text = stringResource(R.string.appearance_display_mode),
                        style = typography.titleMedium,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(spacing.sm))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                    ) {
                        ThemeModeChip(
                            title = stringResource(R.string.theme_mode_amoled),
                            isSelected = theme.mode == ThemeMode.AMOLED,
                            onClick = { viewModel.setThemeMode(ThemeMode.AMOLED) },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeModeChip(
                            title = stringResource(R.string.theme_mode_dark),
                            isSelected = theme.mode == ThemeMode.DARK,
                            onClick = { viewModel.setThemeMode(ThemeMode.DARK) },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeModeChip(
                            title = stringResource(R.string.theme_mode_light),
                            isSelected = theme.mode == ThemeMode.LIGHT,
                            onClick = { viewModel.setThemeMode(ThemeMode.LIGHT) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Button Shape & Neon Borders
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                shape = shapes.card,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.borderSubtle, shapes.card)
            ) {
                Column(modifier = Modifier.padding(spacing.md)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.appearance_neon_borders),
                            style = typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            color = colors.textPrimary
                        )
                        Switch(
                            checked = theme.neonBordersEnabled,
                            onCheckedChange = { viewModel.toggleNeonBorders() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.primary,
                                checkedTrackColor = colors.primary.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(spacing.md))

                    Text(
                        text = stringResource(R.string.appearance_corner_style),
                        style = typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(spacing.xs))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                    ) {
                        ShapeChip(
                            title = stringResource(R.string.appearance_shape_squircle),
                            isSelected = theme.buttonShape == ButtonShape.SQUIRCLE,
                            modifier = Modifier.weight(1f)
                        ) {
                            viewModel.setButtonShape(ButtonShape.SQUIRCLE)
                        }
                        ShapeChip(
                            title = stringResource(R.string.appearance_shape_rounded),
                            isSelected = theme.buttonShape == ButtonShape.ROUNDED,
                            modifier = Modifier.weight(1f)
                        ) {
                            viewModel.setButtonShape(ButtonShape.ROUNDED)
                        }
                        ShapeChip(
                            title = stringResource(R.string.appearance_shape_sharp),
                            isSelected = theme.buttonShape == ButtonShape.SHARP,
                            modifier = Modifier.weight(1f)
                        ) {
                            viewModel.setButtonShape(ButtonShape.SHARP)
                        }
                    }

                    Spacer(modifier = Modifier.height(spacing.sm))

                    // Live Interactive Preview Box of Current Shape
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shapes.button)
                            .background(colors.surfaceElevated)
                            .border(1.dp, colors.primary.copy(alpha = 0.5f), shapes.button)
                            .padding(spacing.sm),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when (theme.buttonShape) {
                                ButtonShape.SQUIRCLE -> "✦ Squircle (16dp)"
                                ButtonShape.ROUNDED -> "● Rounded (24dp+)"
                                ButtonShape.SHARP -> "■ Sharp (4dp)"
                            },
                            style = typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = colors.primary
                        )
                    }
                }
            }
        }

        // Device Wallpaper Applier
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                shape = shapes.card,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.borderSubtle, shapes.card)
            ) {
                Column(modifier = Modifier.padding(spacing.md)) {
                    Text(
                        text = stringResource(R.string.background_title),
                        style = typography.titleMedium,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(spacing.sm))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                    ) {
                        Button(
                            onClick = { viewModel.applyWallpaper(WallpaperManager.FLAG_SYSTEM) },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceElevated),
                            shape = shapes.button,
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text(stringResource(R.string.wallpaper_apply_home), style = typography.bodySmall, color = colors.textPrimary)
                        }
                        Button(
                            onClick = { viewModel.applyWallpaper(WallpaperManager.FLAG_LOCK) },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceElevated),
                            shape = shapes.button,
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text(stringResource(R.string.wallpaper_apply_lock), style = typography.bodySmall, color = colors.textPrimary)
                        }
                        Button(
                            onClick = { viewModel.applyWallpaper(WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK) },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                            shape = shapes.button,
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text(stringResource(R.string.wallpaper_apply_both), style = typography.bodySmall, color = colors.onPrimary)
                        }
                    }
                }
            }
        }

        // Launcher Icon Variant
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                shape = shapes.card,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.borderSubtle, shapes.card)
            ) {
                Column(modifier = Modifier.padding(spacing.md)) {
                    Text(
                        text = stringResource(R.string.launcher_icon_title),
                        style = typography.titleMedium,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(spacing.sm))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.xxs)
                    ) {
                        IconChip("Crimson", modifier = Modifier.weight(1f)) { viewModel.switchLauncherIcon("Crimson") }
                        IconChip("Violet", modifier = Modifier.weight(1f)) { viewModel.switchLauncherIcon("Violet") }
                        IconChip("Mono", modifier = Modifier.weight(1f)) { viewModel.switchLauncherIcon("Monochrome") }
                        IconChip("Stealth", modifier = Modifier.weight(1f)) { viewModel.switchLauncherIcon("Stealth") }
                        IconChip("Notes", modifier = Modifier.weight(1f)) { viewModel.switchLauncherIcon("Notes") }
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
    val colors = NebulaTheme.colors
    val shapes = NebulaTheme.shapes
    val typography = NebulaTheme.typography

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) color.copy(alpha = 0.2f) else colors.surface
        ),
        shape = shapes.card,
        modifier = modifier
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) color else colors.borderSubtle,
                shape = shapes.card
            )
            .clickable { onClick() }
            .defaultMinSize(minHeight = 48.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(shapes.chip)
                    .background(color)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                style = typography.bodySmall.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal),
                color = colors.textPrimary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ThemeModeChip(
    title: String,
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
            .background(if (isSelected) colors.primary else colors.surfaceElevated)
            .border(1.dp, if (isSelected) colors.primary else colors.borderSubtle, shapes.button)
            .clickable { onClick() }
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = typography.bodySmall.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium),
            color = if (isSelected) colors.onPrimary else colors.textPrimary,
            maxLines = 1
        )
    }
}

@Composable
private fun ShapeChip(
    title: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = NebulaTheme.colors
    val shapes = NebulaTheme.shapes
    val typography = NebulaTheme.typography

    Box(
        modifier = modifier
            .clip(shapes.button)
            .background(if (isSelected) colors.primary else colors.surfaceElevated)
            .border(1.dp, if (isSelected) colors.primary else colors.borderSubtle, shapes.button)
            .clickable { onClick() }
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = typography.bodySmall.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, fontSize = 11.sp),
            color = if (isSelected) colors.onPrimary else colors.textPrimary,
            maxLines = 1
        )
    }
}

@Composable
private fun IconChip(
    title: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = NebulaTheme.colors
    val shapes = NebulaTheme.shapes
    val typography = NebulaTheme.typography

    Box(
        modifier = modifier
            .clip(shapes.chip)
            .background(colors.surfaceElevated)
            .border(1.dp, colors.borderSubtle, shapes.chip)
            .clickable { onClick() }
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = typography.bodySmall.copy(fontSize = 11.sp),
            color = colors.textPrimary,
            maxLines = 1
        )
    }
}
