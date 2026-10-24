package app.nebula.vpn.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nebula.vpn.core.crypto.SecretBox
import app.nebula.vpn.core.model.Result
import app.nebula.vpn.data.repository.ProfileRepository
import app.nebula.vpn.data.repository.RoutingRepository
import app.nebula.vpn.data.repository.SettingsRepository
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
            val appLocale = LocaleListCompat.forLanguageTags(langCode)
            AppCompatDelegate.setApplicationLocales(appLocale)
        }
    }

    fun panicWipe() {
        viewModelScope.launch {
            vpnRepository.stopVpn()
            profileRepository.deleteAll()
            settingsRepository.clearAll()
        }
    }

    fun exportEncryptedBackup(password: CharArray): Result<String> {
        return try {
            val profiles = profileRepository.allProfiles
            // Serialize and encrypt with password
            val plain = "{\"profiles_count\": 0}" // Placeholder for backup payload
            val encrypted = SecretBox.encryptWithPassword(plain, password)
            Result.Success(encrypted)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}
