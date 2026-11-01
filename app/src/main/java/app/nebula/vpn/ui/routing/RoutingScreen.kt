package app.nebula.vpn.ui.routing

import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import app.nebula.vpn.ui.theme.StatusGreen
import app.nebula.vpn.ui.theme.StatusRed
import app.nebula.vpn.ui.theme.StatusYellow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FilterCenterFocus
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import app.nebula.vpn.R
import app.nebula.vpn.core.model.AppRoutingMode
import app.nebula.vpn.core.model.RoutingRule
import app.nebula.vpn.core.model.RuleAction
import app.nebula.vpn.core.model.RuleType
import app.nebula.vpn.ui.theme.NebulaTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutingScreen(
    viewModel: RoutingViewModel,
    modifier: Modifier = Modifier
) {
    val routingMode by viewModel.routingMode.collectAsState()
    val customRules by viewModel.customRules.collectAsState()
    val filteredRules by viewModel.filteredRules.collectAsState()
    val filteredApps by viewModel.filteredApps.collectAsState()
    val searchQuery by viewModel.searchFilter.collectAsState()
    val ruleFilter by viewModel.ruleFilter.collectAsState()
    val appFilter by viewModel.appFilter.collectAsState()
    val selectedRuleIds by viewModel.selectedRuleIds.collectAsState()
    val shadowedRuleIds by viewModel.shadowedRuleIds.collectAsState()
    val traceResult by viewModel.domainTraceResult.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.routing_tab_rules) to customRules.size,
        stringResource(R.string.routing_tab_apps) to filteredApps.size,
        stringResource(R.string.routing_tab_presets) to 3,
        stringResource(R.string.routing_tab_test) to 0
    )

    var showAddRuleSheet by remember { mutableStateOf(false) }
    var showMassImportDialog by remember { mutableStateOf(false) }

    val colors = NebulaTheme.colors
    val spacing = NebulaTheme.spacing
    val shapes = NebulaTheme.shapes
    val typography = NebulaTheme.typography

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (selectedTabIndex == 0) {
                FloatingActionButton(
                    onClick = { showAddRuleSheet = true },
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                    shape = shapes.button
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_rule))
                }
            }
        },
        containerColor = Color.Transparent,
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = spacing.md, vertical = spacing.xs)
        ) {
            // Screen Title & Summary Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = spacing.xxs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.routing_title),
                        style = typography.displayLarge.copy(fontSize = 22.sp),
                        color = colors.textPrimary
                    )
                    Text(
                        text = "${customRules.count { it.isEnabled }} active rules • Mode: ${routingMode.name}",
                        style = typography.bodySmall,
                        color = colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.xs))

            // Sticky Top Segmented Tabs
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = colors.surfaceElevated,
                contentColor = colors.primary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = colors.primary
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shapes.card)
                    .border(1.dp, colors.borderSubtle, shapes.card)
            ) {
                tabs.forEachIndexed { index, (title, count) ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = title,
                                    fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                                if (count > 0 && index < 2) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                if (selectedTabIndex == index) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                                CircleShape
                                            )
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = count.toString(),
                                            fontSize = 10.sp,
                                            color = if (selectedTabIndex == index) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Pinned Search Field (for Rules and Apps tabs)
            if (selectedTabIndex in 0..1) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.onSearchFilterChanged(it) },
                    placeholder = { Text(stringResource(R.string.search_rules_apps), fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchFilterChanged("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Tab Content
            when (selectedTabIndex) {
                0 -> RulesTabContent(
                    rules = filteredRules,
                    selectedFilter = ruleFilter,
                    selectedRuleIds = selectedRuleIds,
                    shadowedRuleIds = shadowedRuleIds,
                    onFilterSelected = { viewModel.setRuleFilter(it) },
                    onToggleRule = { viewModel.toggleRule(it) },
                    onDeleteRule = { rule ->
                        viewModel.deleteRuleWithUndo(rule)
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = "Rule deleted: ${rule.value}",
                                actionLabel = "Undo",
                                duration = SnackbarDuration.Short
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                viewModel.undoLastDelete()
                            }
                        }
                    },
                    onToggleSelect = { viewModel.toggleRuleSelection(it) },
                    onSelectAll = { viewModel.selectAllFilteredRules() },
                    onClearSelection = { viewModel.clearRuleSelection() },
                    onBulkDelete = { viewModel.bulkDeleteSelected() },
                    onBulkChangeAction = { viewModel.bulkChangeActionSelected(it) },
                    onMoveUp = { idx -> if (idx > 0) viewModel.reorderRules(idx, idx - 1) },
                    onMoveDown = { idx -> if (idx < customRules.size - 1) viewModel.reorderRules(idx, idx + 1) }
                )
                1 -> AppsTabContent(
                    apps = filteredApps,
                    routingMode = routingMode,
                    appFilter = appFilter,
                    onModeChanged = { viewModel.setRoutingMode(it) },
                    onAppFilterChanged = { viewModel.setAppFilter(it) },
                    onToggleApp = { viewModel.toggleAppSelection(it) },
                    onSelectAll = { viewModel.selectAllApps() },
                    onClearAll = { viewModel.clearAllApps() },
                    onInvert = { viewModel.invertAppsSelection() },
                    onOpenMassImport = { showMassImportDialog = true }
                )
                2 -> PresetsTabContent(
                    onApplyRuDirect = {
                        viewModel.applyRuDirectPreset()
                        scope.launch { snackbarHostState.showSnackbar("Russia Direct preset applied") }
                    },
                    onApplyAdBlock = {
                        viewModel.applyAdBlockPreset()
                        scope.launch { snackbarHostState.showSnackbar("AdBlock preset applied") }
                    },
                    onApplySocials = {
                        viewModel.applySocialsProxyPreset()
                        scope.launch { snackbarHostState.showSnackbar("Socials Proxy preset applied") }
                    }
                )
                3 -> TestTabContent(
                    traceResult = traceResult,
                    onTrace = { viewModel.traceRoute(it) },
                    onClear = { viewModel.clearTraceResult() }
                )
            }
        }
    }

    // Add Rule / Smart Paste Bottom Sheet
    if (showAddRuleSheet) {
        AddRuleBottomSheet(
            onDismiss = { showAddRuleSheet = false },
            onAddSingleRule = { type, value, action ->
                viewModel.addRule(type, value, action)
                showAddRuleSheet = false
            },
            onImportSmartRules = { raw, action ->
                val count = viewModel.importSmartRules(raw, action)
                showAddRuleSheet = false
                scope.launch { snackbarHostState.showSnackbar("Imported $count rules successfully") }
            }
        )
    }

    // Mass Package Name Import Dialog
    if (showMassImportDialog) {
        MassPackageImportDialog(
            onDismiss = { showMassImportDialog = false },
            onImport = { raw ->
                viewModel.applyMassPackageList(raw)
                showMassImportDialog = false
                scope.launch { snackbarHostState.showSnackbar("Package list updated") }
            }
        )
    }
}

