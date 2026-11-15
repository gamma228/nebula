package app.nebula.vpn.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.nebula.vpn.core.model.AppRoutingMode
import app.nebula.vpn.core.engine.EngineType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "nebula_settings")

class SettingsDataStore(private val context: Context) {

    companion object {
        private val KEY_SELECTED_PROFILE_ID = stringPreferencesKey("selected_profile_id")
        private val KEY_ROUTING_MODE = stringPreferencesKey("routing_mode")
        private val KEY_TARGET_PACKAGES = stringSetPreferencesKey("target_packages")
        private val KEY_KILL_SWITCH = booleanPreferencesKey("kill_switch")
        private val KEY_REMOTE_DNS = stringPreferencesKey("remote_dns")
        private val KEY_FAKE_DNS = booleanPreferencesKey("fake_dns")
        private val KEY_BYPASS_LAN = booleanPreferencesKey("bypass_lan")
        private val KEY_LOCAL_PORT = intPreferencesKey("local_port")
        private val KEY_LANGUAGE = stringPreferencesKey("language_code")
        private val KEY_BIOMETRIC_LOCK = booleanPreferencesKey("biometric_lock")
        private val KEY_FLAG_SECURE = booleanPreferencesKey("flag_secure")
        private val KEY_THEME_CONFIG = stringPreferencesKey("theme_config")
        private val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        private val KEY_BOOT_AUTOSTART = booleanPreferencesKey("boot_autostart")
        private val KEY_PING_MODE = stringPreferencesKey("ping_mode")
        private val KEY_IPV6_ENABLED = booleanPreferencesKey("ipv6_enabled")
        private val KEY_BLOCK_QUIC = booleanPreferencesKey("block_quic")
        private val KEY_POWER_PROFILE = stringPreferencesKey("power_profile")
        private val KEY_SHOW_SPEED_IN_NOTIF = booleanPreferencesKey("show_speed_in_notif")
        private val KEY_ACTIVE_ENGINE = stringPreferencesKey("active_engine")
        private val KEY_TOR_BRIDGE_TYPE = stringPreferencesKey("tor_bridge_type")
        private val KEY_TOR_CUSTOM_BRIDGES = stringPreferencesKey("tor_custom_bridges")
        private val KEY_TOR_CARRIER_ENABLED = booleanPreferencesKey("tor_carrier_enabled")
        private val KEY_TOR_CARRIER_PROFILE_ID = stringPreferencesKey("tor_carrier_profile_id")
    }

    val selectedProfileId: Flow<String?> = context.dataStore.data.map { it[KEY_SELECTED_PROFILE_ID] }
    suspend fun setSelectedProfileId(id: String?) {
        context.dataStore.edit {
            if (id != null) it[KEY_SELECTED_PROFILE_ID] = id else it.remove(KEY_SELECTED_PROFILE_ID)
        }
    }

    val routingMode: Flow<AppRoutingMode> = context.dataStore.data.map {
        when (it[KEY_ROUTING_MODE]) {
            AppRoutingMode.FORWARD_APPS.name -> AppRoutingMode.FORWARD_APPS
            AppRoutingMode.BYPASS_APPS.name -> AppRoutingMode.BYPASS_APPS
            else -> AppRoutingMode.GLOBAL
        }
    }
    suspend fun setRoutingMode(mode: AppRoutingMode) {
        context.dataStore.edit { it[KEY_ROUTING_MODE] = mode.name }
    }

    val targetPackages: Flow<Set<String>> = context.dataStore.data.map { it[KEY_TARGET_PACKAGES] ?: emptySet() }
    suspend fun setTargetPackages(packages: Set<String>) {
        context.dataStore.edit { it[KEY_TARGET_PACKAGES] = packages }
    }

