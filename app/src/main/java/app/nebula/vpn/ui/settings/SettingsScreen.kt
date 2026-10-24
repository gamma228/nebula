package app.nebula.vpn.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nebula.vpn.R
import app.nebula.vpn.ui.theme.StatusRed

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val killSwitch by viewModel.killSwitchEnabled.collectAsState()
    val fakeDns by viewModel.fakeDnsEnabled.collectAsState()
    val biometricLock by viewModel.biometricLockEnabled.collectAsState()
    val flagSecure by viewModel.flagSecureEnabled.collectAsState()
    val currentLang by viewModel.currentLanguageCode.collectAsState()

    var showPanicDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    if (showPanicDialog) {
        AlertDialog(
            onDismissRequest = { showPanicDialog = false },
            title = { Text(stringResource(R.string.panic_wipe)) },
            text = { Text(stringResource(R.string.panic_wipe_confirm)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.panicWipe()
                        showPanicDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRed)
                ) {
                    Text("Wipe Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPanicDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.language_title)) },
            text = {
                Column {
                    LanguageOption("English (Default)", "en", currentLang) {
                        viewModel.changeLanguage("en")
                        showLanguageDialog = false
                    }
                    LanguageOption("Русский", "ru", currentLang) {
                        viewModel.changeLanguage("ru")
                        showLanguageDialog = false
                    }
                    LanguageOption("Українська", "uk", currentLang) {
                        viewModel.changeLanguage("uk")
                        showLanguageDialog = false
                    }
                    LanguageOption("简体中文", "zh-CN", currentLang) {
                        viewModel.changeLanguage("zh-CN")
                        showLanguageDialog = false
                    }
                    LanguageOption("Deutsch", "de", currentLang) {
                        viewModel.changeLanguage("de")
                        showLanguageDialog = false
                    }
                }
            },
            confirmButton = {}
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.settings_title),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Language Selector
        item {
            SettingClickableCard(
                icon = Icons.Default.Language,
                title = stringResource(R.string.language_title),
                subtitle = when (currentLang) {
                    "ru" -> "Русский"
                    "uk" -> "Українська"
                    "zh-CN" -> "简体中文"
                    "de" -> "Deutsch"
                    else -> "English"
                },
                onClick = { showLanguageDialog = true }
            )
        }

        // Kill Switch
        item {
            SettingSwitchCard(
                icon = Icons.Default.Shield,
                title = stringResource(R.string.kill_switch),
                subtitle = stringResource(R.string.kill_switch_desc),
                checked = killSwitch,
                onCheckedChange = { viewModel.setKillSwitch(it) }
            )
        }

        // Fake-DNS
        item {
            SettingSwitchCard(
                icon = Icons.Default.GppGood,
                title = "Fake-DNS (198.18.0.0/15)",
                subtitle = "Prevent DNS poisoning & speed up initial connection",
                checked = fakeDns,
                onCheckedChange = { viewModel.setFakeDns(it) }
            )
        }

        // Biometric Lock
        item {
            SettingSwitchCard(
                icon = Icons.Default.Fingerprint,
                title = stringResource(R.string.biometric_lock),
                subtitle = "Require biometric verification on app resume",
                checked = biometricLock,
                onCheckedChange = { viewModel.setBiometricLock(it) }
            )
        }

        // FLAG_SECURE
        item {
            SettingSwitchCard(
                icon = Icons.Default.Security,
                title = "FLAG_SECURE",
                subtitle = stringResource(R.string.flag_secure),
                checked = flagSecure,
                onCheckedChange = { viewModel.setFlagSecure(it) }
            )
        }

        // Panic Wipe
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showPanicDialog = true }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = null,
                        tint = StatusRed
                    )
                    Spacer(modifier = Modifier.padding(horizontal = 6.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.panic_wipe),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = StatusRed
                        )
                        Text(
                            text = "Immediately destroy all profiles and cryptographic keys",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }

        // About & License
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.about_title),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.gpl_license_notice),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.padding(horizontal = 6.dp))
                Column {
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
private fun SettingClickableCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.padding(horizontal = 6.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun LanguageOption(name: String, code: String, currentCode: String, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = name,
            fontSize = 16.sp,
            color = if (code == currentCode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (code == currentCode) FontWeight.Bold else FontWeight.Normal
        )
    }
}
