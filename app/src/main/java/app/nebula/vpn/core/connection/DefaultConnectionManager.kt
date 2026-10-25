package app.nebula.vpn.core.connection

import app.nebula.vpn.core.model.ConnectionState
import app.nebula.vpn.core.model.TrafficStats
import app.nebula.vpn.core.proxy.ProxyCore
import app.nebula.vpn.data.repository.ProfileRepository
import app.nebula.vpn.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultConnectionManager @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val settingsRepository: SettingsRepository,
    private val proxyCore: ProxyCore,
    private val serviceController: VpnServiceController
) : ConnectionManager {

    private var dispatcher: CoroutineDispatcher = Dispatchers.IO
    private var scope: CoroutineScope = CoroutineScope(kotlinx.coroutines.SupervisorJob() + dispatcher)

    constructor(
        profileRepository: ProfileRepository,
        settingsRepository: SettingsRepository,
        proxyCore: ProxyCore,
        serviceController: VpnServiceController,
        dispatcher: CoroutineDispatcher,
        scope: CoroutineScope
    ) : this(profileRepository, settingsRepository, proxyCore, serviceController) {
        this.dispatcher = dispatcher
        this.scope = scope
    }

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _stats = MutableStateFlow(TrafficStats())
    override val stats: StateFlow<TrafficStats> = _stats.asStateFlow()

    @Volatile
    private var _userRequestedDisconnect = false
    override val isUserRequestedDisconnect: Boolean get() = _userRequestedDisconnect

    private val commandMutex = Mutex()
    private var activeJob: Job? = null
    private var activeProfileId: String? = null
    private var connectionStartTime: Long = 0L

    override suspend fun connect(profileId: String?) {
        commandMutex.withLock {
            _userRequestedDisconnect = false
            val current = _state.value
            val targetId = profileId ?: settingsRepository.selectedProfileId.first()

            if (current is ConnectionState.Connected && targetId != null && targetId == activeProfileId) {
                // Idempotent: already connected to this target
                return
            }
            if (current is ConnectionState.Connected && targetId != null) {
                // Switch server
                switchServerLocked(targetId)
                return
            }

            activeJob?.cancel()
            _state.value = ConnectionState.Connecting

            activeJob = scope.launch(dispatcher) {
                try {
                    val profile = if (targetId != null) {
                        profileRepository.getProfileById(targetId)
                    } else {
                        profileRepository.allProfiles.first().firstOrNull()
                    }

                    if (profile == null) {
                        _state.value = ConnectionState.Failed("No profile available")
                        return@launch
                    }

                    activeProfileId = profile.id
                    settingsRepository.setSelectedProfileId(profile.id)
                    serviceController.startVpnService(profile.id)
                } catch (e: Exception) {
                    if (!_userRequestedDisconnect) {
                        _state.value = ConnectionState.Failed(e.message ?: "Connect failed")
                    }
                }
            }
        }
    }

    override suspend fun disconnect() {
        commandMutex.withLock {
            _userRequestedDisconnect = true
            // Cancel any in-flight connect or switch immediately
            activeJob?.cancel()
            activeJob = null

            _state.value = ConnectionState.Disconnecting

            // Give stopVpnService up to 3000ms, then force termination
            withTimeoutOrNull(3000L) {
                serviceController.stopVpnService(3000L)
            }

            activeProfileId = null
            connectionStartTime = 0L
            _state.value = ConnectionState.Disconnected
            _stats.value = TrafficStats()
        }
    }

    override suspend fun switchServer(profileId: String) {
        commandMutex.withLock {
            switchServerLocked(profileId)
        }
    }

    private suspend fun switchServerLocked(profileId: String) {
        if (profileId == activeProfileId && _state.value is ConnectionState.Connected) {
            return
        }
        val targetProfile = profileRepository.getProfileById(profileId) ?: return
        activeProfileId = targetProfile.id
        settingsRepository.setSelectedProfileId(targetProfile.id)

        try {
            serviceController.switchVpnService(profileId)
            val startT = if (connectionStartTime > 0L) connectionStartTime else System.currentTimeMillis()
            _state.value = ConnectionState.Connected(targetProfile.name, startT)
        } catch (e: Exception) {
            if (!_userRequestedDisconnect) {
                _state.value = ConnectionState.Failed(e.message ?: "Switch failed")
            }
        }
    }

    override suspend fun reconnect() {
        val lastId = activeProfileId
        disconnect()
        connect(lastId)
    }

    override fun onServiceConnected(profileName: String) {
        if (!_userRequestedDisconnect) {
            if (connectionStartTime == 0L) {
                connectionStartTime = System.currentTimeMillis()
            }
            _state.value = ConnectionState.Connected(profileName, connectionStartTime)
        }
    }

    override fun onServiceError(message: String) {
        if (!_userRequestedDisconnect) {
            _state.value = ConnectionState.Failed(message)
        }
    }

    override fun onServiceDisconnected() {
        _state.value = ConnectionState.Disconnected
        _stats.value = TrafficStats()
        connectionStartTime = 0L
    }

    override fun updateStats(stats: TrafficStats) {
        _stats.value = stats
    }

    override fun onVpnRevoked() {
        _userRequestedDisconnect = true
        _state.value = ConnectionState.Disconnected
        scope.launch(dispatcher) {
            serviceController.stopVpnService(1000L)
        }
    }
}