// -------------------------------------------------------------
// TAB 0: RULES TAB (Compact Dense List with Filters & Bulk Ops)
// -------------------------------------------------------------
@Composable
fun RulesTabContent(
    rules: List<RoutingRule>,
    selectedFilter: RuleFilterType,
    selectedRuleIds: Set<String>,
    shadowedRuleIds: Set<String>,
    onFilterSelected: (RuleFilterType) -> Unit,
    onToggleRule: (String) -> Unit,
    onDeleteRule: (RoutingRule) -> Unit,
    onToggleSelect: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onBulkDelete: () -> Unit,
    onBulkChangeAction: (RuleAction) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Filter Chips Row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(RuleFilterType.values()) { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { onFilterSelected(filter) },
                    label = { Text(filter.name, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        // Bulk Selection Action Bar
        AnimatedVisibility(visible = selectedRuleIds.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${selectedRuleIds.size} selected",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = { onBulkChangeAction(RuleAction.PROXY) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Bolt, contentDescription = "Proxy", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { onBulkChangeAction(RuleAction.DIRECT) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.AutoMirrored.Filled.AltRoute, contentDescription = "Direct", tint = StatusGreen, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { onBulkChangeAction(RuleAction.BLOCK) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Block, contentDescription = "Block", tint = StatusRed, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = onBulkDelete, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = StatusRed, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = onClearSelection, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Dense Rules List
        if (rules.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 40.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Text(
                    text = "No rules match the current filter.\nTap + to add or smart-paste rules.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(rules, key = { _, item -> item.id }) { index, rule ->
                    val isSelected = selectedRuleIds.contains(rule.id)
                    val isShadowed = shadowedRuleIds.contains(rule.id)

                    CompactRuleRow(
                        rule = rule,
                        index = index,
                        isSelected = isSelected,
                        isShadowed = isShadowed,
                        onToggleEnabled = { onToggleRule(rule.id) },
                        onDelete = { onDeleteRule(rule) },
                        onSelect = { onToggleSelect(rule.id) },
                        onMoveUp = { onMoveUp(index) },
                        onMoveDown = { onMoveDown(index) }
                    )
                }
                item { Spacer(modifier = Modifier.height(72.dp)) }
            }
        }
    }
}

@Composable
fun CompactRuleRow(
    rule: RoutingRule,
    index: Int,
    isSelected: Boolean,
    isShadowed: Boolean,
    onToggleEnabled: () -> Unit,
    onDelete: () -> Unit,
    onSelect: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Priority index badge
                Text(
                    text = "#${index + 1}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(28.dp)
                )

                // Type Icon
                val typeIcon = when (rule.type) {
                    RuleType.DOMAIN, RuleType.DOMAIN_SUFFIX, RuleType.DOMAIN_KEYWORD, RuleType.DOMAIN_REGEX -> Icons.Default.Language
                    RuleType.IP_CIDR -> Icons.Default.Public
                    RuleType.PACKAGE_NAME, RuleType.PROCESS_NAME, RuleType.PROCESS_PATH -> Icons.Default.FilterCenterFocus
                    RuleType.RULE_SET -> Icons.Default.Shield
                }
                Icon(
                    typeIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )

                Spacer(modifier = Modifier.width(6.dp))

                // Rule Value & Type Tag
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rule.value,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (rule.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Text(
                        text = rule.type.name,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Action Chip
                val (actionBg, actionFg) = when (rule.action) {
                    RuleAction.PROXY -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
                    RuleAction.DIRECT -> StatusGreen to Color.Black
                    RuleAction.BLOCK -> StatusRed to Color.White
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(actionBg.copy(alpha = 0.2f))
                        .border(1.dp, actionBg.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = rule.action.name,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = actionBg
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Switch enable / disable
                Switch(
                    checked = rule.isEnabled,
                    onCheckedChange = { onToggleEnabled() },
                    modifier = Modifier.size(width = 36.dp, height = 24.dp)
                )

                // Delete Icon
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                }
            }

            // Conflict / Shadowing warning indicator
            if (isShadowed) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(StatusYellow.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = StatusYellow, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.rule_shadowed_warning),
                        fontSize = 10.sp,
                        color = StatusYellow
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 1: APPS TAB (App Picker, Mass Package Import, Mode Selector)
// -------------------------------------------------------------
@Composable
fun AppsTabContent(
    apps: List<AppInfoItem>,
    routingMode: AppRoutingMode,
    appFilter: AppFilterType,
    onModeChanged: (AppRoutingMode) -> Unit,
    onAppFilterChanged: (AppFilterType) -> Unit,
    onToggleApp: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
    onInvert: () -> Unit,
    onOpenMassImport: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Pinned Mode Selector Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ModeSelectorButton(
                        title = stringResource(R.string.app_mode_global),
                        isSelected = routingMode == AppRoutingMode.GLOBAL,
                        onClick = { onModeChanged(AppRoutingMode.GLOBAL) },
                        modifier = Modifier.weight(1f)
                    )
                    ModeSelectorButton(
                        title = stringResource(R.string.app_mode_bypass),
                        isSelected = routingMode == AppRoutingMode.BYPASS_APPS,
                        onClick = { onModeChanged(AppRoutingMode.BYPASS_APPS) },
                        modifier = Modifier.weight(1f)
                    )
                    ModeSelectorButton(
                        title = stringResource(R.string.app_mode_forward),
                        isSelected = routingMode == AppRoutingMode.FORWARD_APPS,
                        onClick = { onModeChanged(AppRoutingMode.FORWARD_APPS) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Short Mode Explanation
                val desc = when (routingMode) {
                    AppRoutingMode.GLOBAL -> stringResource(R.string.app_mode_global_desc)
                    AppRoutingMode.BYPASS_APPS -> stringResource(R.string.app_mode_bypass_desc)
                    AppRoutingMode.FORWARD_APPS -> stringResource(R.string.app_mode_forward_desc)
                }
                Text(
                    text = desc,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Quick App Action Buttons & Filters
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = appFilter == AppFilterType.ALL,
                    onClick = { onAppFilterChanged(AppFilterType.ALL) },
                    label = { Text("All", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = appFilter == AppFilterType.SELECTED,
                    onClick = { onAppFilterChanged(AppFilterType.SELECTED) },
                    label = { Text("Selected", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = appFilter == AppFilterType.UNSELECTED,
                    onClick = { onAppFilterChanged(AppFilterType.UNSELECTED) },
                    label = { Text("Unselected", fontSize = 11.sp) }
                )
            }

            OutlinedButton(
                onClick = onOpenMassImport,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Mass Import", fontSize = 11.sp)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TextButton(onClick = onSelectAll) { Text("Select All", fontSize = 11.sp) }
            TextButton(onClick = onInvert) { Text("Invert", fontSize = 11.sp) }
            TextButton(onClick = onClearAll) { Text("Clear All", fontSize = 11.sp) }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Apps List
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(apps, key = { it.packageName }) { app ->
                AppRow(app = app, onToggle = { onToggleApp(app.packageName) })
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@Composable
fun AppRow(app: AppInfoItem, onToggle: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (app.icon != null) {
                val bitmap = remember(app.packageName) { app.icon.toBitmap(48, 48).asImageBitmap() }
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.packageName,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Checkbox(
                checked = app.isSelected,
                onCheckedChange = { onToggle() },
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun ModeSelectorButton(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        )
    }
}

// -------------------------------------------------------------
// TAB 2: PRESETS TAB (One-Tap Rule Packs with Preview)
// -------------------------------------------------------------
@Composable
fun PresetsTabContent(
    onApplyRuDirect: () -> Unit,
    onApplyAdBlock: () -> Unit,
    onApplySocials: () -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            PresetCard(
                title = stringResource(R.string.preset_russia_direct),
                description = stringResource(R.string.preset_russia_direct_desc),
                icon = Icons.AutoMirrored.Filled.AltRoute,
                rulesPreview = listOf(".ru (Direct)", ".su (Direct)", ".рф (Direct)"),
                onApply = onApplyRuDirect
            )
        }
        item {
            PresetCard(
                title = stringResource(R.string.preset_block_ads),
                description = stringResource(R.string.preset_block_ads_desc),
                icon = Icons.Default.Block,
                rulesPreview = listOf("doubleclick (Block)", "adservice (Block)", "googleadservices (Block)"),
                onApply = onApplyAdBlock
            )
        }
        item {
            PresetCard(
                title = stringResource(R.string.preset_socials_proxy),
                description = stringResource(R.string.preset_socials_proxy_desc),
                icon = Icons.Default.Bolt,
                rulesPreview = listOf("instagram.com (Proxy)", "twitter.com / x.com (Proxy)", "facebook.com (Proxy)"),
                onApply = onApplySocials
            )
        }
    }
}

@Composable
fun PresetCard(
    title: String,
    description: String,
    icon: ImageVector,
    rulesPreview: List<String>,
    onApply: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(text = description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Rules Preview chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                rulesPreview.forEach { preview ->
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(text = preview, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onApply,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = stringResource(R.string.preset_apply_btn), fontSize = 13.sp)
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 3: TEST TAB (Route Trace Simulator)
// -------------------------------------------------------------
@Composable
fun TestTabContent(
    traceResult: RouteTraceOutput?,
    onTrace: (String) -> Unit,
    onClear: () -> Unit
) {
    var inputQuery by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(text = stringResource(R.string.test_route_title), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(text = stringResource(R.string.test_route_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = inputQuery,
                    onValueChange = { inputQuery = it },
                    placeholder = { Text(stringResource(R.string.test_input_placeholder), fontSize = 13.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onTrace(inputQuery) },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.test_route_btn), fontSize = 13.sp)
                    }
                    if (traceResult != null) {
                        OutlinedButton(
                            onClick = {
                                inputQuery = ""
                                onClear()
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Clear")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Simulation Trace Output Card
        if (traceResult != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Target Outbound: ${traceResult.outbound}", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = traceResult.explanation, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    if (traceResult.matchedRule != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "Rule: [${traceResult.matchedRule.type}] ${traceResult.matchedRule.value} -> ${traceResult.matchedRule.action}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// BOTTOM SHEET: ADD RULE / SMART PASTE (No Hidden Dropdowns)
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRuleBottomSheet(
    onDismiss: () -> Unit,
    onAddSingleRule: (RuleType, String, RuleAction) -> Unit,
    onImportSmartRules: (String, RuleAction) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isSmartPasteMode by remember { mutableStateOf(false) }

    var selectedType by remember { mutableStateOf(RuleType.DOMAIN_SUFFIX) }
    var selectedAction by remember { mutableStateOf(RuleAction.PROXY) }
    var singleValueInput by remember { mutableStateOf("") }
    var smartPasteText by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            // Mode Toggle: Single Rule vs Smart Paste
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ModeSelectorButton(
                    title = "Single Rule",
                    isSelected = !isSmartPasteMode,
                    onClick = { isSmartPasteMode = false },
                    modifier = Modifier.weight(1f)
                )
                ModeSelectorButton(
                    title = "Smart Paste",
                    isSelected = isSmartPasteMode,
                    onClick = { isSmartPasteMode = true },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Segmented Control (Proxy / Direct / Block)
            Text(text = stringResource(R.string.rule_action), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ModeSelectorButton(
                    title = "Proxy",
                    isSelected = selectedAction == RuleAction.PROXY,
                    onClick = { selectedAction = RuleAction.PROXY },
                    modifier = Modifier.weight(1f)
                )
                ModeSelectorButton(
                    title = "Direct",
                    isSelected = selectedAction == RuleAction.DIRECT,
                    onClick = { selectedAction = RuleAction.DIRECT },
                    modifier = Modifier.weight(1f)
                )
                ModeSelectorButton(
                    title = "Block",
                    isSelected = selectedAction == RuleAction.BLOCK,
                    onClick = { selectedAction = RuleAction.BLOCK },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (!isSmartPasteMode) {
                // Rule Type Segmented Controls (all visible, no hidden dropdowns)
                Text(text = stringResource(R.string.rule_type), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val availableTypes = listOf(
                        RuleType.DOMAIN_SUFFIX to "Domain Suffix",
                        RuleType.DOMAIN to "Full Domain",
                        RuleType.DOMAIN_KEYWORD to "Keyword",
                        RuleType.IP_CIDR to "IP CIDR",
                        RuleType.PACKAGE_NAME to "Package Name"
                    )
                    items(availableTypes) { (type, label) ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = singleValueInput,
                    onValueChange = { singleValueInput = it },
                    label = { Text(stringResource(R.string.rule_value)) },
                    placeholder = { Text("e.g. google.com or 192.168.1.0/24") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (singleValueInput.isNotBlank()) {
                            onAddSingleRule(selectedType, singleValueInput.trim(), selectedAction)
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    enabled = singleValueInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add Rule")
                }
            } else {
                // Smart Paste Multi-Line Text Area
                Text(text = stringResource(R.string.smart_paste_title), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = smartPasteText,
                    onValueChange = { smartPasteText = it },
                    placeholder = { Text(stringResource(R.string.smart_paste_hint), fontSize = 12.sp) },
                    minLines = 4,
                    maxLines = 8,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        if (smartPasteText.isNotBlank()) {
                            onImportSmartRules(smartPasteText, selectedAction)
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    enabled = smartPasteText.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.smart_paste_import_btn))
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

// -------------------------------------------------------------
// MASS PACKAGE IMPORT DIALOG
// -------------------------------------------------------------
@Composable
fun MassPackageImportDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit
) {
    var rawText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_mass_import), fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    text = "Paste package names (comma or line separated):\ne.g. com.google.android.youtube, org.telegram.messenger",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = rawText,
                    onValueChange = { rawText = it },
                    placeholder = { Text("com.example.app\ncom.other.app") },
                    minLines = 4,
                    maxLines = 6,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onImport(rawText) },
                enabled = rawText.isNotBlank()
            ) {
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
