package app.nebula.vpn.core.model

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data class Connected(val profileName: String, val connectedAt: Long = System.currentTimeMillis()) : ConnectionState
    data object Reconnecting : ConnectionState
    data object Disconnecting : ConnectionState
    data class Failed(val reason: String, val timestamp: Long = System.currentTimeMillis()) : ConnectionState
    data class Error(val message: String, val timestamp: Long = System.currentTimeMillis()) : ConnectionState

    val isConnected: Boolean get() = this is Connected
}
