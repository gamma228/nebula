package app.nebula.vpn.desktop.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nebula.vpn.core.model.ThemeMode
import app.nebula.vpn.desktop.i18n.AppLanguage
import app.nebula.vpn.desktop.i18n.DesktopStrings
import app.nebula.vpn.desktop.state.DesktopAppState

@Composable
fun SettingsScreen(state: DesktopAppState) {
    val lang = state.currentLanguage
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            text = DesktopStrings.get("nav_settings", lang),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        // 1. Appearance / Theme
        SettingsSection(title = DesktopStrings.get("settings_theme", lang)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ThemeChip("Dark", state.currentThemeMode == ThemeMode.DARK) {
                    state.currentThemeMode = ThemeMode.DARK
                }
                ThemeChip("OLED Black", state.currentThemeMode == ThemeMode.AMOLED) {
                    state.currentThemeMode = ThemeMode.AMOLED
                }
                ThemeChip("Light", state.currentThemeMode == ThemeMode.LIGHT) {
                    state.currentThemeMode = ThemeMode.LIGHT
                }
            }
        }

        // 2. Language Switcher
        SettingsSection(title = DesktopStrings.get("settings_language", lang)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (l in AppLanguage.entries) {
                    ThemeChip(l.displayName, state.currentLanguage == l) {
                        state.currentLanguage = l
                    }
                }
            }
        }

        // 3. Connection Mode (System Proxy vs TUN)
        SettingsSection(title = DesktopStrings.get("settings_connection_mode", lang)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ThemeChip(DesktopStrings.get("mode_system_proxy", lang), state.connectionMode == "SYSTEM_PROXY") {
                    state.connectionMode = "SYSTEM_PROXY"
                }
                ThemeChip(DesktopStrings.get("mode_tun", lang), state.connectionMode == "TUN") {
                    state.connectionMode = "TUN"
                }
            }
        }

        // 4. Windows Integration (Autostart & Tray)
        SettingsSection(title = "Windows Integration") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(DesktopStrings.get("settings_autostart", lang), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("Start Nebula automatically on user login", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = state.autostartEnabled,
                    onCheckedChange = { state.updateAutostart(it, state.autostartMinimized) }
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(DesktopStrings.get("settings_close_to_tray", lang), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("Closing the window minimizes to notification tray", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = state.closeToTray,
                    onCheckedChange = { state.closeToTray = it }
                )
            }
        }

        // 5. Network & DNS
        SettingsSection(title = "Network & Security") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(DesktopStrings.get("settings_killswitch", lang), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("Block all traffic if VPN disconnects (strict_route)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = state.killSwitchEnabled,
                    onCheckedChange = { state.killSwitchEnabled = it }
                )
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.remoteDnsUrl,
                onValueChange = { state.remoteDnsUrl = it },
                label = { Text(DesktopStrings.get("settings_remote_dns", lang)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = state.tunMtu.toString(),
                onValueChange = { state.tunMtu = it.toIntOrNull() ?: 1400 },
                label = { Text(DesktopStrings.get("settings_mtu", lang)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun ThemeChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val bg = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textColor)
    }
}
