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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import app.nebula.vpn.core.model.RoutingRule
import app.nebula.vpn.core.model.RuleAction
import app.nebula.vpn.core.model.RuleType
import app.nebula.vpn.core.windows.ProcessHelper
import app.nebula.vpn.core.windows.WindowsProcessInfo
import app.nebula.vpn.desktop.i18n.DesktopStrings
import app.nebula.vpn.desktop.state.AppPreset
import app.nebula.vpn.desktop.state.DesktopAppState
import java.util.UUID

@Composable
fun RoutingScreen(state: DesktopAppState) {
    val rules by state.rules.collectAsState()
    val lang = state.currentLanguage

    var showAddDialog by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }
    var runningProcesses by remember { mutableStateOf<List<WindowsProcessInfo>>(emptyList()) }
    var processFilter by remember { mutableStateOf("") }

    var selectedRuleType by remember { mutableStateOf(RuleType.DOMAIN_SUFFIX) }
    var selectedAction by remember { mutableStateOf(RuleAction.PROXY) }
    var ruleValue by remember { mutableStateOf("") }
    var presetNotification by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        // Title Bar & Add Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "${DesktopStrings.get("rules_title", lang)} (${rules.size})",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (presetNotification != null) {
                    Text(
                        text = presetNotification!!,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Button(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(DesktopStrings.get("add_rule", lang), fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(14.dp))

        // Quick App Presets Section
        Text(
            text = DesktopStrings.get("presets_title", lang),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.appPresets) { preset ->
                AppPresetChip(preset = preset, lang = lang) {
                    state.addAppPreset(preset)
                    presetNotification = "${DesktopStrings.get(preset.titleKey, lang)}: ${DesktopStrings.get("preset_added", lang)}"
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Rules List
        if (rules.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No custom rules configured. Click '+ Add Rule' or pick a Quick App Preset above.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rules, key = { it.id }) { rule ->
                    RuleListItem(
                        rule = rule,
                        onDelete = { state.deleteRule(rule.id) }
                    )
                }
            }
        }
    }

    // Add Rule Dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(DesktopStrings.get("add_rule", lang)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Rule Target Type", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TypeChip("Domain", selectedRuleType == RuleType.DOMAIN_SUFFIX) {
                            selectedRuleType = RuleType.DOMAIN_SUFFIX
                        }
                        TypeChip("IP CIDR", selectedRuleType == RuleType.IP_CIDR) {
                            selectedRuleType = RuleType.IP_CIDR
                        }
                        TypeChip("App (.exe)", selectedRuleType == RuleType.PROCESS_NAME) {
                            selectedRuleType = RuleType.PROCESS_NAME
                        }
                    }

                    OutlinedTextField(
                        value = ruleValue,
                        onValueChange = { ruleValue = it },
                        placeholder = {
                            Text(
                                when (selectedRuleType) {
                                    RuleType.PROCESS_NAME -> "e.g. chrome.exe, telegram.exe"
                                    RuleType.IP_CIDR -> "e.g. 192.168.1.0/24"
                                    else -> "e.g. youtube.com, instagram.com"
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (selectedRuleType == RuleType.PROCESS_NAME || selectedRuleType == RuleType.PROCESS_PATH) {
                        Button(
                            onClick = {
                                runningProcesses = ProcessHelper.getRunningProcesses()
                                showAppPicker = true
                            }
                        ) {
                            Icon(Icons.Default.Apps, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(DesktopStrings.get("pick_running_process", lang), fontSize = 12.sp)
                        }
                    }

                    Text("Routing Action", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TypeChip("Proxy", selectedAction == RuleAction.PROXY) { selectedAction = RuleAction.PROXY }
                        TypeChip("Direct", selectedAction == RuleAction.DIRECT) { selectedAction = RuleAction.DIRECT }
                        TypeChip("Block", selectedAction == RuleAction.BLOCK) { selectedAction = RuleAction.BLOCK }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (ruleValue.isNotBlank()) {
                            val newRule = RoutingRule(
                                id = UUID.randomUUID().toString(),
                                type = selectedRuleType,
                                value = ruleValue.trim(),
                                action = selectedAction,
                                priority = rules.size
                            )
                            state.addRule(newRule)
                            ruleValue = ""
                            showAddDialog = false
                        }
                    },
                    enabled = ruleValue.isNotBlank()
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(DesktopStrings.get("btn_cancel", lang))
                }
            }
        )
    }

    // Running Process Picker Dialog
    if (showAppPicker) {
        val filtered = runningProcesses.filter {
            it.name.contains(processFilter, ignoreCase = true) ||
            it.path.contains(processFilter, ignoreCase = true)
        }

        AlertDialog(
            onDismissRequest = { showAppPicker = false },
            title = { Text(DesktopStrings.get("pick_running_process", lang)) },
            text = {
                Column(modifier = Modifier.width(420.dp).height(340.dp)) {
                    OutlinedTextField(
                        value = processFilter,
                        onValueChange = { processFilter = it },
                        placeholder = { Text("Filter processes...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(10.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(filtered, key = { it.pid }) { proc ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        ruleValue = proc.name
                                        selectedRuleType = RuleType.PROCESS_NAME
                                        showAppPicker = false
                                    },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(proc.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(
                                        proc.path.ifEmpty { "PID: ${proc.pid}" },
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAppPicker = false }) {
                    Text(DesktopStrings.get("btn_cancel", lang))
                }
            }
        )
    }
}

@Composable
private fun AppPresetChip(
    preset: AppPreset,
    lang: app.nebula.vpn.desktop.i18n.AppLanguage,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(4.dp))
            Text(
                text = DesktopStrings.get(preset.titleKey, lang),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun RuleListItem(
    rule: RoutingRule,
    onDelete: () -> Unit
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
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = rule.type.name,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = rule.value,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                val actionColor = when (rule.action) {
                    RuleAction.PROXY -> Color(0xFF00D084)
                    RuleAction.DIRECT -> Color(0xFF3B82F6)
                    RuleAction.BLOCK -> Color(0xFFFF5252)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(actionColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = rule.action.name,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = actionColor
                    )
                }
                Spacer(Modifier.width(6.dp))
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TypeChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val bg = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = textColor)
    }
}
