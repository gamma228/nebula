package app.nebula.vpn.core.model

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data class Connected(val profileName: String, val connectedAt: Long = System.currentTimeMillis()) : ConnectionState
    data object Disconnecting : ConnectionState
    data class Error(val message: String, val timestamp: Long = System.currentTimeMillis()) : ConnectionState
}
