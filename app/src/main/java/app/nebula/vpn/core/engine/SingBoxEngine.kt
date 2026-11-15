package app.nebula.vpn.core.engine

import android.content.Context
import app.nebula.vpn.core.model.ConnectionState
import app.nebula.vpn.core.model.TrafficStats
import app.nebula.vpn.core.proxy.ProxyCore
import app.nebula.vpn.core.proxy.TunProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SingBoxEngine wraps the existing SagerNet sing-box / libbox core implementation
 * conforming to the unified [TunnelEngine] contract without modifying sing-box's internal logic.
 */
@Singleton
class SingBoxEngine(
    private val proxyCore: ProxyCore,
    private val engineScope: CoroutineScope
) : TunnelEngine {

    @Inject
    constructor(proxyCore: ProxyCore) : this(
        proxyCore = proxyCore,
        engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    )

    @Volatile
    var tunProvider: TunProvider? = null

    override val id: String = EngineType.SING_BOX.id
    override val displayName: String = "sing-box"
    override val capabilities: EngineCapabilities = EngineCapabilities.SING_BOX

    private val _status = MutableStateFlow<EngineStatus>(EngineStatus.Idle)
    override val status: StateFlow<EngineStatus> = _status.asStateFlow()

    override val stats: StateFlow<TrafficStats> = proxyCore.stats

    private val _logStream = MutableSharedFlow<String>(replay = 50)
    override val logStream: Flow<String> = _logStream.asSharedFlow()

    init {
        engineScope.launch {
            proxyCore.state.collect { coreState ->
                _status.value = mapCoreStateToEngineStatus(coreState)
            }
        }
    }

    override suspend fun start(request: TunnelStartRequest) {
        val config = request.configJson ?: throw IllegalArgumentException("SingBoxEngine requires valid configJson")
        val provider = tunProvider ?: throw IllegalStateException("TunProvider must be set before starting SingBoxEngine")
        _status.value = EngineStatus.Preparing
        try {
            proxyCore.start(config, provider)
        } catch (e: Exception) {
            _status.value = EngineStatus.Failed(e.message ?: "Failed to start sing-box core")
            throw e
        }
    }

    override suspend fun stop() {
        try {
            proxyCore.stop()
        } finally {
            _status.value = EngineStatus.Idle
        }
    }

    override suspend fun applySettingsWhileConnected(request: TunnelStartRequest): Boolean {
        val newConfig = request.configJson ?: return false
        return try {
            proxyCore.reloadConfig(newConfig)
            true
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun doctorChecks(): List<DoctorCheckResult> {
        val checks = mutableListOf<DoctorCheckResult>()
        val currentState = _status.value
        checks.add(
            DoctorCheckResult(
                id = "singbox_core_status",
                title = "sing-box Core Binary",
                passed = currentState is EngineStatus.Connected,
                detail = "Status: $currentState",
                fixAction = if (currentState !is EngineStatus.Connected) "Tap Connect to start sing-box tunnel" else null
            )
        )
        return checks
    }

    private fun mapCoreStateToEngineStatus(coreState: ConnectionState): EngineStatus {
        return when (coreState) {
            is ConnectionState.Disconnected -> EngineStatus.Idle
            is ConnectionState.Connecting -> EngineStatus.Preparing
            is ConnectionState.Connected -> {
                if (coreState.isDegraded) {
                    EngineStatus.Connected(summary = "Degraded: ${coreState.warning ?: "No Internet"}")
                } else {
                    EngineStatus.Connected(summary = coreState.profileName)
                }
            }
            is ConnectionState.Reconnecting -> EngineStatus.Reconnecting
            is ConnectionState.Disconnecting -> EngineStatus.Preparing
            is ConnectionState.Failed -> EngineStatus.Failed(reason = coreState.reason)
            is ConnectionState.Error -> EngineStatus.Failed(reason = coreState.message)
        }
    }
}
