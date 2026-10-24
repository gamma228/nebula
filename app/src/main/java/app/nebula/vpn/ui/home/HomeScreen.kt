package app.nebula.vpn.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Upload
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nebula.vpn.R
import app.nebula.vpn.core.model.ConnectionState
import app.nebula.vpn.ui.theme.LocalThemeConfig
import app.nebula.vpn.ui.theme.StatusGreen
import app.nebula.vpn.ui.theme.StatusYellow

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToServers: () -> Unit,
    modifier: Modifier = Modifier
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val trafficStats by viewModel.trafficStats.collectAsState()
    val selectedProfile by viewModel.selectedProfile.collectAsState()
    val themeConfig = LocalThemeConfig.current

    val isConnected = connectionState is ConnectionState.Connected
    val isConnecting = connectionState is ConnectionState.Connecting

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isConnecting) 1.08f else if (isConnected) 1.03f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val buttonColor by animateColorAsState(
        targetValue = when {
            isConnected -> StatusGreen
            isConnecting -> StatusYellow
            else -> MaterialTheme.colorScheme.primary
        },
        label = "btnColor"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Status Top Badge
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(buttonColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = when (connectionState) {
                    is ConnectionState.Connected -> stringResource(R.string.state_connected)
                    is ConnectionState.Connecting -> stringResource(R.string.state_connecting)
                    is ConnectionState.Disconnecting -> stringResource(R.string.state_disconnecting)
                    is ConnectionState.Error -> (connectionState as ConnectionState.Error).message
                    else -> stringResource(R.string.state_disconnected)
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Center Giant Neon Connect Button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(240.dp)
        ) {
            // Neon Glow ring
            if (themeConfig.neonBordersEnabled && (isConnected || isConnecting)) {
                Box(
                    modifier = Modifier
                        .size(230.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(buttonColor.copy(alpha = 0.35f), Color.Transparent)
                            )
                        )
                )
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(170.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        width = if (themeConfig.neonBordersEnabled) 3.dp else 1.dp,
                        brush = Brush.linearGradient(
                            listOf(buttonColor, MaterialTheme.colorScheme.secondary)
                        ),
                        shape = CircleShape
                    )
                    .clickable { viewModel.toggleConnection() }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = "Power",
                        tint = buttonColor,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isConnected) stringResource(R.string.action_disconnect) else stringResource(R.string.action_connect),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Bottom Info: Speed Stats + Selected Server
        Column(modifier = Modifier.fillMaxWidth()) {
            // Speeds
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatCard(
                    icon = Icons.Default.Download,
                    title = stringResource(R.string.speed_download, formatSpeed(trafficStats.downloadSpeedBps)),
                    subtitle = stringResource(R.string.total_download, formatBytes(trafficStats.totalDownloadBytes)),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                StatCard(
                    icon = Icons.Default.Upload,
                    title = stringResource(R.string.speed_upload, formatSpeed(trafficStats.uploadSpeedBps)),
                    subtitle = stringResource(R.string.total_upload, formatBytes(trafficStats.totalUploadBytes)),
                    modifier = Modifier.weight(1f)
                )
            }

            // Server Selector Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToServers() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = selectedProfile?.name ?: stringResource(R.string.auto_select_fastest),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = selectedProfile?.let { "${it.protocol} • ${it.server}:${it.port}" }
                                    ?: "Lowest Latency Route",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.ArrowForwardIos,
                        contentDescription = "Select",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

private fun formatSpeed(bps: Long): String {
    val kbps = bps / 1024.0
    val mbps = kbps / 1024.0
    return if (mbps >= 1.0) {
        String.format("%.1f MB", mbps)
    } else {
        String.format("%.0f KB", kbps)
    }
}

private fun formatBytes(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format("%.2f GB", gb)
        mb >= 1.0 -> String.format("%.1f MB", mb)
        else -> String.format("%.0f KB", kb)
    }
}
