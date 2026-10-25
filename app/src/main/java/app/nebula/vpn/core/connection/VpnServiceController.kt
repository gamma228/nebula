package app.nebula.vpn.core.connection

interface VpnServiceController {
    suspend fun startVpnService(profileId: String?)
    suspend fun switchVpnService(profileId: String)
    suspend fun stopVpnService(timeoutMs: Long)
}
