package app.nebula.vpn.desktop.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nebula.vpn.core.windows.SingBoxBinaryVerifier
import app.nebula.vpn.desktop.i18n.DesktopStrings
import app.nebula.vpn.desktop.state.ActiveConnection
import app.nebula.vpn.desktop.state.DesktopAppState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

data class DoctorItem(
    val title: String,
    val details: String,
    val isSuccess: Boolean,
    val isPending: Boolean = false
)

@Composable
fun ToolsScreen(state: DesktopAppState) {
    val lang = state.currentLanguage
    val connState by state.connectionState.collectAsState()
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(0) }
    var isRunningDoctor by remember { mutableStateOf(false) }
    var doctorReportStatus by remember { mutableStateOf<String?>(null) }
    val doctorResults = remember { mutableStateListOf<DoctorItem>() }

    var logSearch by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        // Tab Row: Doctor, Active Connections, Core Logs
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clip(RoundedCornerShape(12.dp))
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(DesktopStrings.get("doctor_title", lang), fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = {
                    selectedTab = 1
                    state.refreshConnections()
                },
                text = { Text(DesktopStrings.get("connections_title", lang), fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text(DesktopStrings.get("logs_title", lang), fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
            )
        }

        Spacer(Modifier.height(16.dp))

        when (selectedTab) {
            0 -> {
                // Connection Doctor Tab
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            isRunningDoctor = true
                            doctorReportStatus = null
                            doctorResults.clear()
                            scope.launch {
                                runDoctorDiagnostics(state, doctorResults)
                                isRunningDoctor = false
                            }
                        },
                        enabled = !isRunningDoctor
                    ) {
                        if (isRunningDoctor) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Testing...")
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(DesktopStrings.get("doctor_run_tests", lang))
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            val report = buildDoctorReport(state, doctorResults)
                            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(report), null)
                            doctorReportStatus = DesktopStrings.get("doctor_report_copied", lang)
                        },
                        enabled = doctorResults.isNotEmpty()
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(DesktopStrings.get("doctor_copy_report", lang))
                    }
                }

                if (doctorReportStatus != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(doctorReportStatus!!, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                }

                Spacer(Modifier.height(16.dp))

                if (doctorResults.isEmpty() && !isRunningDoctor) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Click 'Run Diagnostics' to test binary integrity, driver, proxy state, and network reachability",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(doctorResults) { item ->
                            DoctorItemCard(item)
                        }
                    }
                }
            }

            1 -> {
                // Active Connections Tab (Incy / Clash style)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${DesktopStrings.get("connections_count", lang)}: ${state.activeConnections.size}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        IconButton(onClick = { state.refreshConnections() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.primary)
                        }
                        Button(
                            onClick = { state.closeAllConnections() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text(DesktopStrings.get("connections_close_all", lang), fontSize = 12.sp)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                if (!connState.isConnected) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "VPN is not connected. Connect to see active proxy flows.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (state.activeConnections.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = DesktopStrings.get("connections_none", lang),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.activeConnections, key = { it.id }) { conn ->
                            ActiveConnectionCard(conn = conn) {
                                state.closeConnection(conn.id)
                            }
                        }
                    }
                }
            }

            2 -> {
                // Logs Viewer Tab
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = logSearch,
                        onValueChange = { logSearch = it },
                        placeholder = { Text("Filter logs...") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(Modifier.width(12.dp))
                    Button(
                        onClick = {
                            val allLogs = state.controller.getRecentLogs().joinToString("\n")
                            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(allLogs), null)
                        }
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Copy All Logs")
                    }
                }

                Spacer(Modifier.height(14.dp))

                val logs = state.controller.getRecentLogs().filter { it.contains(logSearch, ignoreCase = true) }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                        .padding(14.dp)
                ) {
                    if (logs.isEmpty()) {
                        Text("No logs available yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(logs) { line ->
                                Text(
                                    text = line,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = if (line.contains("error", ignoreCase = true) || line.contains("panic", ignoreCase = true)) {
                                        MaterialTheme.colorScheme.error
                                    } else if (line.contains("warn", ignoreCase = true)) {
                                        Color(0xFFFFB74D)
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveConnectionCard(
    conn: ActiveConnection,
    onClose: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = conn.metadata.network.uppercase().ifEmpty { "TCP" },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (conn.metadata.host.isNotEmpty()) {
                            "${conn.metadata.host}:${conn.metadata.destinationPort}"
                        } else {
                            "${conn.metadata.destinationIP}:${conn.metadata.destinationPort}"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(Modifier.height(4.dp))

                val procName = conn.metadata.processPath.substringAfterLast('\\').ifEmpty { "System" }
                Text(
                    text = "$procName • Up: ${formatBytes(conn.upload)} • Down: ${formatBytes(conn.download)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onClose) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close connection",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun DoctorItemCard(item: DoctorItem) {
    Card(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (item.isPending) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else if (item.isSuccess) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF00D084),
                    modifier = Modifier.size(22.dp)
                )
            } else {
                Icon(
                    Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.width(14.dp))

            Column {
                Text(
                    text = item.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.details,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private suspend fun runDoctorDiagnostics(
    state: DesktopAppState,
    results: MutableList<DoctorItem>
) = withContext(Dispatchers.IO) {
    // 1. Sing-box binary integrity
    val binVerified = SingBoxBinaryVerifier.verifySingBox(File(state.workingDir, "sing-box.exe"))
    results.add(
        DoctorItem(
            title = "Sing-Box Executable Integrity",
            details = if (binVerified) "Binary hash matches pinned release SHA-256 (2DC46C2...)" else "Binary missing or hash mismatch",
            isSuccess = binVerified
        )
    )

    // 2. Wintun driver (needed for TUN mode)
    val wintunFile = File(state.workingDir, "wintun.dll")
    val wintunOk = wintunFile.exists() && wintunFile.length() > 0
    results.add(
        DoctorItem(
            title = "Wintun TUN Driver",
            details = if (wintunOk) "wintun.dll present (${wintunFile.length()} bytes)" else "wintun.dll missing in working directory",
            isSuccess = wintunOk
        )
    )

    // 3. Local Inbound Port Availability (Mixed Port 2080)
    val portFree = try {
        java.net.ServerSocket(2080).use { true }
    } catch (_: Exception) {
        state.connectionState.value.isConnected // If connected, sing-box already owns port 2080
    }
    results.add(
        DoctorItem(
            title = "Local Inbound Port (127.0.0.1:2080)",
            details = if (portFree) "Port 2080 is available for proxy traffic" else "Port 2080 is blocked by another process",
            isSuccess = portFree
        )
    )

    // 4. Remote Server TCP Reachability
    val profile = state.selectedProfile.value
    if (profile == null) {
        results.add(DoctorItem("Remote Server Reachability", "No active proxy profile selected", isSuccess = false))
    } else {
        var serverOk = false
        var latencyMs = 0L
        val start = System.currentTimeMillis()
        try {
            java.net.Socket().use { sock ->
                sock.connect(InetSocketAddress(profile.server, profile.port), 3000)
                latencyMs = System.currentTimeMillis() - start
                serverOk = true
            }
        } catch (_: Exception) {}

        results.add(
            DoctorItem(
                title = "Proxy Server Reachability (${profile.server}:${profile.port})",
                details = if (serverOk) "TCP connection established in ${latencyMs} ms" else "Server TCP handshake timed out or refused",
                isSuccess = serverOk
            )
        )
    }

    // 5. Proxy Data-Path & Web Bypass
    if (state.connectionState.value.isConnected) {
        val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 2080))
        var cpOk = false
        var cfMs = 0L
        try {
            val start = System.currentTimeMillis()
            val conn = URL("http://cp.cloudflare.com/generate_204").openConnection(proxy) as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            if (conn.responseCode == 204) {
                cfMs = System.currentTimeMillis() - start
                cpOk = true
            }
        } catch (_: Exception) {}

        results.add(
            DoctorItem(
                title = "Data Path (Cloudflare HTTP 204)",
                details = if (cpOk) "Real HTTP response received through tunnel in ${cfMs} ms" else "Failed to route HTTP request through local proxy outbound",
                isSuccess = cpOk
            )
        )

        // 6. Telegram DC Reachability
        var tgOk = false
        try {
            val tgConn = java.net.Socket(proxy)
            tgConn.connect(InetSocketAddress("149.154.167.50", 443), 4000)
            tgConn.close()
            tgOk = true
        } catch (_: Exception) {}

        results.add(
            DoctorItem(
                title = "Telegram DC Connectivity (149.154.167.50:443)",
                details = if (tgOk) "Telegram DC connection successful via tunnel" else "Telegram DC unreachable through tunnel",
                isSuccess = tgOk
            )
        )
    } else {
        results.add(
            DoctorItem(
                title = "Tunnel Data-Path",
                details = "VPN is not connected. Connect to test Cloudflare 204 and Telegram routing.",
                isSuccess = true
            )
        )
    }
}

private fun buildDoctorReport(state: DesktopAppState, results: List<DoctorItem>): String {
    val sb = StringBuilder()
    sb.appendLine("=== NEBULA WINDOWS DIAGNOSTIC REPORT ===")
    sb.appendLine("Time: ${java.time.LocalDateTime.now()}")
    sb.appendLine("OS: Windows ${System.getProperty("os.name")} (${System.getProperty("os.arch")})")
    sb.appendLine("Connection Mode: ${state.connectionMode}")
    sb.appendLine("State: ${if (state.connectionState.value.isConnected) "Connected" else "Disconnected"}")
    val profile = state.selectedProfile.value
    if (profile != null) {
        sb.appendLine("Profile: ${profile.name} (${profile.protocol.name} -> ${profile.server}:${profile.port})")
    }
    sb.appendLine("\n--- DIAGNOSTIC RESULTS ---")
    results.forEach { r ->
        sb.appendLine("[${if (r.isSuccess) "PASS" else "FAIL"}] ${r.title}: ${r.details}")
    }
    sb.appendLine("\n--- RECENT SING-BOX LOGS ---")
    state.controller.getRecentLogs().takeLast(30).forEach { line ->
        sb.appendLine(line)
    }
    return sb.toString()
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1024L * 1024L * 1024L -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024L -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> String.format("%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
