package app.nebula.vpn.core.engine

import app.nebula.vpn.core.model.TrafficStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Common abstraction representing an underlying VPN tunnel engine.
 *
 * Implemented by:
 * - SingBoxEngine: Wraps official SagerNet sing-box libbox / sing-box.exe core.
 * - TorEngine: Embedded Tor Arti + Onionmasq user-space networking stack on Android.
 */
interface TunnelEngine {
    val id: String
    val displayName: String
    val capabilities: EngineCapabilities
    val status: StateFlow<EngineStatus>
    val stats: StateFlow<TrafficStats>
    val logStream: Flow<String>

    suspend fun start(request: TunnelStartRequest)
    suspend fun stop()
    suspend fun doctorChecks(): List<DoctorCheckResult>
    suspend fun applySettingsWhileConnected(request: TunnelStartRequest): Boolean = false
    suspend fun newIdentity() {}
}

/**
 * Hardware and functional capabilities advertised by a tunnel engine.
 * The UI layer queries these capabilities to automatically adapt views without
 * scattered engine-type checks.
 */
data class EngineCapabilities(
    val supportsServerList: Boolean,
    val supportsSubscriptions: Boolean,
    val supportsCustomRoutingRules: Boolean,
    val supportsPerAppSelection: Boolean,
    val supportsUdp: Boolean,
    val supportsLocalStats: Boolean,
    val supportsBridges: Boolean,
    val supportsNewIdentity: Boolean,
    val supportsLanAccess: Boolean,
    val supportsCarrier: Boolean,
    val supportedOnThisPlatform: Boolean
) {
    companion object {
        val SING_BOX = EngineCapabilities(
            supportsServerList = true,
            supportsSubscriptions = true,
            supportsCustomRoutingRules = true,
            supportsPerAppSelection = true,
            supportsUdp = true,
            supportsLocalStats = true,
            supportsBridges = false,
            supportsNewIdentity = false,
            supportsLanAccess = true,
            supportsCarrier = false,
            supportedOnThisPlatform = true
        )

        val TOR_ANDROID = EngineCapabilities(
            supportsServerList = false,
            supportsSubscriptions = false,
            supportsCustomRoutingRules = false,
            supportsPerAppSelection = true,
            supportsUdp = false, // Drop all non-DNS UDP (including QUIC) by design
            supportsLocalStats = true,
            supportsBridges = true,
            supportsNewIdentity = true,
            supportsLanAccess = true,
            supportsCarrier = true,
            supportedOnThisPlatform = true
        )

        val TOR_UNSUPPORTED = EngineCapabilities(
            supportsServerList = false,
            supportsSubscriptions = false,
            supportsCustomRoutingRules = false,
            supportsPerAppSelection = false,
            supportsUdp = false,
            supportsLocalStats = false,
            supportsBridges = false,
            supportsNewIdentity = false,
            supportsLanAccess = false,
            supportsCarrier = false,
            supportedOnThisPlatform = false
        )
    }
}

/**
 * State machine representing the lifecycle of an active or transitioning tunnel engine.
 */
sealed class EngineStatus {
    object Idle : EngineStatus() {
        override fun toString(): String = "Idle"
    }
    object Preparing : EngineStatus() {
        override fun toString(): String = "Preparing"
    }
    data class Bootstrapping(val percent: Int, val summary: String? = null) : EngineStatus()
    data class Connected(val summary: String? = null) : EngineStatus()
    object Reconnecting : EngineStatus() {
        override fun toString(): String = "Reconnecting"
    }
    data class Failed(val reason: String) : EngineStatus()
    data class Blocking(val reason: String? = null) : EngineStatus()
}

/**
 * Request payload provided to [TunnelEngine.start].
 */
data class TunnelStartRequest(
    val profileId: String? = null,
    val configJson: String? = null,
    val tunFd: Int? = null,
    val bridgeLines: String? = null,
    val carrierConfig: CarrierConfig? = null,
    val excludedUids: List<Long> = emptyList(),
    val isolatedApps: List<String> = emptyList(),
    val allowLan: Boolean = false
)

/**
 * Configuration for "Tor via my server" carrier mode.
 */
data class CarrierConfig(
    val enabled: Boolean = false,
    val carrierProfileId: String? = null,
    val socksPort: Int = 0,
    val socksHost: String = "127.0.0.1",
    val authUsername: String? = null,
    val authPassword: String? = null
)

/**
 * Diagnostic result model for Connection Doctor checks.
 */
data class DoctorCheckResult(
    val id: String,
    val title: String,
    val passed: Boolean,
    val detail: String,
    val fixAction: String? = null
)
