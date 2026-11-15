package app.nebula.vpn.desktop.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.nebula.vpn.core.model.*
import app.nebula.vpn.desktop.i18n.AppLanguage
import app.nebula.vpn.desktop.i18n.DesktopStrings

@Composable
fun NodeEditorDialog(
    profile: ProxyProfile,
    language: AppLanguage,
    onDismiss: () -> Unit,
    onSave: (ProxyProfile) -> Unit
) {
    var name by remember { mutableStateOf(profile.name) }
    var server by remember { mutableStateOf(profile.server) }
    var portText by remember { mutableStateOf(profile.port.toString()) }
    var authSecret by remember { mutableStateOf(profile.authSecret) }
    var sni by remember { mutableStateOf(profile.tls.serverName) }
    var realityPubKey by remember { mutableStateOf(profile.tls.realityPublicKey) }
    var realityShortId by remember { mutableStateOf(profile.tls.realityShortId) }
    var flow by remember { mutableStateOf(profile.tls.flow) }
    var alpnText by remember { mutableStateOf(profile.tls.alpn.joinToString(",")) }
    var transportType by remember { mutableStateOf(profile.transport.type) }
    var path by remember { mutableStateOf(profile.transport.path) }
    var serviceName by remember { mutableStateOf(profile.transport.serviceName) }
    var host by remember { mutableStateOf(profile.transport.host) }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.width(580.dp).height(620.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Text(
                    text = "${DesktopStrings.get("edit_node", language)}: ${profile.protocol.name}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Server Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = server,
                            onValueChange = { server = it },
                            label = { Text("Server Host / IP") },
                            modifier = Modifier.weight(0.7f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = portText,
                            onValueChange = { portText = it },
                            label = { Text("Port") },
                            modifier = Modifier.weight(0.3f),
                            singleLine = true
                        )
                    }

                    OutlinedTextField(
                        value = authSecret,
                        onValueChange = { authSecret = it },
                        label = { Text("UUID / Password") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = sni,
                        onValueChange = { sni = it },
                        label = { Text("TLS SNI (Server Name)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    if (profile.protocol == ProtocolType.VLESS) {
                        OutlinedTextField(
                            value = flow,
                            onValueChange = { flow = it },
                            label = { Text("Flow (e.g. xtls-rprx-vision)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = realityPubKey,
                            onValueChange = { realityPubKey = it },
                            label = { Text("Reality Public Key (pbk)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = realityShortId,
                            onValueChange = { realityShortId = it },
                            label = { Text("Reality Short ID (sid)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    OutlinedTextField(
                        value = alpnText,
                        onValueChange = { alpnText = it },
                        label = { Text("ALPN (comma separated, e.g. h2,http/1.1)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // Transport section
                    Text(
                        text = "Transport Settings",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TransportType.entries.forEach { t ->
                            FilterChip(
                                selected = transportType == t,
                                onClick = { transportType = t },
                                label = { Text(t.name, fontSize = 11.sp) }
                            )
                        }
                    }

                    if (transportType == TransportType.GRPC) {
                        OutlinedTextField(
                            value = serviceName,
                            onValueChange = { serviceName = it },
                            label = { Text("gRPC Service Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    } else if (transportType == TransportType.WS || transportType == TransportType.HTTP_UPGRADE) {
                        OutlinedTextField(
                            value = path,
                            onValueChange = { path = it },
                            label = { Text("WebSocket / HTTP Path") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = host,
                            onValueChange = { host = it },
                            label = { Text("Host Header") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(DesktopStrings.get("btn_cancel", language))
                    }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = {
                            val port = portText.toIntOrNull()
                            if (server.isBlank()) {
                                errorMessage = "Server address cannot be empty"
                                return@Button
                            }
                            if (port == null || port !in 1..65535) {
                                errorMessage = "Port must be a valid number (1-65535)"
                                return@Button
                            }

                            val alpnList = alpnText.split(",")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }

                            val updatedTls = profile.tls.copy(
                                enabled = sni.isNotBlank() || realityPubKey.isNotBlank() || profile.tls.enabled,
                                serverName = sni.trim(),
                                flow = flow.trim(),
                                realityPublicKey = realityPubKey.trim(),
                                realityShortId = realityShortId.trim(),
                                alpn = alpnList
                            )

                            val updatedTransport = profile.transport.copy(
                                type = transportType,
                                path = path.trim(),
                                host = host.trim(),
                                serviceName = serviceName.trim()
                            )

                            val updatedProfile = profile.copy(
                                name = name.trim().ifEmpty { profile.name },
                                server = server.trim(),
                                port = port,
                                authSecret = authSecret.trim(),
                                tls = updatedTls,
                                transport = updatedTransport,
                                updatedAt = System.currentTimeMillis()
                            )

                            onSave(updatedProfile)
                        }
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
