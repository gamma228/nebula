package app.nebula.vpn.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.nebula.vpn.ui.theme.NebulaTheme
import java.util.Locale

@Composable
fun NebulaStatusHeader(
    downloadBps: Long,
    uploadBps: Long,
    durationSeconds: Long,
    pingMs: Long?,
    modifier: Modifier = Modifier
) {
    val colors = NebulaTheme.colors
    val shapes = NebulaTheme.shapes
    val spacing = NebulaTheme.spacing
    val typography = NebulaTheme.typography

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shapes.card)
            .background(colors.surface)
            .border(1.dp, colors.borderSubtle, shapes.card)
            .padding(horizontal = spacing.md, vertical = spacing.sm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Download Speed Meter
            TelemetryItem(
                icon = Icons.Filled.ArrowDownward,
                iconTint = colors.statusOk,
                label = "DOWN",
                value = formatSpeed(downloadBps)
            )

            // Divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(28.dp)
                    .background(colors.borderSubtle)
            )

            // Upload Speed Meter
            TelemetryItem(
                icon = Icons.Filled.ArrowUpward,
                iconTint = colors.primary,
                label = "UP",
                value = formatSpeed(uploadBps)
            )

            // Divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(28.dp)
                    .background(colors.borderSubtle)
            )

            // Session Duration
            TelemetryItem(
                icon = Icons.Filled.Timer,
                iconTint = colors.accent,
                label = "TIME",
                value = formatDuration(durationSeconds)
            )

            // Ping Badge if connected
            if (pingMs != null && pingMs > 0) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(28.dp)
                        .background(colors.borderSubtle)
                )

                val pingColor = when {
                    pingMs < 100 -> colors.statusOk
                    pingMs < 300 -> colors.statusWarning
                    else -> colors.statusError
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "PING",
                        style = typography.labelSmall,
                        color = colors.textMuted
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${pingMs}ms",
                        style = typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = pingColor
                    )
                }
            }
        }
    }
}

@Composable
private fun TelemetryItem(
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    label: String,
    value: String
) {
    val colors = NebulaTheme.colors
    val typography = NebulaTheme.typography

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(
                text = label,
                style = typography.labelSmall,
                color = colors.textMuted
            )
            Text(
                text = value,
                style = typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary
            )
        }
    }
}

private fun formatSpeed(bytesPerSec: Long): String {
    if (bytesPerSec <= 0) return "0 KB/s"
    val kb = bytesPerSec / 1024.0
    if (kb < 1000) {
        return String.format(Locale.US, "%.1f KB/s", kb)
    }
    val mb = kb / 1024.0
    return String.format(Locale.US, "%.1f MB/s", mb)
}

private fun formatDuration(seconds: Long): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hrs > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format(Locale.US, "%02d:%02d", mins, secs)
    }
}

@Preview(name = "Status Header Preview", showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun PreviewNebulaStatusHeader() {
    NebulaTheme {
        NebulaStatusHeader(
            downloadBps = 14_500_000L,
            uploadBps = 1_850_000L,
            durationSeconds = 3725L,
            pingMs = 42L
        )
    }
}
