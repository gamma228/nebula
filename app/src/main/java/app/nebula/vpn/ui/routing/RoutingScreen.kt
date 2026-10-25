package app.nebula.vpn.ui.routing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterCenterFocus
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import app.nebula.vpn.core.model.RoutingRule
import app.nebula.vpn.core.model.RuleAction
import app.nebula.vpn.core.model.RuleType
import app.nebula.vpn.ui.theme.EmeraldGreen
import app.nebula.vpn.ui.theme.MintTeal
import app.nebula.vpn.ui.theme.StatusGreen
import app.nebula.vpn.ui.theme.StatusRed
import app.nebula.vpn.ui.theme.StatusYellow

@Composable
fun RoutingScreen(
    viewModel: RoutingViewModel,
    modifier: Modifier = Modifier
) {
    val routingMode by viewModel.routingMode.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()
    val customRules by viewModel.customRules.collectAsState()
    val searchQuery by viewModel.searchFilter.collectAsState()
    val traceResult by viewModel.domainTraceResult.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.routing_apps),
        stringResource(R.string.routing_rules)
    )

    var showAddRuleDialog by remember { mutableStateOf(false) }
    var traceInputDomain by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Screen Header
        Text(
            text = stringResource(R.string.routing_title),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Compact Modern Segmented Mode Selector
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SegmentButton(
                        icon = Icons.Default.Public,
                        title = "Все сети",
                        isSelected = routingMode == AppRoutingMode.GLOBAL,
                        onClick = { viewModel.setRoutingMode(AppRoutingMode.GLOBAL) },
                        modifier = Modifier.weight(1f)
                    )
                    SegmentButton(
                        icon = Icons.Default.Bolt,
                        title = "Обход",
                        isSelected = routingMode == AppRoutingMode.BYPASS_APPS,
                        onClick = { viewModel.setRoutingMode(AppRoutingMode.BYPASS_APPS) },
                        modifier = Modifier.weight(1f)
                    )
                    SegmentButton(
                        icon = Icons.Default.FilterCenterFocus,
                        title = "Выбранные",
                        isSelected = routingMode == AppRoutingMode.FORWARD_APPS,
                        onClick = { viewModel.setRoutingMode(AppRoutingMode.FORWARD_APPS) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Concise Status Subtitle
                Text(
                    text = when (routingMode) {
                        AppRoutingMode.GLOBAL -> "Весь интернет-трафик направляется через защищенный VPN-туннель"
                        AppRoutingMode.BYPASS_APPS -> "Выбранные приложения работают напрямую в обход VPN"
                        AppRoutingMode.FORWARD_APPS -> "Только выбранные приложения направляются в VPN, остальные напрямую"
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Tabs: Apps vs Custom Rules
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            tabs.forEachIndexed { index, title ->
                val badgeText = if (index == 0) {
                    val count = installedApps.count { it.isSelected }
                    if (count > 0) " ($count)" else ""
                } else {
                    if (customRules.isNotEmpty()) " (${customRules.size})" else ""
                }
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = {
                        Text(
                            text = "$title$badgeText",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (selectedTabIndex == 0) {
            // Apps tab
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchFilterChanged(it) },
                placeholder = { Text(stringResource(R.string.search_apps)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            val filteredApps = installedApps.filter {
                searchQuery.isEmpty() ||
                        it.appName.contains(searchQuery, ignoreCase = true) ||
                        it.packageName.contains(searchQuery, ignoreCase = true)
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleAppSelection(app.packageName) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.appName,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = app.packageName,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                            Checkbox(
                                checked = app.isSelected,
                                onCheckedChange = { viewModel.toggleAppSelection(app.packageName) },
                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
            }
        } else {
            // Custom Rules Tab
            Column(modifier = Modifier.fillMaxSize()) {
                // Rules Actions Header & Quick Presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Правила маршрутизации",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Button(
                        onClick = { showAddRuleDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Добавить", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Presets Bar
                Text(
                    text = "БЫСТРЫЕ ПРЕСЕТЫ",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        PresetChip(
                            title = "🇷🇺 Обход RU (.ru, .рф)",
                            color = StatusGreen,
                            onClick = { viewModel.applyRuDirectPreset() }
                        )
                    }
                    item {
                        PresetChip(
                            title = "🌐 Соцсети (Proxy)",
                            color = MaterialTheme.colorScheme.primary,
                            onClick = { viewModel.applySocialsProxyPreset() }
                        )
                    }
                    item {
                        PresetChip(
                            title = "🛑 Блокировка рекламы",
                            color = StatusRed,
                            onClick = { viewModel.applyAdBlockPreset() }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Domain Route Tracer (Test Domain)
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Проверка маршрута домена",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = traceInputDomain,
                                onValueChange = {
                                    traceInputDomain = it
                                    if (it.isBlank()) viewModel.clearTraceResult()
                                },
                                placeholder = { Text("Например: youtube.com или ya.ru", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.small,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (traceInputDomain.isNotBlank()) {
                                        viewModel.traceDomain(traceInputDomain)
                                    }
                                },
                                shape = MaterialTheme.shapes.small,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Text("Тест", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }

                        if (traceResult != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.small)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = traceResult!!,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Custom Rules List
                if (customRules.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.TravelExplore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Нет пользовательских правил",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Нажмите \"Добавить\" или выберите пресет выше для обхода RU-сайтов или проксирования соцсетей.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        items(customRules, key = { it.id }) { rule ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            ActionBadge(action = rule.action)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = rule.type.name,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = rule.value,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    IconButton(onClick = { viewModel.deleteRule(rule.id) }) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = StatusRed.copy(alpha = 0.8f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Rule Dialog
    if (showAddRuleDialog) {
        var ruleType by remember { mutableStateOf(RuleType.DOMAIN_SUFFIX) }
        var ruleValue by remember { mutableStateOf("") }
        var ruleAction by remember { mutableStateOf(RuleAction.DIRECT) }

        AlertDialog(
            onDismissRequest = { showAddRuleDialog = false },
            title = { Text("Новое правило маршрутизации") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Тип совпадения:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        RuleTypeChip("Окончание", ruleType == RuleType.DOMAIN_SUFFIX) {
                            ruleType = RuleType.DOMAIN_SUFFIX
                        }
                        RuleTypeChip("Точный", ruleType == RuleType.DOMAIN) {
                            ruleType = RuleType.DOMAIN
                        }
                        RuleTypeChip("Ключ", ruleType == RuleType.DOMAIN_KEYWORD) {
                            ruleType = RuleType.DOMAIN_KEYWORD
                        }
                        RuleTypeChip("IP CIDR", ruleType == RuleType.IP_CIDR) {
                            ruleType = RuleType.IP_CIDR
                        }
                    }

                    OutlinedTextField(
                        value = ruleValue,
                        onValueChange = { ruleValue = it },
                        placeholder = {
                            Text(
                                when (ruleType) {
                                    RuleType.DOMAIN_SUFFIX -> "youtube.com, ru, org"
                                    RuleType.DOMAIN -> "api.github.com"
                                    RuleType.DOMAIN_KEYWORD -> "torrent, google"
                                    RuleType.IP_CIDR -> "192.168.1.0/24"
                                    else -> "Значение"
                                }
                            )
                        },
                        label = { Text("Шаблон или значение") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Действие для совпадения:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ActionSelectorChip(
                            title = "Обход (DIRECT)",
                            color = StatusGreen,
                            isSelected = ruleAction == RuleAction.DIRECT,
                            onClick = { ruleAction = RuleAction.DIRECT },
                            modifier = Modifier.weight(1f)
                        )
                        ActionSelectorChip(
                            title = "Прокси (PROXY)",
                            color = MaterialTheme.colorScheme.primary,
                            isSelected = ruleAction == RuleAction.PROXY,
                            onClick = { ruleAction = RuleAction.PROXY },
                            modifier = Modifier.weight(1f)
                        )
                        ActionSelectorChip(
                            title = "Блок (BLOCK)",
                            color = StatusRed,
                            isSelected = ruleAction == RuleAction.BLOCK,
                            onClick = { ruleAction = RuleAction.BLOCK },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (ruleValue.isNotBlank()) {
                            viewModel.addRule(ruleType, ruleValue, ruleAction)
                            showAddRuleDialog = false
                        }
                    }
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddRuleDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun SegmentButton(
    icon: ImageVector,
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val fg = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)

    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(bg)
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PresetChip(
    title: String,
    color: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.35f), MaterialTheme.shapes.small)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}

@Composable
private fun ActionBadge(action: RuleAction) {
    val (label, bg, fg) = when (action) {
        RuleAction.DIRECT -> Triple("ОБХОД", StatusGreen.copy(alpha = 0.15f), StatusGreen)
        RuleAction.PROXY -> Triple("ПРОКСИ", MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), MaterialTheme.colorScheme.primary)
        RuleAction.BLOCK -> Triple("БЛОК", StatusRed.copy(alpha = 0.15f), StatusRed)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = fg
        )
    }
}

@Composable
private fun RuleTypeChip(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(
            text = title,
            fontSize = 11.sp,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ActionSelectorChip(
    title: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(if (isSelected) color.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant)
            .border(if (isSelected) 1.dp else 0.dp, color, MaterialTheme.shapes.small)
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) color else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
