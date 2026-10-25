package app.nebula.vpn.ui.settings

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nebula.vpn.R
import app.nebula.vpn.core.model.AppRoutingMode
import app.nebula.vpn.ui.theme.EmeraldGreen
import app.nebula.vpn.ui.theme.MintTeal
import app.nebula.vpn.ui.theme.StatusRed
import app.nebula.vpn.ui.theme.StatusYellow

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateToRouting: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToDoctor: () -> Unit,
    onNavigateToTools: () -> Unit,
    modifier: Modifier = Modifier
) {
    val killSwitch by viewModel.killSwitchEnabled.collectAsState()
    val fakeDns by viewModel.fakeDnsEnabled.collectAsState()
    val bypassLan by viewModel.bypassLanEnabled.collectAsState()
    val bootAutostart by viewModel.bootAutostartEnabled.collectAsState()
    val remoteDns by viewModel.remoteDnsUrl.collectAsState()
    val routingMode by viewModel.routingMode.collectAsState()
    val activeTheme by viewModel.activeTheme.collectAsState()
    val biometricLock by viewModel.biometricLockEnabled.collectAsState()
    val flagSecure by viewModel.flagSecureEnabled.collectAsState()
    val currentLang by viewModel.currentLanguageCode.collectAsState()
    val pingMode by viewModel.pingMode.collectAsState()

    var showPanicDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showConnectionDialog by remember { mutableStateOf(false) }
    var showPingModeDialog by remember { mutableStateOf(false) }
    var showTunnelDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    // Panic Wipe Confirmation
    if (showPanicDialog) {
        AlertDialog(
            onDismissRequest = { showPanicDialog = false },
            title = { Text("Экстренное удаление (Panic Wipe)") },
            text = { Text("Все сохранённые профили, подписки, ключи и базы данных будут немедленно и безвозвратно уничтожены.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.panicWipe()
                        showPanicDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRed)
                ) {
                    Text("Уничтожить всё")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPanicDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    // Language Selector Dialog
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.language_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val langs = listOf(
                        "Русский" to "ru",
                        "English (Default)" to "en",
                        "Українська" to "uk",
                        "简体中文" to "zh-CN",
                        "Deutsch" to "de"
                    )
                    langs.forEach { (name, code) ->
                        LanguageOption(
                            name = name,
                            code = code,
                            selectedCode = currentLang
                        ) {
                            viewModel.changeLanguage(code)
                            showLanguageDialog = false
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    // Connection Settings Dialog
    if (showConnectionDialog) {
        AlertDialog(
            onDismissRequest = { showConnectionDialog = false },
            title = { Text("Настройки соединения") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Автоподключение при старте", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Подключаться к VPN при запуске системы", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                        Switch(
                            checked = bootAutostart,
                            onCheckedChange = { viewModel.setBootAutostart(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = EmeraldGreen, checkedTrackColor = EmeraldGreen.copy(alpha = 0.5f))
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Обход локальной сети (LAN)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Прямой доступ к устройствам в домашней сети", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                        Switch(
                            checked = bypassLan,
                            onCheckedChange = { viewModel.setBypassLan(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = EmeraldGreen, checkedTrackColor = EmeraldGreen.copy(alpha = 0.5f))
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Kill Switch (Блокировка утечек)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Блокировать весь трафик при обрыве туннеля", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                        Switch(
                            checked = killSwitch,
                            onCheckedChange = { viewModel.setKillSwitch(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = EmeraldGreen, checkedTrackColor = EmeraldGreen.copy(alpha = 0.5f))
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showConnectionDialog = false }) {
                    Text("Готово")
                }
            }
        )
    }

    // Ping Check Mode Dialog
    if (showPingModeDialog) {
        AlertDialog(
            onDismissRequest = { showPingModeDialog = false },
            title = { Text("Метод проверки задержки (пинга)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (pingMode == "TCP") EmeraldGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setPingMode("TCP")
                                showPingModeDialog = false
                            }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("⚡ TCP Handshake (Рекомендуется)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Замер времени открытия TCP-сокета до порта узла. Быстрый отклик, нулевой расход трафика.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (pingMode == "HTTP") EmeraldGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setPingMode("HTTP")
                                showPingModeDialog = false
                            }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("🌐 HTTP URL Test (204)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Реальный веб-запрос Cloudflare 204. Проверяет сквозную доступность интернета.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPingModeDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    // Tunnel & DNS Settings Dialog
    if (showTunnelDialog) {
        var tempDns by remember { mutableStateOf(remoteDns) }
        AlertDialog(
            onDismissRequest = { showTunnelDialog = false },
            title = { Text("Туннель и DNS") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Fake-DNS (198.18.0.0/15)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Ускорение соединений и защита от DNS poisoning", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                        Switch(
                            checked = fakeDns,
                            onCheckedChange = { viewModel.setFakeDns(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = EmeraldGreen, checkedTrackColor = EmeraldGreen.copy(alpha = 0.5f))
                        )
                    }

                    Column {
                        Text("Удалённый DNS (DoH/DoT)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = tempDns,
                            onValueChange = { tempDns = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setRemoteDns(tempDns)
                        showTunnelDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTunnelDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    // About Dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("О приложении Nebula") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Nebula VPN Client v1.0.0", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Ядро прокси: SagerNet sing-box v1.14.4 (GPL-3.0)", fontSize = 13.sp)
                    Text("Поддерживаемые протоколы: VLESS, VMess, Trojan, Shadowsocks, Hysteria 2, WireGuard.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Полная приватность: 0 трекеров, 0 аналитики, 0 Google Play Services, 100% открытый исходный код.", fontSize = 12.sp, color = EmeraldGreen)
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("Закрыть")
                }
            }
        )
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header
        Text(
            text = stringResource(R.string.settings_title),
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
        )

        // SECTION: ПРОКСИ ПО ПРИЛОЖЕНИЯМ
        SettingsSectionHeader(stringResource(R.string.routing_title).uppercase())
        SettingNavigationCard(
            icon = Icons.Default.Apps,
            iconTint = Color(0xFFFFB703),
            iconBg = Color(0xFFFFB703).copy(alpha = 0.15f),
            title = stringResource(R.string.routing_apps),
            value = when (routingMode) {
                AppRoutingMode.GLOBAL -> stringResource(R.string.mode_global)
                AppRoutingMode.BYPASS_APPS -> stringResource(R.string.mode_bypass)
                AppRoutingMode.FORWARD_APPS -> stringResource(R.string.mode_forward)
            },
            onClick = onNavigateToRouting
        )

        // SECTION: ОФОРМЛЕНИЕ
        SettingsSectionHeader(stringResource(R.string.appearance_title).uppercase())
        SettingNavigationCard(
            icon = Icons.Default.Palette,
            iconTint = EmeraldGreen,
            iconBg = EmeraldGreen.copy(alpha = 0.15f),
            title = stringResource(R.string.appearance_title),
            value = when (activeTheme.presetId) {
                "emerald_cyber" -> "Cyber Emerald"
                "oled_crimson" -> stringResource(R.string.preset_oled_crimson)
                "cyber_violet" -> stringResource(R.string.preset_cyber_violet)
                "monochrome" -> stringResource(R.string.preset_monochrome)
                else -> stringResource(R.string.custom_theme)
            },
            onClick = onNavigateToAppearance
        )

        // SECTION: СОЕДИНЕНИЕ
        SettingsSectionHeader(stringResource(R.string.vpn_permission_check).uppercase())
        SettingNavigationCard(
            icon = Icons.Default.FlashOn,
            iconTint = Color(0xFFFFB703),
            iconBg = Color(0xFFFFB703).copy(alpha = 0.15f),
            title = stringResource(R.string.kill_switch),
            value = if (killSwitch) "ON" else "OFF",
            onClick = { showConnectionDialog = true }
        )
        SettingNavigationCard(
            icon = Icons.Default.Speed,
            iconTint = EmeraldGreen,
            iconBg = EmeraldGreen.copy(alpha = 0.15f),
            title = "Ping Method",
            value = if (pingMode == "HTTP") "HTTP (URL 204)" else "TCP Handshake",
            onClick = { showPingModeDialog = true }
        )

        // SECTION: ТУННЕЛЬ
        SettingsSectionHeader(stringResource(R.string.dns_title).uppercase())
        SettingNavigationCard(
            icon = Icons.Default.Dns,
            iconTint = MintTeal,
            iconBg = MintTeal.copy(alpha = 0.15f),
            title = stringResource(R.string.dns_title),
            value = if (fakeDns) "Fake-DNS" else "Standard",
            onClick = { showTunnelDialog = true }
        )

        // SECTION: ДИАГНОСТИКА И ЛОГИ
        SettingsSectionHeader(stringResource(R.string.logs_title).uppercase())
        SettingNavigationCard(
            icon = Icons.Default.MedicalServices,
            iconTint = EmeraldGreen,
            iconBg = EmeraldGreen.copy(alpha = 0.15f),
            title = stringResource(R.string.doctor_title),
            value = stringResource(R.string.fix_issue),
            onClick = onNavigateToDoctor
        )
        SettingNavigationCard(
            icon = Icons.Default.Build,
            iconTint = Color(0xFF3B82F6),
            iconBg = Color(0xFF3B82F6).copy(alpha = 0.15f),
            title = stringResource(R.string.logs_title),
            value = "Logs",
            onClick = onNavigateToTools
        )

        // SECTION: БЕЗОПАСНОСТЬ
        SettingsSectionHeader(stringResource(R.string.security_title).uppercase())
        SettingSwitchCard(
            icon = Icons.Default.Fingerprint,
            iconTint = Color(0xFFA855F7),
            iconBg = Color(0xFFA855F7).copy(alpha = 0.15f),
            title = stringResource(R.string.biometric_lock),
            subtitle = "Biometric / PIN",
            checked = biometricLock,
            onCheckedChange = { viewModel.setBiometricLock(it) }
        )
        SettingSwitchCard(
            icon = Icons.Default.Security,
            iconTint = Color(0xFF3B82F6),
            iconBg = Color(0xFF3B82F6).copy(alpha = 0.15f),
            title = stringResource(R.string.flag_secure),
            subtitle = "FLAG_SECURE",
            checked = flagSecure,
            onCheckedChange = { viewModel.setFlagSecure(it) }
        )
        SettingClickableCard(
            icon = Icons.Default.DeleteForever,
            iconTint = StatusRed,
            iconBg = StatusRed.copy(alpha = 0.15f),
            title = stringResource(R.string.panic_wipe),
            subtitle = stringResource(R.string.panic_wipe_confirm),
            onClick = { showPanicDialog = true }
        )

        // SECTION: ПРИЛОЖЕНИЕ
        SettingsSectionHeader(stringResource(R.string.language_title).uppercase())
        SettingNavigationCard(
            icon = Icons.Default.Language,
            iconTint = Color(0xFF8B5CF6),
            iconBg = Color(0xFF8B5CF6).copy(alpha = 0.15f),
            title = stringResource(R.string.language_title),
            value = when (currentLang) {
                "ru" -> "Русский"
                "uk" -> "Українська"
                "zh-CN" -> "简体中文"
                "de" -> "Deutsch"
                else -> "English"
            },
            onClick = { showLanguageDialog = true }
        )
        SettingNavigationCard(
            icon = Icons.Default.Info,
            iconTint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            iconBg = MaterialTheme.colorScheme.surfaceVariant,
            title = stringResource(R.string.about_title),
            value = "v1.0.0",
            onClick = { showAboutDialog = true }
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SettingsSectionHeader(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        modifier = Modifier.padding(start = 4.dp, top = 6.dp)
    )
}

@Composable
private fun SettingNavigationCard(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(
                    text = value,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.ArrowForwardIos,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
private fun SettingSwitchCard(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = EmeraldGreen,
                    checkedTrackColor = EmeraldGreen.copy(alpha = 0.5f)
                )
            )
        }
    }
}

@Composable
private fun SettingClickableCard(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = iconTint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun LanguageOption(
    name: String,
    code: String,
    selectedCode: String,
    onSelect: () -> Unit
) {
    val isSelected = code == selectedCode || (code == "zh-CN" && selectedCode == "zh") || (code == "en" && selectedCode.isEmpty())
    val bg = if (isSelected) EmeraldGreen.copy(alpha = 0.12f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable { onSelect() }
            .padding(vertical = 12.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = name,
            fontSize = 15.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) EmeraldGreen else MaterialTheme.colorScheme.onSurface
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = EmeraldGreen,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
