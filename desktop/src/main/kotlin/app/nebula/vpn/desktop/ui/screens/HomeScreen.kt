package app.nebula.vpn.desktop.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nebula.vpn.core.model.ConnectionState
import app.nebula.vpn.desktop.i18n.DesktopStrings
import app.nebula.vpn.desktop.state.DesktopAppState
import app.nebula.vpn.desktop.state.NavScreen
import app.nebula.vpn.desktop.ui.components.TrafficChart

@Composable
fun HomeScreen(state: DesktopAppState) {
    val connState by state.connectionState.collectAsState()
    val stats by state.trafficStats.collectAsState()
    val selectedProfile by state.selectedProfile.collectAsState()
    val lang = state.currentLanguage
    val scrollState = rememberScrollState()

    val isConnected = connState.isConnected
    val isConnecting = connState is ConnectionState.Connecting

    // Pulsing animation for connecting / connected glow
    val infiniteTransition = rememberInfiniteTransition()
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isConnected || isConnecting) 1.08f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400),
            repeatMode = RepeatMode.Reverse
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header: App Title & Connection Mode Switcher
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = DesktopStrings.get("app_name", lang),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = if (state.connectionMode == "TUN") {
                        DesktopStrings.get("mode_tun", lang)
                    } else {
                        DesktopStrings.get("mode_system_proxy", lang)
                    },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Connection Mode Switcher Pill
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(4.dp)
            ) {
                ModeToggleChip(
                    title = "Proxy",
                    isSelected = state.connectionMode == "SYSTEM_PROXY",
                    onClick = {
                        state.connectionMode = "SYSTEM_PROXY"
                        state.persistSettings()
                    }
                )
                Spacer(Modifier.width(4.dp))
                ModeToggleChip(
                    title = "TUN",
                    isSelected = state.connectionMode == "TUN",
                    onClick = {
                        state.connectionMode = "TUN"
                        state.persistSettings()
                    }
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // Center: Giant Connect Button with Glow Effect
        Box(contentAlignment = Alignment.Center) {
            // Glow layer
            if (isConnected || isConnecting) {
                Box(
                    modifier = Modifier
                        .size(170.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }

            val buttonColor = if (isConnected) {
                MaterialTheme.colorScheme.primary
            } else if (isConnecting) {
                Color(0xFFFFB74D)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }

            Box(
                modifier = Modifier
                    .size(130.dp)
                    .clip(CircleShape)
                    .background(buttonColor)
                    .clickable { state.toggleConnect() },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.PowerSettingsNew,
                        contentDescription = null,
                        tint = if (isConnected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(42.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (isConnected) {
                            DesktopStrings.get("btn_disconnect", lang)
                        } else if (isConnecting) {
                            "..."
                        } else {
                            DesktopStrings.get("btn_connect", lang)
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isConnected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Active Server Card
        Card(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Dns,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Column {
                        Text(
                            text = selectedProfile?.name ?: DesktopStrings.get("no_server_selected", lang),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (selectedProfile != null) {
                                "${selectedProfile!!.protocol.name} • ${selectedProfile!!.server}:${selectedProfile!!.port}"
                            } else {
                                DesktopStrings.get("select_server", lang)
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = { state.currentScreen = NavScreen.SERVERS },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        text = "Change",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Real-Time Speed Chart (Incy / Clash style)
        TrafficChart(
            samples = state.speedHistory,
            currentDownBps = stats.downloadSpeedBps,
            currentUpBps = stats.uploadSpeedBps
        )

        // Traffic Metrics Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TrafficCard(
                modifier = Modifier.weight(1f),
                title = DesktopStrings.get("speed_down", lang),
                value = formatSpeed(stats.downloadSpeedBps),
                icon = Icons.Default.ArrowDownward,
                iconTint = MaterialTheme.colorScheme.primary
            )
            TrafficCard(
                modifier = Modifier.weight(1f),
                title = DesktopStrings.get("speed_up", lang),
                value = formatSpeed(stats.uploadSpeedBps),
                icon = Icons.Default.ArrowUpward,
                iconTint = MaterialTheme.colorScheme.secondary
            )
            TrafficCard(
                modifier = Modifier.weight(1f),
                title = DesktopStrings.get("total_down", lang),
                value = formatBytes(stats.totalDownloadBytes),
                icon = Icons.Default.ArrowDownward,
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TrafficCard(
                modifier = Modifier.weight(1f),
                title = DesktopStrings.get("total_up", lang),
                value = formatBytes(stats.totalUploadBytes),
                icon = Icons.Default.ArrowUpward,
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ModeToggleChip(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )
    }
}

@Composable
private fun TrafficCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color
) {
    Card(
        modifier = modifier.clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = value,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

private fun formatSpeed(bytesPerSec: Long): String {
    return when {
        bytesPerSec >= 1024 * 1024 -> String.format("%.1f MB/s", bytesPerSec / (1024.0 * 1024.0))
        bytesPerSec >= 1024 -> String.format("%.1f KB/s", bytesPerSec / 1024.0)
        else -> "$bytesPerSec B/s"
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1024L * 1024L * 1024L -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024L -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> String.format("%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
