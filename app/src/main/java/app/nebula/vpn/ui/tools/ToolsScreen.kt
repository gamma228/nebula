package app.nebula.vpn.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.TextButton
import app.nebula.vpn.ui.theme.NebulaTheme

@Composable
fun ToolsScreen(
    viewModel: ToolsViewModel,
    modifier: Modifier = Modifier
) {
    val logs by viewModel.logs.collectAsState()
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
