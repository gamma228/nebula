package app.nebula.vpn.data.repository

import app.nebula.vpn.core.engine.EngineType
import app.nebula.vpn.core.model.AppRoutingMode
import app.nebula.vpn.data.datastore.SettingsDataStore
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: SettingsDataStore
) {
    val selectedProfileId: Flow<String?> = dataStore.selectedProfileId
    val routingMode: Flow<AppRoutingMode> = dataStore.routingMode
    val targetPackages: Flow<Set<String>> = dataStore.targetPackages
    val killSwitchEnabled: Flow<Boolean> = dataStore.killSwitchEnabled
    val remoteDnsUrl: Flow<String> = dataStore.remoteDnsUrl
    val fakeDnsEnabled: Flow<Boolean> = dataStore.fakeDnsEnabled
    val bypassLan: Flow<Boolean> = dataStore.bypassLan
    val languageCode: Flow<String> = dataStore.languageCode
    val biometricLockEnabled: Flow<Boolean> = dataStore.biometricLockEnabled
    val flagSecureEnabled: Flow<Boolean> = dataStore.flagSecureEnabled
    val themeConfigJson: Flow<String?> = dataStore.themeConfigJson
    val onboardingDone: Flow<Boolean> = dataStore.onboardingDone
    val bootAutostartEnabled: Flow<Boolean> = dataStore.bootAutostartEnabled
    val pingMode: Flow<String> = dataStore.pingMode
    val ipv6Enabled: Flow<Boolean> = dataStore.ipv6Enabled
    val blockQuic: Flow<Boolean> = dataStore.blockQuic
    val powerProfile: Flow<String> = dataStore.powerProfile
    val showSpeedInNotification: Flow<Boolean> = dataStore.showSpeedInNotification
    val activeEngine: Flow<EngineType> = dataStore.activeEngine
    val torBridgeType: Flow<String> = dataStore.torBridgeType
    val torCustomBridges: Flow<String> = dataStore.torCustomBridges
    val torCarrierEnabled: Flow<Boolean> = dataStore.torCarrierEnabled
    val torCarrierProfileId: Flow<String?> = dataStore.torCarrierProfileId

    suspend fun setSelectedProfileId(id: String?) = dataStore.setSelectedProfileId(id)
    suspend fun setRoutingMode(mode: AppRoutingMode) = dataStore.setRoutingMode(mode)
    suspend fun setTargetPackages(packages: Set<String>) = dataStore.setTargetPackages(packages)
    suspend fun setKillSwitchEnabled(enabled: Boolean) = dataStore.setKillSwitchEnabled(enabled)
    suspend fun setRemoteDnsUrl(url: String) = dataStore.setRemoteDnsUrl(url)
    suspend fun setFakeDnsEnabled(enabled: Boolean) = dataStore.setFakeDnsEnabled(enabled)
    suspend fun setBypassLan(enabled: Boolean) = dataStore.setBypassLan(enabled)
    suspend fun setLanguageCode(code: String) = dataStore.setLanguageCode(code)
    suspend fun setBiometricLockEnabled(enabled: Boolean) = dataStore.setBiometricLockEnabled(enabled)
    suspend fun setFlagSecureEnabled(enabled: Boolean) = dataStore.setFlagSecureEnabled(enabled)
    suspend fun setThemeConfigJson(json: String) = dataStore.setThemeConfigJson(json)
    suspend fun setOnboardingDone(done: Boolean) = dataStore.setOnboardingDone(done)
    suspend fun setBootAutostartEnabled(enabled: Boolean) = dataStore.setBootAutostartEnabled(enabled)
    suspend fun setPingMode(mode: String) = dataStore.setPingMode(mode)
    suspend fun setIpv6Enabled(enabled: Boolean) = dataStore.setIpv6Enabled(enabled)
    suspend fun setBlockQuic(enabled: Boolean) = dataStore.setBlockQuic(enabled)
    suspend fun setPowerProfile(profile: String) = dataStore.setPowerProfile(profile)
    suspend fun setShowSpeedInNotification(enabled: Boolean) = dataStore.setShowSpeedInNotification(enabled)
    suspend fun setActiveEngine(engine: EngineType) = dataStore.setActiveEngine(engine)
    suspend fun setTorBridgeType(type: String) = dataStore.setTorBridgeType(type)
    suspend fun setTorCustomBridges(bridges: String) = dataStore.setTorCustomBridges(bridges)
    suspend fun setTorCarrierEnabled(enabled: Boolean) = dataStore.setTorCarrierEnabled(enabled)
    suspend fun setTorCarrierProfileId(id: String?) = dataStore.setTorCarrierProfileId(id)
    suspend fun clearAll() = dataStore.clearAll()
}
