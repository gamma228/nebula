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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nebula.vpn.core.model.ThemeMode
import app.nebula.vpn.desktop.i18n.AppLanguage
import app.nebula.vpn.desktop.i18n.DesktopStrings
import app.nebula.vpn.desktop.state.DesktopAppState
import app.nebula.vpn.desktop.theme.DesktopThemePreset
import app.nebula.vpn.desktop.theme.parseHexColor
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
fun SettingsScreen(state: DesktopAppState) {
    val lang = state.currentLanguage
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = DesktopStrings.get("nav_settings", lang),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        // 1. Appearance / Theme & Customization (Phone Parity)
        SettingsSection(title = DesktopStrings.get("settings_theme", lang)) {
            // Theme Mode: Dark / AMOLED / Light
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ThemeChip(DesktopStrings.get("theme_dark", lang), state.currentThemeMode == ThemeMode.DARK) {
                    state.currentThemeMode = ThemeMode.DARK
                    state.persistSettings()
                }
                ThemeChip(DesktopStrings.get("theme_oled", lang), state.currentThemeMode == ThemeMode.AMOLED) {
                    state.currentThemeMode = ThemeMode.AMOLED
                    state.persistSettings()
                }
                ThemeChip(DesktopStrings.get("theme_light", lang), state.currentThemeMode == ThemeMode.LIGHT) {
                    state.currentThemeMode = ThemeMode.LIGHT
                    state.persistSettings()
                }
            }

            Spacer(Modifier.height(14.dp))

            // Color Presets (6 stylish palettes + Custom)
            Text(DesktopStrings.get("theme_preset", lang), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                DesktopThemePreset.entries.forEach { preset ->
                    val isSelected = state.currentThemePreset == preset
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable {
                                state.setThemePreset(preset)
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(parseHexColor(preset.primaryHex))
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = DesktopStrings.get(preset.displayNameKey, lang),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Custom Accent Color input if CUSTOM preset selected
            if (state.currentThemePreset == DesktopThemePreset.CUSTOM) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(parseHexColor(state.customAccentHex))
                    )
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = state.customAccentHex,
                        onValueChange = { state.setCustomAccent(it) },
                        label = { Text(DesktopStrings.get("settings_custom_accent", lang)) },
                        modifier = Modifier.width(180.dp),
                        singleLine = true
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Custom Background Wallpaper (Image + Dimming)
            Text(DesktopStrings.get("settings_wallpaper", lang), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    val chooser = JFileChooser().apply {
                        dialogTitle = "Select Background Image"
                        fileFilter = FileNameExtensionFilter("Image files (*.png, *.jpg, *.jpeg, *.webp)", "png", "jpg", "jpeg", "webp")
                    }
                    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                        state.setWallpaper(chooser.selectedFile.absolutePath)
                    }
                }) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(DesktopStrings.get("settings_pick_wallpaper", lang), fontSize = 12.sp)
                }

                if (state.backgroundImagePath != null) {
                    OutlinedButton(
                        onClick = { state.setWallpaper(null) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(DesktopStrings.get("settings_clear_wallpaper", lang), fontSize = 12.sp)
                    }
                }
            }

            if (state.backgroundImagePath != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "${DesktopStrings.get("settings_wallpaper_dim", lang)}: ${(state.backgroundDimAlpha * 100).toInt()}%",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = state.backgroundDimAlpha,
                    onValueChange = { state.setWallpaperDim(it) },
                    valueRange = 0.05f..0.90f,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // 2. Anti-Censorship & Incy Anti-DPI Features (TSPU Bypass)
        SettingsSection(title = DesktopStrings.get("settings_anticensorship", lang)) {
            // Block QUIC (UDP 443)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(DesktopStrings.get("settings_block_quic", lang), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(DesktopStrings.get("settings_block_quic_sub", lang), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = state.blockQuic,
                    onCheckedChange = {
                        state.blockQuic = it
                        state.persistSettings()
                    }
                )
            }

            Spacer(Modifier.height(12.dp))

            // TLS ClientHello Fragmentation (Incy Anti-DPI)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(DesktopStrings.get("settings_fragment", lang), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(DesktopStrings.get("settings_fragment_sub", lang), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = state.fragmentEnabled,
                    onCheckedChange = {
                        state.fragmentEnabled = it
                        state.persistSettings()
                    }
                )
            }

            Spacer(Modifier.height(12.dp))

            // Anti-DPI SNI Spoofing Presets
            Column {
                Text(DesktopStrings.get("settings_sni_spoof", lang), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(DesktopStrings.get("settings_sni_spoof_sub", lang), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("rutube.ru", "lk.x5.ru", "gosuslugi.ru", "vk.com", "yandex.ru").forEach { host ->
                        ThemeChip(host, state.sniSpoofPreset == host) {
                            state.sniSpoofPreset = host
                            state.persistSettings()
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // FakeDNS Acceleration
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(DesktopStrings.get("settings_fakedns", lang), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(DesktopStrings.get("settings_fakedns_sub", lang), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = state.fakeDnsEnabled,
                    onCheckedChange = {
                        state.fakeDnsEnabled = it
                        state.persistSettings()
                    }
                )
            }

            Spacer(Modifier.height(12.dp))

            // Disable IPv6
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(DesktopStrings.get("settings_disable_ipv6", lang), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(DesktopStrings.get("settings_disable_ipv6_sub", lang), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = state.disableIpv6,
                    onCheckedChange = {
                        state.disableIpv6 = it
                        state.persistSettings()
                    }
                )
            }
        }

        // 3. Language Switcher
        SettingsSection(title = DesktopStrings.get("settings_language", lang)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (l in AppLanguage.entries) {
                    ThemeChip(l.displayName, state.currentLanguage == l) {
                        state.currentLanguage = l
                        state.persistSettings()
                    }
                }
            }
        }

        // 4. Connection Mode (System Proxy vs TUN)
        SettingsSection(title = DesktopStrings.get("settings_connection_mode", lang)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ThemeChip(DesktopStrings.get("mode_system_proxy", lang), state.connectionMode == "SYSTEM_PROXY") {
                    state.connectionMode = "SYSTEM_PROXY"
                    state.persistSettings()
                }
                ThemeChip(DesktopStrings.get("mode_tun", lang), state.connectionMode == "TUN") {
                    state.connectionMode = "TUN"
                    state.persistSettings()
                }
            }
        }

        // 5. Windows Integration (Autostart & Tray)
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
                    onCheckedChange = {
                        state.closeToTray = it
                        state.persistSettings()
                    }
                )
            }
        }

        // 6. Network & DNS
        SettingsSection(title = "Network & Security") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(DesktopStrings.get("settings_killswitch", lang), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("Block all traffic if VPN disconnects (strict_route in TUN)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = state.killSwitchEnabled,
                    onCheckedChange = {
                        state.killSwitchEnabled = it
                        state.persistSettings()
                    }
                )
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.remoteDnsUrl,
                onValueChange = {
                    state.remoteDnsUrl = it
                    state.persistSettings()
                },
                label = { Text(DesktopStrings.get("settings_remote_dns", lang)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = state.tunMtu.toString(),
                onValueChange = {
                    state.tunMtu = it.toIntOrNull() ?: 1400
                    state.persistSettings()
                },
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
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
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
