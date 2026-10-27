package app.nebula.vpn.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nebula.vpn.R
import app.nebula.vpn.core.model.PowerProfile
import app.nebula.vpn.ui.theme.NebulaTheme

@Composable
fun ToolsScreen(
    viewModel: ToolsViewModel,
    modifier: Modifier = Modifier
) {
    DisposableEffect(Unit) {
        viewModel.startReportTicker()
        onDispose {
            viewModel.stopReportTicker()
        }
    }

    val logs by viewModel.logs.collectAsState()
    val batteryReport by viewModel.batteryReport.collectAsState()
    val colors = NebulaTheme.colors
    val spacing = NebulaTheme.spacing
    val shapes = NebulaTheme.shapes
    val typography = NebulaTheme.typography

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(spacing.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.nav_tools),
                style = typography.displayLarge.copy(fontSize = 24.sp),
                color = colors.textPrimary
            )

            Button(
                onClick = { viewModel.batchPingAll() },
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                shape = shapes.button,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) {
                Icon(Icons.Default.Speed, contentDescription = null, tint = colors.onPrimary)
                Spacer(modifier = Modifier.width(spacing.xs))
                Text(
                    text = stringResource(R.string.auto_select_fastest),
                    style = typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = colors.onPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(spacing.md))

        // ⚡ Battery & CPU Diagnostics Card
        Card(
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            shape = shapes.card,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.borderSubtle, shapes.card)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.md)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.padding(end = spacing.xs)
                        )
                        Text(
                            text = "Battery & CPU Report",
                            style = typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.textPrimary
                        )
                    }
                    IconButton(
                        onClick = { viewModel.refreshBatteryReport() },
                        modifier = Modifier.defaultMinSize(minWidth = 36.dp, minHeight = 36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = colors.textMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(spacing.sm))

                // Stats row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "PROCESS CPU",
                            style = typography.labelSmall,
                            color = colors.textMuted
                        )
                        Text(
                            text = batteryReport.formattedCpuTime,
                            style = typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.primary
                        )
                    }
                    Column {
                        Text(
                            text = "APP UPTIME",
                            style = typography.labelSmall,
                            color = colors.textMuted
                        )
                        Text(
                            text = batteryReport.formattedUptime,
                            style = typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.textPrimary
                        )
                    }
                    Column {
                        Text(
                            text = "THREADS",
                            style = typography.labelSmall,
                            color = colors.textMuted
                        )
                        Text(
                            text = "${batteryReport.liveThreadCount}",
                            style = typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(spacing.sm))

                // Power Profile Chips
                Text(
                    text = "POWER PROFILE",
                    style = typography.labelSmall,
                    color = colors.textMuted
                )
                Spacer(modifier = Modifier.height(spacing.xxs))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    PowerProfile.entries.forEach { profile ->
                        val selected = batteryReport.powerProfile == profile
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.setPowerProfile(profile) },
                            label = {
                                Text(
                                    text = profile.displayName,
                                    style = typography.bodySmall.copy(
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.primary.copy(alpha = 0.2f),
                                selectedLabelColor = colors.primary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = if (selected) colors.primary else colors.borderSubtle
                            )
                        )
                    }
                }

                Text(
                    text = batteryReport.powerProfile.description,
                    style = typography.bodySmall.copy(fontSize = 11.sp),
                    color = colors.textMuted,
                    modifier = Modifier.padding(top = spacing.xxs, bottom = spacing.xs)
                )

                // Notification Speed Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Speed in notification",
                            style = typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Throttled to 3s when awake; frozen when screen is off",
                            style = typography.labelSmall,
                            color = colors.textMuted
                        )
                    }
                    Switch(
                        checked = batteryReport.showSpeedInNotification,
                        onCheckedChange = { viewModel.setShowSpeedInNotification(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.onPrimary,
                            checkedTrackColor = colors.primary
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(spacing.md))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.logs_title),
                style = typography.titleMedium,
                color = colors.textPrimary
            )

            IconButton(
                onClick = { viewModel.clearLogs() },
                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = stringResource(R.string.clear_logs),
                    tint = colors.textMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(spacing.xs))

        // Log Console
        Card(
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            shape = shapes.card,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(1.dp, colors.borderSubtle, shapes.card)
        ) {
            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(spacing.lg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "— Log stream is empty —",
                        style = typography.bodyMedium,
                        color = colors.textMuted
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(spacing.sm)
                ) {
                    items(logs) { log ->
                        val logColor = when {
                            log.contains("error", ignoreCase = true) || log.contains("fatal", ignoreCase = true) -> colors.statusError
                            log.contains("warn", ignoreCase = true) -> colors.statusWarning
                            log.contains("info", ignoreCase = true) -> colors.statusOk
                            else -> colors.textSecondary
                        }
                        Text(
                            text = log,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = logColor,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
