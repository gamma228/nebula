package app.nebula.vpn.core.connection

import app.nebula.vpn.core.model.ConnectionState
import app.nebula.vpn.core.model.TrafficStats
import kotlinx.coroutines.flow.StateFlow

interface ConnectionManager {
    val state: StateFlow<ConnectionState>
    val stats: StateFlow<TrafficStats>
    val isUserRequestedDisconnect: Boolean

    suspend fun connect(profileId: String? = null)
    suspend fun disconnect()
    suspend fun switchServer(profileId: String)
    suspend fun reconnect()

    fun onServiceConnected(profileName: String, isDegraded: Boolean = false, warning: String? = null)
    fun onServiceError(message: String)
    fun onServiceDisconnected()
    fun updateStats(stats: TrafficStats)
    fun onVpnRevoked()
}
