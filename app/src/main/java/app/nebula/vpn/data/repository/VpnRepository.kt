package app.nebula.vpn.data.repository

import app.nebula.vpn.core.connection.ConnectionManager
import app.nebula.vpn.core.model.ConnectionState
import app.nebula.vpn.core.model.TrafficStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VpnRepository @Inject constructor(
    private val connectionManager: ConnectionManager
) {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val connectionState: StateFlow<ConnectionState> = connectionManager.state
    val trafficStats: StateFlow<TrafficStats> = connectionManager.stats
    val isUserRequestedDisconnect: Boolean get() = connectionManager.isUserRequestedDisconnect

    fun updateState(state: ConnectionState) {
        android.util.Log.i("VpnRepository", "updateState -> $state")
        when (state) {
            is ConnectionState.Connected -> connectionManager.onServiceConnected(state.profileName, state.isDegraded, state.warning)
            is ConnectionState.Failed -> connectionManager.onServiceError(state.reason)
            is ConnectionState.Error -> connectionManager.onServiceError(state.message)
            is ConnectionState.Disconnected -> connectionManager.onServiceDisconnected()
            else -> {}
        }
    }

    fun updateStats(stats: TrafficStats) {
        connectionManager.updateStats(stats)
    }

    fun startVpn(profileId: String) {
        repositoryScope.launch {
            connectionManager.connect(profileId.ifEmpty { null })
        }
    }

    fun switchProfile(profileId: String) {
        repositoryScope.launch {
            connectionManager.switchServer(profileId)
        }
    }

    fun stopVpn() {
        repositoryScope.launch {
            connectionManager.disconnect()
        }
    }

    fun onVpnRevoked() {
        connectionManager.onVpnRevoked()
    }
}
