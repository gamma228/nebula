package app.nebula.vpn.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nebula.vpn.core.model.ConnectionState
import app.nebula.vpn.core.model.ProxyProfile
import app.nebula.vpn.core.model.ThemeConfig
import app.nebula.vpn.core.model.TrafficStats
import app.nebula.vpn.data.repository.ProfileRepository
import app.nebula.vpn.data.repository.SettingsRepository
import app.nebula.vpn.data.repository.ThemeRepository
import app.nebula.vpn.data.repository.VpnRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val vpnRepository: VpnRepository,
    private val profileRepository: ProfileRepository,
    private val settingsRepository: SettingsRepository,
    private val themeRepository: ThemeRepository
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = vpnRepository.connectionState
    val trafficStats: StateFlow<TrafficStats> = vpnRepository.trafficStats
    val activeTheme: StateFlow<ThemeConfig> = themeRepository.activeTheme.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ThemeRepository.PRESET_OLED_CRIMSON
    )

    private val _selectedProfile = MutableStateFlow<ProxyProfile?>(null)
    val selectedProfile: StateFlow<ProxyProfile?> = _selectedProfile.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.selectedProfileId.collect { id ->
                if (id != null) {
                    _selectedProfile.value = profileRepository.getProfileById(id)
                } else {
                    val all = profileRepository.allProfiles.first()
                    _selectedProfile.value = all.firstOrNull()
                }
            }
        }
    }

    fun toggleConnection() {
        val state = connectionState.value
        if (state is ConnectionState.Connected || state is ConnectionState.Connecting) {
            vpnRepository.stopVpn()
        } else {
            val profile = _selectedProfile.value
            if (profile != null) {
                vpnRepository.startVpn(profile.id)
            } else {
                viewModelScope.launch {
                    val first = profileRepository.allProfiles.first().firstOrNull()
                    if (first != null) {
                        _selectedProfile.value = first
                        settingsRepository.setSelectedProfileId(first.id)
                        vpnRepository.startVpn(first.id)
                    }
                }
            }
        }
    }
}
