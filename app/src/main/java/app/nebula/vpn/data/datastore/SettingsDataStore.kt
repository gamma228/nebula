package app.nebula.vpn.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.nebula.vpn.core.model.AppRoutingMode
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

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}
