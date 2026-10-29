package app.nebula.vpn.core.proxy

import app.nebula.vpn.core.model.ConnectionState
import app.nebula.vpn.core.model.TrafficStats
import kotlinx.coroutines.flow.StateFlow

interface ProxyCore {
    val state: StateFlow<ConnectionState>
    val stats: StateFlow<TrafficStats>

    suspend fun start(configJson: String, tunProvider: TunProvider)
    suspend fun stop()
    suspend fun urlTest(groupTag: String)
    suspend fun selectOutbound(groupTag: String, outboundTag: String): Boolean
    suspend fun reloadConfig(configJson: String)
    fun setScreenInteractive(isInteractive: Boolean)
}
