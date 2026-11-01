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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
            .padding(28.dp)
    ) {
        // Tab Row: Doctor vs Logs
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(DesktopStrings.get("doctor_title", lang), fontWeight = FontWeight.SemiBold) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text(DesktopStrings.get("logs_title", lang), fontWeight = FontWeight.SemiBold) }
            )
        }

        Spacer(Modifier.height(18.dp))

        if (selectedTab == 0) {
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
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(DesktopStrings.get("doctor_run_tests", lang))
                }

                if (doctorResults.isNotEmpty()) {
                    Button(
                        onClick = {
                            val report = buildDoctorReport(state, doctorResults)
                            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(report), null)
                            doctorReportStatus = DesktopStrings.get("doctor_report_copied", lang)
                        }
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(DesktopStrings.get("doctor_copy_report", lang))
                    }
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
        } else {
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
                    .background(MaterialTheme.colorScheme.surface)
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

@Composable
private fun DoctorItemCard(item: DoctorItem) {
    Card(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (item.isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                tint = if (item.isSuccess) Color(0xFF00D084) else Color(0xFFFF5252),
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = item.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = item.details,
                    fontSize = 12.sp,
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
    // 1. Check sing-box.exe binary & hash
    val singBox = state.controller.singBoxBinary
    val singBoxOk = singBox.exists() && SingBoxBinaryVerifier.verifySingBox(singBox)
    results.add(
        DoctorItem(
            title = "sing-box Core Binary",
            details = if (singBoxOk) "Verified SHA-256: ${singBox.absolutePath}" else "Missing or corrupted binary!",
            isSuccess = singBoxOk
        )
    )

    // 2. Check wintun.dll driver
    val wintun = File("C:\\Users\\User\\tools\\wintun.dll")
    val wintunOk = wintun.exists() && SingBoxBinaryVerifier.verifyWintun(wintun)
    results.add(
        DoctorItem(
            title = "Wintun Virtual Adapter Driver",
            details = if (wintunOk) "Verified SHA-256 driver present" else "wintun.dll missing or untrusted",
            isSuccess = wintunOk
        )
    )

    // 3. System Proxy State Check
    val proxyEnabled = state.integration.isSystemProxyEnabled()
    val isConnected = state.connectionState.value.isConnected
    results.add(
        DoctorItem(
            title = "Windows System Proxy Subsystem",
            details = if (proxyEnabled) "System proxy active (127.0.0.1:2080)" else "Direct internet (Proxy disabled)",
            isSuccess = true
        )
    )

    // 4. Test Connectivity through proxy/tunnel if connected
    val proxy = if (proxyEnabled) Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 2080)) else Proxy.NO_PROXY

    // Probe Cloudflare HTTP 204
    try {
        val url = URL("https://cp.cloudflare.com/generate_204")
        val conn = url.openConnection(proxy) as HttpURLConnection
        conn.connectTimeout = 3000
        conn.readTimeout = 3000
        val code = conn.responseCode
        results.add(
            DoctorItem(
                title = "Web Bypass Reachability (Cloudflare 204)",
                details = "HTTP Status $code OK",
                isSuccess = code == 204 || code == 200
            )
        )
        conn.disconnect()
    } catch (e: Exception) {
        results.add(
            DoctorItem(
                title = "Web Bypass Reachability (Cloudflare 204)",
                details = "Failed: ${e.message}",
                isSuccess = false
            )
        )
    }

    // Probe Exit IP
    try {
        val url = URL("https://api.ipify.org")
        val conn = url.openConnection(proxy) as HttpURLConnection
        conn.connectTimeout = 3000
        conn.readTimeout = 3000
        val ip = conn.inputStream.bufferedReader().readText().trim()
        results.add(
            DoctorItem(
                title = "Public Exit IP Address",
                details = "Exit IP: $ip",
                isSuccess = ip.isNotBlank()
            )
        )
        conn.disconnect()
    } catch (e: Exception) {
        results.add(
            DoctorItem(
                title = "Public Exit IP Address",
                details = "Failed to query exit IP: ${e.message}",
                isSuccess = false
            )
        )
    }
}

private fun buildDoctorReport(state: DesktopAppState, results: List<DoctorItem>): String {
    val sb = StringBuilder()
    sb.appendLine("=== NEBULA DESKTOP CONNECTION DOCTOR REPORT ===")
    sb.appendLine("App Version: v1.2.0 Desktop (Windows)")
    sb.appendLine("Connection Mode: ${state.connectionMode}")
    sb.appendLine("Connection State: ${state.connectionState.value}")
    sb.appendLine("Active Profile: ${state.selectedProfile.value?.name ?: "None"}")
    sb.appendLine("\n--- DIAGNOSTIC RESULTS ---")
    for (r in results) {
        sb.appendLine("[${if (r.isSuccess) "PASS" else "FAIL"}] ${r.title}: ${r.details}")
    }
    sb.appendLine("\n--- RECENT SING-BOX LOGS (LAST 50) ---")
    for (l in state.controller.getRecentLogs().takeLast(50)) {
        sb.appendLine(l)
    }
    return sb.toString()
}
