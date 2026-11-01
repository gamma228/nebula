package app.nebula.vpn.desktop.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nebula.vpn.core.model.ProxyProfile
import app.nebula.vpn.desktop.i18n.DesktopStrings
import app.nebula.vpn.desktop.state.DesktopAppState
import app.nebula.vpn.desktop.ui.components.QrCodeDialog
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@Composable
fun ServersScreen(state: DesktopAppState) {
    val profiles by state.profiles.collectAsState()
    val selectedProfile by state.selectedProfile.collectAsState()
    val lang = state.currentLanguage

    var searchQuery by remember { mutableStateOf("") }
    var showImportDialog by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf<String?>(null) }
    var isImporting by remember { mutableStateOf(false) }
    var qrProfileToShow by remember { mutableStateOf<ProxyProfile?>(null) }
    var copyNotification by remember { mutableStateOf<String?>(null) }

    val filteredProfiles = profiles.filter {
        it.name.contains(searchQuery, ignoreCase = true) ||
        it.server.contains(searchQuery, ignoreCase = true) ||
        it.protocol.name.contains(searchQuery, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        // Header & Action Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "${DesktopStrings.get("nav_servers", lang)} (${profiles.size})",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (copyNotification != null) {
                    Text(
                        text = copyNotification!!,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Ping All Button
                Button(
                    onClick = { state.testAllPings() },
                    enabled = !state.isPingTestingAll
                ) {
                    if (state.isPingTestingAll) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(DesktopStrings.get("testing_ping", lang), fontSize = 13.sp)
                    } else {
                        Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(DesktopStrings.get("test_all_ping", lang), fontSize = 13.sp)
                    }
                }

                // Import Button
                Button(onClick = { showImportDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(DesktopStrings.get("add_profile", lang), fontSize = 13.sp)
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Search Input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(DesktopStrings.get("search_servers", lang)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(14.dp))

        // Server Cards List
        if (filteredProfiles.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = DesktopStrings.get("no_server_selected", lang),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(filteredProfiles, key = { it.id }) { profile ->
                    ServerListItem(
                        profile = profile,
                        isSelected = profile.id == selectedProfile?.id,
                        onSelect = { state.selectProfile(profile) },
                        onDelete = { state.deleteProfile(profile.id) },
                        onShowQr = { qrProfileToShow = profile },
                        onCopyLink = {
                            val link = profile.toShareLink()
                            Toolkit.getDefaultToolkit().systemClipboard.setContents(
                                StringSelection(link),
                                null
                            )
                            copyNotification = DesktopStrings.get("copied_to_clipboard", lang)
                        }
                    )
                }
            }
        }
    }

    // QR Code Dialog
    if (qrProfileToShow != null) {
        QrCodeDialog(
            profile = qrProfileToShow!!,
            language = lang,
            onDismiss = { qrProfileToShow = null }
        )
    }

    // Import Dialog
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { if (!isImporting) showImportDialog = false },
            title = { Text(DesktopStrings.get("add_profile", lang)) },
            text = {
                Column {
                    Text(
                        DesktopStrings.get("import_hint", lang),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = importText,
                        onValueChange = {
                            importText = it
                            importError = null
                        },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        placeholder = { Text("vless://... or https://...") }
                    )
                    if (importError != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = importError!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (importText.isNotBlank()) {
                            isImporting = true
                            state.importLinkOrSubscription(importText) { count, err ->
                                isImporting = false
                                if (err == null && count > 0) {
                                    showImportDialog = false
                                    importText = ""
                                } else {
                                    importError = err ?: "Failed to import"
                                }
                            }
                        }
                    },
                    enabled = !isImporting && importText.isNotBlank()
                ) {
                    if (isImporting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text(DesktopStrings.get("btn_import", lang))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showImportDialog = false },
                    enabled = !isImporting
                ) {
                    Text(DesktopStrings.get("btn_cancel", lang))
                }
            }
        )
    }
}

@Composable
private fun ServerListItem(
    profile: ProxyProfile,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onShowQr: () -> Unit,
    onCopyLink: () -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
            }
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                if (isSelected) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                }

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = profile.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = profile.protocol.name,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "${profile.server}:${profile.port}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Latency, QR, Copy Link & Delete Actions
            Row(verticalAlignment = Alignment.CenterVertically) {
                val ping = profile.lastPingMs
                val (pingText, pingColor) = when {
                    ping == null || ping <= 0L -> "N/A" to MaterialTheme.colorScheme.onSurfaceVariant
                    ping < 120 -> "${ping} ms" to Color(0xFF00D084)
                    ping < 280 -> "${ping} ms" to Color(0xFFFFB74D)
                    else -> "${ping} ms" to Color(0xFFFF5252)
                }

                Text(
                    text = pingText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = pingColor,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )

                IconButton(onClick = onShowQr) {
                    Icon(
                        Icons.Default.QrCode,
                        contentDescription = "QR Code",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(19.dp)
                    )
                }

                IconButton(onClick = onCopyLink) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy Link",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
