package app.nebula.vpn.ui.routing

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nebula.vpn.core.model.AppRoutingMode
import app.nebula.vpn.core.model.RoutingRule
import app.nebula.vpn.core.model.RuleAction
import app.nebula.vpn.core.model.RuleType
import app.nebula.vpn.core.parser.PackageNameParser
import app.nebula.vpn.core.parser.SmartRuleParser
import app.nebula.vpn.core.routing.RuleConflictDetector
import app.nebula.vpn.data.repository.RoutingRepository
import app.nebula.vpn.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

enum class RuleFilterType {
    ALL, PROXY, DIRECT, BLOCK, DOMAIN, IP, APP
}

enum class AppFilterType {
    ALL, SELECTED, UNSELECTED
}

data class AppInfoItem(
    val packageName: String,
    val appName: String,
    val icon: Drawable? = null,
    val isSelected: Boolean = false
)

data class RouteTraceOutput(
    val matchedRule: RoutingRule? = null,
    val outbound: String = "select",
    val explanation: String = ""
)

@HiltViewModel
class RoutingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val routingRepository: RoutingRepository
) : ViewModel() {

    val routingMode: StateFlow<AppRoutingMode> = settingsRepository.routingMode.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        AppRoutingMode.GLOBAL
    )

    val customRules: StateFlow<List<RoutingRule>> = routingRepository.allRules.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    private val _installedApps = MutableStateFlow<List<AppInfoItem>>(emptyList())
    val installedApps: StateFlow<List<AppInfoItem>> = _installedApps.asStateFlow()

    private val _searchFilter = MutableStateFlow("")
    val searchFilter: StateFlow<String> = _searchFilter.asStateFlow()

    private val _ruleFilter = MutableStateFlow(RuleFilterType.ALL)
    val ruleFilter: StateFlow<RuleFilterType> = _ruleFilter.asStateFlow()

    private val _appFilter = MutableStateFlow(AppFilterType.ALL)
    val appFilter: StateFlow<AppFilterType> = _appFilter.asStateFlow()

    private val _selectedRuleIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedRuleIds: StateFlow<Set<String>> = _selectedRuleIds.asStateFlow()

    val shadowedRuleIds: StateFlow<Set<String>> = customRules.combine(_searchFilter) { rules, _ ->
        RuleConflictDetector.findShadowedRules(rules)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val _recentlyDeletedRules = mutableListOf<RoutingRule>()
    val hasUndo: Boolean get() = _recentlyDeletedRules.isNotEmpty()

    private val _domainTraceResult = MutableStateFlow<RouteTraceOutput?>(null)
    val domainTraceResult: StateFlow<RouteTraceOutput?> = _domainTraceResult.asStateFlow()

    // Filtered rules stream
    val filteredRules: StateFlow<List<RoutingRule>> = combine(
        customRules,
        _searchFilter,
        _ruleFilter
    ) { rules, query, filter ->
        rules.filter { rule ->
            // Search query matching
            val matchesQuery = if (query.isBlank()) true else {
                rule.value.contains(query.trim(), ignoreCase = true) ||
                rule.type.name.contains(query.trim(), ignoreCase = true) ||
                rule.action.name.contains(query.trim(), ignoreCase = true)
            }
            // Filter chip matching
            val matchesFilter = when (filter) {
                RuleFilterType.ALL -> true
                RuleFilterType.PROXY -> rule.action == RuleAction.PROXY
                RuleFilterType.DIRECT -> rule.action == RuleAction.DIRECT
                RuleFilterType.BLOCK -> rule.action == RuleAction.BLOCK
                RuleFilterType.DOMAIN -> rule.type in listOf(RuleType.DOMAIN, RuleType.DOMAIN_SUFFIX, RuleType.DOMAIN_KEYWORD, RuleType.DOMAIN_REGEX)
                RuleFilterType.IP -> rule.type == RuleType.IP_CIDR
                RuleFilterType.APP -> rule.type == RuleType.PACKAGE_NAME
            }
            matchesQuery && matchesFilter
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered apps stream
    val filteredApps: StateFlow<List<AppInfoItem>> = combine(
        _installedApps,
        _searchFilter,
        _appFilter
    ) { apps, query, filter ->
        apps.filter { app ->
            val matchesQuery = if (query.isBlank()) true else {
                app.appName.contains(query.trim(), ignoreCase = true) ||
                app.packageName.contains(query.trim(), ignoreCase = true)
            }
            val matchesFilter = when (filter) {
                AppFilterType.ALL -> true
                AppFilterType.SELECTED -> app.isSelected
                AppFilterType.UNSELECTED -> !app.isSelected
            }
            matchesQuery && matchesFilter
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadInstalledApps()
    }

    fun setRoutingMode(mode: AppRoutingMode) {
        viewModelScope.launch {
            settingsRepository.setRoutingMode(mode)
        }
    }

    fun onSearchFilterChanged(query: String) {
        _searchFilter.value = query
    }

    fun setRuleFilter(filter: RuleFilterType) {
        _ruleFilter.value = filter
    }

    fun setAppFilter(filter: AppFilterType) {
        _appFilter.value = filter
    }

    fun toggleRuleSelection(id: String) {
        val current = _selectedRuleIds.value.toMutableSet()
        if (current.contains(id)) {
            current.remove(id)
        } else {
            current.add(id)
        }
        _selectedRuleIds.value = current
    }

    fun selectAllFilteredRules() {
        val allIds = filteredRules.value.map { it.id }.toSet()
        _selectedRuleIds.value = allIds
    }

    fun clearRuleSelection() {
        _selectedRuleIds.value = emptySet()
    }

    fun bulkDeleteSelected() {
        val toDelete = _selectedRuleIds.value
        if (toDelete.isEmpty()) return
        viewModelScope.launch {
            val rulesToDelete = customRules.value.filter { toDelete.contains(it.id) }
            _recentlyDeletedRules.clear()
            _recentlyDeletedRules.addAll(rulesToDelete)
            routingRepository.deleteRules(toDelete)
            clearRuleSelection()
        }
    }

    fun bulkChangeActionSelected(action: RuleAction) {
        val toUpdate = _selectedRuleIds.value
        if (toUpdate.isEmpty()) return
        viewModelScope.launch {
            val updated = customRules.value
                .filter { toUpdate.contains(it.id) }
                .map { it.copy(action = action) }
            routingRepository.saveRules(updated)
            clearRuleSelection()
        }
    }

    fun toggleRule(id: String) {
        viewModelScope.launch {
            val rule = customRules.value.find { it.id == id } ?: return@launch
            val updated = rule.copy(isEnabled = !rule.isEnabled)
            routingRepository.saveRule(updated)
        }
    }

    fun deleteRuleWithUndo(rule: RoutingRule) {
        viewModelScope.launch {
            _recentlyDeletedRules.clear()
            _recentlyDeletedRules.add(rule)
            routingRepository.deleteRule(rule.id)
        }
    }

    fun undoLastDelete() {
        if (_recentlyDeletedRules.isEmpty()) return
        viewModelScope.launch {
            routingRepository.saveRules(_recentlyDeletedRules.toList())
            _recentlyDeletedRules.clear()
        }
    }

    fun addRule(type: RuleType, value: String, action: RuleAction) {
        viewModelScope.launch {
            val current = customRules.value
            val newRule = RoutingRule(
                id = UUID.randomUUID().toString(),
                type = type,
                value = value.trim(),
                action = action,
                priority = current.size,
                isEnabled = true
            )
            routingRepository.saveRule(newRule)
        }
    }

    fun importSmartRules(rawText: String, defaultAction: RuleAction): Int {
        val result = SmartRuleParser.parse(rawText, defaultAction, startPriority = customRules.value.size)
        if (result.rules.isNotEmpty()) {
            viewModelScope.launch {
                routingRepository.saveRules(result.rules)
            }
        }
        return result.rules.size
    }

    fun reorderRules(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            val list = customRules.value.toMutableList()
            if (fromIndex in list.indices && toIndex in list.indices) {
                val item = list.removeAt(fromIndex)
                list.add(toIndex, item)
                routingRepository.updateRulesOrder(list)
            }
        }
    }

    fun toggleAppSelection(packageName: String) {
        viewModelScope.launch {
            val currentSelected = settingsRepository.targetPackages.stateIn(viewModelScope).value.toMutableSet()
            if (currentSelected.contains(packageName)) {
                currentSelected.remove(packageName)
            } else {
                currentSelected.add(packageName)
            }
            settingsRepository.setTargetPackages(currentSelected)
            updateAppSelectionState(currentSelected)
        }
    }

    fun selectAllApps() {
        viewModelScope.launch {
            val allPackages = _installedApps.value.map { it.packageName }.toSet()
            settingsRepository.setTargetPackages(allPackages)
            updateAppSelectionState(allPackages)
        }
    }

    fun clearAllApps() {
        viewModelScope.launch {
            settingsRepository.setTargetPackages(emptySet())
            updateAppSelectionState(emptySet())
        }
    }

    fun invertAppsSelection() {
        viewModelScope.launch {
            val currentSelected = settingsRepository.targetPackages.stateIn(viewModelScope).value
            val inverted = _installedApps.value.map { it.packageName }.filter { !currentSelected.contains(it) }.toSet()
            settingsRepository.setTargetPackages(inverted)
            updateAppSelectionState(inverted)
        }
    }

    fun applyMassPackageList(rawText: String) {
        viewModelScope.launch {
            val parsed = PackageNameParser.parse(rawText)
            settingsRepository.setTargetPackages(parsed.toSet())
            updateAppSelectionState(parsed.toSet())
        }
    }

    // Presets
    fun applyRuDirectPreset() {
        viewModelScope.launch {
            val current = customRules.value
            val presets = listOf(
                RoutingRule(UUID.randomUUID().toString(), RuleType.DOMAIN_SUFFIX, "ru", RuleAction.DIRECT, priority = current.size),
                RoutingRule(UUID.randomUUID().toString(), RuleType.DOMAIN_SUFFIX, "su", RuleAction.DIRECT, priority = current.size + 1),
                RoutingRule(UUID.randomUUID().toString(), RuleType.DOMAIN_SUFFIX, "xn--p1ai", RuleAction.DIRECT, priority = current.size + 2)
            )
            routingRepository.saveRules(presets)
        }
    }

    fun applySocialsProxyPreset() {
        viewModelScope.launch {
            val current = customRules.value
            val presets = listOf(
                RoutingRule(UUID.randomUUID().toString(), RuleType.DOMAIN_SUFFIX, "instagram.com", RuleAction.PROXY, priority = current.size),
                RoutingRule(UUID.randomUUID().toString(), RuleType.DOMAIN_SUFFIX, "cdninstagram.com", RuleAction.PROXY, priority = current.size + 1),
                RoutingRule(UUID.randomUUID().toString(), RuleType.DOMAIN_SUFFIX, "twitter.com", RuleAction.PROXY, priority = current.size + 2),
                RoutingRule(UUID.randomUUID().toString(), RuleType.DOMAIN_SUFFIX, "x.com", RuleAction.PROXY, priority = current.size + 3),
                RoutingRule(UUID.randomUUID().toString(), RuleType.DOMAIN_SUFFIX, "facebook.com", RuleAction.PROXY, priority = current.size + 4)
            )
            routingRepository.saveRules(presets)
        }
    }

    fun applyAdBlockPreset() {
        viewModelScope.launch {
            val current = customRules.value
            val presets = listOf(
                RoutingRule(UUID.randomUUID().toString(), RuleType.DOMAIN_KEYWORD, "doubleclick", RuleAction.BLOCK, priority = current.size),
                RoutingRule(UUID.randomUUID().toString(), RuleType.DOMAIN_KEYWORD, "adservice", RuleAction.BLOCK, priority = current.size + 1),
                RoutingRule(UUID.randomUUID().toString(), RuleType.DOMAIN_KEYWORD, "googleadservices", RuleAction.BLOCK, priority = current.size + 2)
            )
            routingRepository.saveRules(presets)
        }
    }

    fun clearAllRules() {
        viewModelScope.launch {
            for (rule in customRules.value) {
                routingRepository.deleteRule(rule.id)
            }
        }
    }

    // Route Trace Simulator
    fun traceRoute(input: String) {
        val target = input.trim().lowercase()
        if (target.isEmpty()) {
            _domainTraceResult.value = null
            return
        }

        val rules = customRules.value.sortedBy { it.priority }
        for (rule in rules) {
            if (!rule.isEnabled) continue
            val rVal = rule.value.lowercase()
            val matches = when (rule.type) {
                RuleType.DOMAIN -> target == rVal
                RuleType.DOMAIN_SUFFIX -> target == rVal || target.endsWith(".$rVal")
                RuleType.DOMAIN_KEYWORD -> target.contains(rVal)
                RuleType.DOMAIN_REGEX -> try { Regex(rule.value).containsMatchIn(target) } catch (e: Exception) { false }
                RuleType.IP_CIDR -> target == rVal || target.startsWith(rVal.substringBefore("/"))
                RuleType.PACKAGE_NAME -> target == rVal
                else -> false
            }

            if (matches) {
                val outboundName = when (rule.action) {
                    RuleAction.PROXY -> "select (Proxy Tunnel)"
                    RuleAction.DIRECT -> "direct (Direct Bypass)"
                    RuleAction.BLOCK -> "block (Blackhole / Dropped)"
                }
                _domainTraceResult.value = RouteTraceOutput(
                    matchedRule = rule,
                    outbound = outboundName,
                    explanation = "Matched Rule #${rule.priority + 1} [${rule.type}] \"${rule.value}\""
                )
                return
            }
        }

        _domainTraceResult.value = RouteTraceOutput(
            matchedRule = null,
            outbound = "select (Proxy Tunnel)",
            explanation = "No user rules matched. Evaluated default final outbound."
        )
    }

    fun clearTraceResult() {
        _domainTraceResult.value = null
    }

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val intent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val resolveInfos = try {
                    pm.queryIntentActivities(intent, 0)
                } catch (e: Exception) {
                    emptyList()
                }
                val selectedPackages = try {
                    settingsRepository.targetPackages.first()
                } catch (e: Exception) {
                    emptySet()
                }

                val apps = resolveInfos.mapNotNull { ri ->
                    val pkgName = ri.activityInfo?.packageName ?: return@mapNotNull null
                    if (pkgName == context.packageName) null
                    else {
                        val label = try {
                            ri.loadLabel(pm).toString()
                        } catch (_: Exception) {
                            pkgName
                        }
                        val icon = try {
                            ri.loadIcon(pm)
                        } catch (_: Exception) {
                            null
                        }
                        AppInfoItem(
                            packageName = pkgName,
                            appName = label,
                            icon = icon,
                            isSelected = selectedPackages.contains(pkgName)
                        )
                    }
                }.distinctBy { it.packageName }.sortedBy { it.appName }

                withContext(Dispatchers.Main) {
                    _installedApps.value = apps
                }
            } catch (e: Exception) {
                android.util.Log.e("RoutingViewModel", "Failed to load installed apps: ${e.message}", e)
            }
        }
    }

    private fun updateAppSelectionState(selectedPackages: Set<String>) {
        _installedApps.value = _installedApps.value.map { item ->
            item.copy(isSelected = selectedPackages.contains(item.packageName))
        }
    }
}
