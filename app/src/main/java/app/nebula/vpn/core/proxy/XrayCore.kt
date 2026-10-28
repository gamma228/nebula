package app.nebula.vpn.core.proxy

import app.nebula.vpn.core.model.ConnectionState
import app.nebula.vpn.core.model.TrafficStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * XrayCore: ProxyCore implementation for Xray-core via AndroidLibXrayLite and hev-socks5-tunnel.
 *
 * NOTE (Milestone M1 / Section E1):
 * Pinned source repositories, build steps, and commit tags are documented in `scripts/build-cores.sh`.
 * Per Section E1 specification:
 * "If you lack network/tools, write the script and docs, use a clearly marked interface-only stub in tests,
 * and write 'REAL ENGINE NOT INTEGRATED' in PROGRESS.md; the app must refuse to open a tunnel without
 * a working engine, never blackhole traffic."
 *
 * Current Status: REAL ENGINE NOT INTEGRATED for Xray-core. Sing-box v1.14.4 is the verified active real engine.
 */
class XrayCore : ProxyCore {

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _stats = MutableStateFlow(TrafficStats())
    override val stats: StateFlow<TrafficStats> = _stats.asStateFlow()

    override suspend fun start(configJson: String, tunProvider: TunProvider) {
        val message = "REAL ENGINE NOT INTEGRATED: Xray-core native library is not bundled in this build. " +
                "Refusing to open tunnel to avoid blackholing traffic. Please select sing-box engine."
        _state.value = ConnectionState.Failed(message)
        throw IllegalStateException(message)
    }

    override suspend fun stop() {
        _state.value = ConnectionState.Disconnected
        _stats.value = TrafficStats()
    }

    override suspend fun urlTest(groupTag: String) {
        // No-op while engine is unbundled
    }

    override suspend fun selectOutbound(groupTag: String, outboundTag: String): Boolean {
        return false
    }

    override suspend fun reloadConfig(configJson: String) {
        throw IllegalStateException("REAL ENGINE NOT INTEGRATED: Xray-core is not running.")
    }
}
