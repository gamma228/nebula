package app.nebula.vpn.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import app.nebula.vpn.core.model.ConnectionState

@Composable
fun WindowFrameHeader(
    windowState: WindowState,
    connectionState: ConnectionState,
    isMiniMode: Boolean,
    onToggleMiniMode: () -> Unit,
    onMinimize: () -> Unit,
    onMaximize: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Icon + Title + Status Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )

                Text(
                    text = "Nebula VPN",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Status Indicator Dot / Pill
                val (statusColor, statusText) = when (connectionState) {
                    is ConnectionState.Connected -> Color(0xFF00D084) to "ONLINE"
                    is ConnectionState.Connecting -> Color(0xFFF59E0B) to "CONNECTING"
                    is ConnectionState.Disconnecting -> Color(0xFFEF4444) to "STOPPING"
                    else -> Color(0xFF64748B) to "READY"
                }

                Row(
                    modifier = Modifier
                        .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(modifier = Modifier.size(6.dp).background(statusColor, CircleShape))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
            }

            // Draggable Spacer in Center
            Spacer(modifier = Modifier.weight(1f).fillMaxHeight())

            // Right: Window Actions (MiniMode, Minimize, Maximize/Restore, Close)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Mini Mode Toggle
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clickable { onToggleMiniMode() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isMiniMode) Icons.Default.OpenInFull else Icons.Default.CloseFullscreen,
                        contentDescription = "Mini Mode",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }

                // Minimize
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clickable { onMinimize() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Minimize,
                        contentDescription = "Minimize",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }

                // Maximize / Restore
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clickable { onMaximize() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (windowState.placement == WindowPlacement.Maximized) Icons.Default.FullscreenExit else Icons.Default.CropSquare,
                        contentDescription = "Maximize",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }

                // Close
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
