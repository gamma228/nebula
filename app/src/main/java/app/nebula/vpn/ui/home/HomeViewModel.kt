package app.nebula.vpn.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nebula.vpn.core.model.ConnectionState
import app.nebula.vpn.core.model.ProxyProfile
import app.nebula.vpn.core.model.ThemeConfig
import app.nebula.vpn.core.model.TrafficStats
import app.nebula.vpn.core.proxy.PingTester
import app.nebula.vpn.data.database.entity.SubscriptionEntity
import app.nebula.vpn.data.repository.ProfileRepository
import app.nebula.vpn.data.repository.SettingsRepository
import app.nebula.vpn.data.repository.SubscriptionRepository
import app.nebula.vpn.data.repository.ThemeRepository
import app.nebula.vpn.data.repository.VpnRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val vpnRepository: VpnRepository,
    private val profileRepository: ProfileRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val settingsRepository: SettingsRepository,
    private val themeRepository: ThemeRepository,
    private val pingTester: PingTester
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = vpnRepository.connectionState
    val trafficStats: StateFlow<TrafficStats> = vpnRepository.trafficStats
    val activeTheme: StateFlow<ThemeConfig> = themeRepository.activeTheme.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ThemeRepository.PRESET_EMERALD_CYBER
    )

    private val _selectedProfile = MutableStateFlow<ProxyProfile?>(null)
    val selectedProfile: StateFlow<ProxyProfile?> = _selectedProfile.asStateFlow()

    val allProfiles: StateFlow<List<ProxyProfile>> = profileRepository.allProfiles.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val activeSubscription: StateFlow<SubscriptionEntity?> = combine(
        _selectedProfile,
        subscriptionRepository.allSubscriptions
    ) { profile, subs ->
        if (profile?.subscriptionId != null) {
            subs.find { it.id == profile.subscriptionId } ?: subs.firstOrNull()
        } else {
            subs.firstOrNull()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _isRefreshingSubscription = MutableStateFlow(false)
    val isRefreshingSubscription: StateFlow<Boolean> = _isRefreshingSubscription.asStateFlow()

    private val _connectionDurationSeconds = MutableStateFlow(0L)
    val connectionDurationSeconds: StateFlow<Long> = _connectionDurationSeconds.asStateFlow()

    private val _downloadHistory = MutableStateFlow<List<Long>>(List(30) { 0L })
    val downloadHistory: StateFlow<List<Long>> = _downloadHistory.asStateFlow()

    private val _uploadHistory = MutableStateFlow<List<Long>>(List(30) { 0L })
    val uploadHistory: StateFlow<List<Long>> = _uploadHistory.asStateFlow()

    private var uiStatsJob: Job? = null
    private var durationTimerJob: Job? = null

    init {
        // Collect selected profile changes
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

    /**
     * Called when the HomeScreen composable becomes active/visible.
     * Starts waveform chart collection and 1s duration timer only while the user is actually looking at the screen.
     */
    fun onHomeScreenVisible() {
        if (uiStatsJob == null) {
            uiStatsJob = viewModelScope.launch {
                trafficStats.collect { stats ->
                    if (connectionState.value is ConnectionState.Connected) {
                        _downloadHistory.value = (_downloadHistory.value.drop(1) + stats.downloadSpeedBps)
                        _uploadHistory.value = (_uploadHistory.value.drop(1) + stats.uploadSpeedBps)
                    } else {
                        _downloadHistory.value = List(30) { 0L }
                        _uploadHistory.value = List(30) { 0L }
                    }
                }
            }
        }
        if (durationTimerJob == null) {
            durationTimerJob = viewModelScope.launch {
                while (isActive) {
                    val state = connectionState.value
                    if (state is ConnectionState.Connected) {
                        val start = state.connectedAt
                        val now = System.currentTimeMillis()
                        _connectionDurationSeconds.value = if (start > 0L) (now - start) / 1000L else 0L
                    } else {
                        _connectionDurationSeconds.value = 0L
                    }
                    delay(1000L)
                }
            }
        }
    }

    /**
     * Called when the HomeScreen is disposed (navigated away or app backgrounded).
     * Immediately terminates UI polling loops to conserve battery and CPU.
     */
    fun onHomeScreenHidden() {
        uiStatsJob?.cancel()
        uiStatsJob = null
        durationTimerJob?.cancel()
        durationTimerJob = null
    }

    fun selectProfile(id: String) {
        viewModelScope.launch {
            settingsRepository.setSelectedProfileId(id)
            val profile = profileRepository.getProfileById(id)
            _selectedProfile.value = profile
            if (connectionState.value is ConnectionState.Connected) {
                vpnRepository.switchProfile(id)
            }
        }
    }

    fun refreshActiveSubscription() {
        val sub = activeSubscription.value ?: return
        viewModelScope.launch {
            _isRefreshingSubscription.value = true
            subscriptionRepository.refreshSubscription(sub.id)
            _isRefreshingSubscription.value = false
        }
    }

    fun pingActiveServer() {
        val profile = _selectedProfile.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val latency = pingTester.pingTcp(profile.server, profile.port)
            profileRepository.updatePing(profile.id, latency)
            val updated = profileRepository.getProfileById(profile.id)
            if (updated != null) {
                _selectedProfile.value = updated
            }
        }
    }

    fun toggleConnection() {
        val state = connectionState.value
        android.util.Log.i("HomeViewModel", "toggleConnection called, current state = $state")
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