    val killSwitchEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_KILL_SWITCH] ?: false }
    suspend fun setKillSwitchEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_KILL_SWITCH] = enabled }
    }

    val remoteDnsUrl: Flow<String> = context.dataStore.data.map { it[KEY_REMOTE_DNS] ?: "https://1.1.1.1/dns-query" }
    suspend fun setRemoteDnsUrl(url: String) {
        context.dataStore.edit { it[KEY_REMOTE_DNS] = url }
    }

    val fakeDnsEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_FAKE_DNS] ?: true }
    suspend fun setFakeDnsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_FAKE_DNS] = enabled }
    }

    val bypassLan: Flow<Boolean> = context.dataStore.data.map { it[KEY_BYPASS_LAN] ?: true }
    suspend fun setBypassLan(enabled: Boolean) {
        context.dataStore.edit { it[KEY_BYPASS_LAN] = enabled }
    }

    val localPortMixedInbound: Flow<Int?> = context.dataStore.data.map { it[KEY_LOCAL_PORT] }
    suspend fun setLocalPortMixedInbound(port: Int?) {
        context.dataStore.edit {
            if (port != null && port > 0) it[KEY_LOCAL_PORT] = port else it.remove(KEY_LOCAL_PORT)
        }
    }

    val languageCode: Flow<String> = context.dataStore.data.map { it[KEY_LANGUAGE] ?: "en" }
    suspend fun setLanguageCode(code: String) {
        context.dataStore.edit { it[KEY_LANGUAGE] = code }
    }

    val biometricLockEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_BIOMETRIC_LOCK] ?: false }
    suspend fun setBiometricLockEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_BIOMETRIC_LOCK] = enabled }
    }

    val flagSecureEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_FLAG_SECURE] ?: false }
    suspend fun setFlagSecureEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_FLAG_SECURE] = enabled }
    }

    val themeConfigJson: Flow<String?> = context.dataStore.data.map { it[KEY_THEME_CONFIG] }
    suspend fun setThemeConfigJson(json: String) {
        context.dataStore.edit { it[KEY_THEME_CONFIG] = json }
    }

    val onboardingDone: Flow<Boolean> = context.dataStore.data.map { it[KEY_ONBOARDING_DONE] ?: false }
    suspend fun setOnboardingDone(done: Boolean) {
        context.dataStore.edit { it[KEY_ONBOARDING_DONE] = done }
    }

    val bootAutostartEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_BOOT_AUTOSTART] ?: false }
    suspend fun setBootAutostartEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_BOOT_AUTOSTART] = enabled }
    }

    val pingMode: Flow<String> = context.dataStore.data.map { it[KEY_PING_MODE] ?: "TCP" }
    suspend fun setPingMode(mode: String) {
        context.dataStore.edit { it[KEY_PING_MODE] = mode }
    }

    val ipv6Enabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_IPV6_ENABLED] ?: false }
    suspend fun setIpv6Enabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_IPV6_ENABLED] = enabled }
    }

    val blockQuic: Flow<Boolean> = context.dataStore.data.map { it[KEY_BLOCK_QUIC] ?: true }
    suspend fun setBlockQuic(enabled: Boolean) {
        context.dataStore.edit { it[KEY_BLOCK_QUIC] = enabled }
    }

    val powerProfile: Flow<String> = context.dataStore.data.map { it[KEY_POWER_PROFILE] ?: "BALANCED" }
    suspend fun setPowerProfile(profile: String) {
        context.dataStore.edit { it[KEY_POWER_PROFILE] = profile }
    }

    val showSpeedInNotification: Flow<Boolean> = context.dataStore.data.map { it[KEY_SHOW_SPEED_IN_NOTIF] ?: true }
    suspend fun setShowSpeedInNotification(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SHOW_SPEED_IN_NOTIF] = enabled }
    }

    val activeEngine: Flow<EngineType> = context.dataStore.data.map {
        val raw = it[KEY_ACTIVE_ENGINE] ?: EngineType.SING_BOX.name
        try {
            EngineType.valueOf(raw)
        } catch (e: Exception) {
            EngineType.SING_BOX
        }
    }
    suspend fun setActiveEngine(engine: EngineType) {
        context.dataStore.edit { it[KEY_ACTIVE_ENGINE] = engine.name }
    }

    val torBridgeType: Flow<String> = context.dataStore.data.map { it[KEY_TOR_BRIDGE_TYPE] ?: "vanilla" }
    suspend fun setTorBridgeType(type: String) {
        context.dataStore.edit { it[KEY_TOR_BRIDGE_TYPE] = type }
    }

    val torCustomBridges: Flow<String> = context.dataStore.data.map { it[KEY_TOR_CUSTOM_BRIDGES] ?: "" }
    suspend fun setTorCustomBridges(bridges: String) {
        context.dataStore.edit { it[KEY_TOR_CUSTOM_BRIDGES] = bridges }
    }

    val torCarrierEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_TOR_CARRIER_ENABLED] ?: false }
    suspend fun setTorCarrierEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_TOR_CARRIER_ENABLED] = enabled }
    }

    val torCarrierProfileId: Flow<String?> = context.dataStore.data.map { it[KEY_TOR_CARRIER_PROFILE_ID] }
    suspend fun setTorCarrierProfileId(id: String?) {
        context.dataStore.edit {
            if (id != null) it[KEY_TOR_CARRIER_PROFILE_ID] = id else it.remove(KEY_TOR_CARRIER_PROFILE_ID)
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}
