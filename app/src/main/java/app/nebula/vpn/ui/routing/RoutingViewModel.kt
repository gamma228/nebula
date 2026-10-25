package app.nebula.vpn.ui.routing

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nebula.vpn.core.model.AppRoutingMode
import app.nebula.vpn.core.model.RoutingRule
import app.nebula.vpn.core.model.RuleAction
import app.nebula.vpn.core.model.RuleType
import app.nebula.vpn.core.parser.PackageNameParser
import app.nebula.vpn.data.repository.RoutingRepository
import app.nebula.vpn.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

data class AppInfoItem(
    val packageName: String,
    val appName: String,
    val icon: Drawable? = null,
    val isSelected: Boolean = false
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

    private val _domainTraceResult = MutableStateFlow<String?>(null)
    val domainTraceResult: StateFlow<String?> = _domainTraceResult.asStateFlow()

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

    fun applyMassPackageList(rawText: String) {
        viewModelScope.launch {
            val parsed = PackageNameParser.parse(rawText)
            settingsRepository.setTargetPackages(parsed.toSet())
            updateAppSelectionState(parsed.toSet())
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
                priority = current.size
            )
            routingRepository.saveRule(newRule)
        }
    }

    fun deleteRule(id: String) {
        viewModelScope.launch {
            routingRepository.deleteRule(id)
        }
    }

    fun applyRuDirectPreset() {
        viewModelScope.launch {
            val current = customRules.value.toMutableList()
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
            val current = customRules.value.toMutableList()
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
            val current = customRules.value.toMutableList()
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

    fun clearTraceResult() {
        _domainTraceResult.value = null
    }

    fun traceDomain(domain: String) {
        val cleanDomain = domain.trim().lowercase()
        for (rule in customRules.value.sortedBy { it.priority }) {
            val matches = when (rule.type) {
                RuleType.DOMAIN -> cleanDomain == rule.value.lowercase()
                RuleType.DOMAIN_SUFFIX -> cleanDomain.endsWith(rule.value.lowercase())
                RuleType.DOMAIN_KEYWORD -> cleanDomain.contains(rule.value.lowercase())
                RuleType.DOMAIN_REGEX -> try { Regex(rule.value).containsMatchIn(cleanDomain) } catch (e: Exception) { false }
                else -> false
            }
            if (matches) {
                _domainTraceResult.value = "Matched rule: [${rule.type}] ${rule.value} -> Action: ${rule.action}"
                return
            }
        }
        _domainTraceResult.value = "No custom rule matched. Routed to Default Outbound (Proxy)"
    }

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(intent, 0)
            val selectedPackages = settingsRepository.targetPackages.stateIn(viewModelScope).value

            val apps = resolveInfos.mapNotNull { ri ->
                val pkgName = ri.activityInfo.packageName
                if (pkgName == context.packageName) null
                else {
                    AppInfoItem(
                        packageName = pkgName,
                        appName = ri.loadLabel(pm).toString(),
                        icon = ri.loadIcon(pm),
                        isSelected = selectedPackages.contains(pkgName)
                    )
                }
            }.distinctBy { it.packageName }.sortedBy { it.appName }

            withContext(Dispatchers.Main) {
                _installedApps.value = apps
            }
        }
    }

    private fun updateAppSelectionState(selectedPackages: Set<String>) {
        _installedApps.value = _installedApps.value.map { item ->
            item.copy(isSelected = selectedPackages.contains(item.packageName))
        }
    }
}
