package app.nebula.vpn.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nebula.vpn.core.model.AppRoutingMode
import app.nebula.vpn.core.model.Result
import app.nebula.vpn.core.model.ThemeConfig
import app.nebula.vpn.data.repository.ProfileRepository
import app.nebula.vpn.data.repository.RoutingRepository
import app.nebula.vpn.data.repository.SettingsRepository
import app.nebula.vpn.data.repository.ThemeRepository
import app.nebula.vpn.data.repository.VpnRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val profileRepository: ProfileRepository,
    private val routingRepository: RoutingRepository,
    private val themeRepository: ThemeRepository,
    private val vpnRepository: VpnRepository
) : ViewModel() {

    val killSwitchEnabled: StateFlow<Boolean> = settingsRepository.killSwitchEnabled.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false
    )

    val fakeDnsEnabled: StateFlow<Boolean> = settingsRepository.fakeDnsEnabled.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false
    )

    val bypassLanEnabled: StateFlow<Boolean> = settingsRepository.bypassLan.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        true
    )

    val bootAutostartEnabled: StateFlow<Boolean> = settingsRepository.bootAutostartEnabled.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false
    )

    val remoteDnsUrl: StateFlow<String> = settingsRepository.remoteDnsUrl.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        "https://dns.google/dns-query"
    )

    val routingMode: StateFlow<AppRoutingMode> = settingsRepository.routingMode.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        AppRoutingMode.GLOBAL
    )

    val activeTheme: StateFlow<ThemeConfig> = themeRepository.activeTheme.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ThemeRepository.PRESET_EMERALD_CYBER
    )

    val biometricLockEnabled: StateFlow<Boolean> = settingsRepository.biometricLockEnabled.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false
    )

    val flagSecureEnabled: StateFlow<Boolean> = settingsRepository.flagSecureEnabled.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false
    )

    val currentLanguageCode: StateFlow<String> = settingsRepository.languageCode.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        "en"
    )

    val pingMode: StateFlow<String> = settingsRepository.pingMode.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        "TCP"
    )

    fun setPingMode(mode: String) {
        viewModelScope.launch {
            settingsRepository.setPingMode(mode)
        }
    }

    fun setKillSwitch(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setKillSwitchEnabled(enabled)
        }
    }

    fun setFakeDns(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setFakeDnsEnabled(enabled)
        }
    }

    fun setBypassLan(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setBypassLan(enabled)
        }
    }

    fun setBootAutostart(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setBootAutostartEnabled(enabled)
        }
    }

    fun setRemoteDns(url: String) {
        viewModelScope.launch {
            settingsRepository.setRemoteDnsUrl(url)
        }
    }

    fun setBiometricLock(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setBiometricLockEnabled(enabled)
        }
    }

    fun setFlagSecure(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setFlagSecureEnabled(enabled)
        }
    }

    fun changeLanguage(langCode: String) {
        viewModelScope.launch {
            settingsRepository.setLanguageCode(langCode)
        }
    }

    fun panicWipe() {
        viewModelScope.launch {
            vpnRepository.stopVpn()
            profileRepository.deleteAll()
            settingsRepository.clearAll()
        }
    }
}
