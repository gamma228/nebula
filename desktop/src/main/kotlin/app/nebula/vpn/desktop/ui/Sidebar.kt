package app.nebula.vpn.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nebula.vpn.core.model.ConnectionState
import app.nebula.vpn.desktop.i18n.DesktopStrings
import app.nebula.vpn.desktop.state.DesktopAppState
import app.nebula.vpn.desktop.state.NavScreen

@Composable
fun Sidebar(state: DesktopAppState) {
    val connState by state.connectionState.collectAsState()
    val lang = state.currentLanguage

    Column(
        modifier = Modifier
            .width(220.dp)
            .fillMaxHeight()
            .background(
                if (state.wallpaperBitmap != null) MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
                else MaterialTheme.colorScheme.surface
            )
            .padding(vertical = 20.dp, horizontal = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            // App Branding Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = DesktopStrings.get("app_name", lang),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "v1.2.1 Desktop",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // Navigation Items
            NavItem(
                title = DesktopStrings.get("nav_home", lang),
                icon = Icons.Default.Security,
                isSelected = state.currentScreen == NavScreen.HOME,
                onClick = { state.currentScreen = NavScreen.HOME }
            )
            NavItem(
                title = DesktopStrings.get("nav_servers", lang),
                icon = Icons.Default.Dns,
                isSelected = state.currentScreen == NavScreen.SERVERS,
                onClick = { state.currentScreen = NavScreen.SERVERS }
            )
            NavItem(
                title = DesktopStrings.get("nav_routing", lang),
                icon = Icons.Default.AltRoute,
                isSelected = state.currentScreen == NavScreen.ROUTING,
                onClick = { state.currentScreen = NavScreen.ROUTING }
            )
            NavItem(
                title = DesktopStrings.get("nav_tools", lang),
                icon = Icons.Default.Build,
                isSelected = state.currentScreen == NavScreen.TOOLS,
                onClick = { state.currentScreen = NavScreen.TOOLS }
            )
            NavItem(
                title = DesktopStrings.get("nav_settings", lang),
                icon = Icons.Default.Settings,
                isSelected = state.currentScreen == NavScreen.SETTINGS,
                onClick = { state.currentScreen = NavScreen.SETTINGS }
            )
        }

        // Bottom Connection Status Pill
        val (statusText, statusColor) = when (connState) {
            is ConnectionState.Connected -> DesktopStrings.get("status_connected", lang) to Color(0xFF00D084)
            is ConnectionState.Connecting -> DesktopStrings.get("status_connecting", lang) to Color(0xFFFFB74D)
            is ConnectionState.Disconnecting -> DesktopStrings.get("status_disconnecting", lang) to Color(0xFFFFB74D)
            is ConnectionState.Failed -> DesktopStrings.get("status_failed", lang) to Color(0xFFFF5252)
            else -> DesktopStrings.get("status_disconnected", lang) to MaterialTheme.colorScheme.onSurfaceVariant
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = statusText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun NavItem(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent
    val contentColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
