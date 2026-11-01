package app.nebula.vpn.core.contract

import app.nebula.vpn.core.model.ConnectionState
import app.nebula.vpn.core.model.ProxyProfile
import app.nebula.vpn.core.model.RoutingRule
import app.nebula.vpn.core.model.TrafficStats
import kotlinx.coroutines.flow.StateFlow

/**
 * Platform-independent contract for controlling the proxy/VPN engine.
 * Android implements via VpnService + libbox.aar.
 * Windows implements via sing-box.exe child process + Wintun / WinINET.
 */
interface CoreController {
    val connectionState: StateFlow<ConnectionState>
    val trafficStats: StateFlow<TrafficStats>

    suspend fun start(profile: ProxyProfile, configJson: String): Result<Unit>
    suspend fun stop(): Result<Unit>
    suspend fun reloadConfig(newConfigJson: String): Result<Unit>
    fun isRunning(): Boolean
}

/**
 * Storage repository contract for profiles, subscriptions, and rules.
 * Android implements via Room database.
 * Windows implements via JSON / SQLite in %APPDATA%\Nebula\ with DPAPI encryption.
 */
interface StorageRepository {
    suspend fun getAllProfiles(): List<ProxyProfile>
    suspend fun getProfileById(id: String): ProxyProfile?
    suspend fun saveProfile(profile: ProxyProfile)
    suspend fun saveProfiles(profiles: List<ProxyProfile>)
    suspend fun deleteProfile(id: String)

    suspend fun getAllRules(): List<RoutingRule>
    suspend fun saveRule(rule: RoutingRule)
    suspend fun deleteRule(id: String)
    suspend fun updateRulePriorities(ruleIdsInOrder: List<String>)

    suspend fun getSelectedProfileId(): String?
    suspend fun setSelectedProfileId(id: String?)
}

/**
 * Desktop system integration contract.
 * Manages system proxy (WinINET), Windows autostart (Run key), and notifications.
 */
interface SystemIntegration {
    fun setSystemProxy(host: String, port: Int, enabled: Boolean): Boolean
    fun isSystemProxyEnabled(): Boolean
    fun setAutoStart(enabled: Boolean, startMinimized: Boolean): Boolean
    fun isAutoStartEnabled(): Boolean
    fun showNotification(title: String, message: String, isError: Boolean = false)
}
