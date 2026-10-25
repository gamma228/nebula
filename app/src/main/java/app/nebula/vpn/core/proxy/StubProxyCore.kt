package app.nebula.vpn.core.proxy

import app.nebula.vpn.core.model.ConnectionState
import app.nebula.vpn.core.model.TrafficStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class StubProxyCore : ProxyCore {

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _stats = MutableStateFlow(TrafficStats())
    override val stats: StateFlow<TrafficStats> = _stats.asStateFlow()

    override suspend fun start(configJson: String, tunProvider: TunProvider) {
        _state.value = ConnectionState.Connecting
        // Simulate successful connection for tests
        _state.value = ConnectionState.Connected("Stub-Profile")
    }

    override suspend fun stop() {
        _state.value = ConnectionState.Disconnecting
        _state.value = ConnectionState.Disconnected
        _stats.value = TrafficStats()
    }

    override suspend fun urlTest(groupTag: String) {
        // No-op for stub
    }

    override suspend fun selectOutbound(groupTag: String, outboundTag: String): Boolean {
        return true
    }

    override suspend fun reloadConfig(configJson: String) {
        // No-op for stub
    }
}
