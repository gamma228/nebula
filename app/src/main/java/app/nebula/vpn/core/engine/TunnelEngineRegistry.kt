package app.nebula.vpn.core.engine

import app.nebula.vpn.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TunnelEngineRegistry(
    private val singBoxEngine: SingBoxEngine,
    private val torEngine: TorEngine,
    private val settingsRepository: SettingsRepository,
    registryScope: CoroutineScope
) {
    @Inject
    constructor(
        singBoxEngine: SingBoxEngine,
        torEngine: TorEngine,
        settingsRepository: SettingsRepository
    ) : this(
        singBoxEngine = singBoxEngine,
        torEngine = torEngine,
        settingsRepository = settingsRepository,
        registryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    )

    val activeEngineType: StateFlow<EngineType> = settingsRepository.activeEngine
        .stateIn(registryScope, SharingStarted.Eagerly, EngineType.SING_BOX)

    val activeEngine: StateFlow<TunnelEngine> = activeEngineType
        .map { getEngine(it) }
        .stateIn(registryScope, SharingStarted.Eagerly, singBoxEngine)

    val activeCapabilities: StateFlow<EngineCapabilities> = activeEngine
        .map { it.capabilities }
        .stateIn(registryScope, SharingStarted.Eagerly, EngineCapabilities.SING_BOX)

    fun getEngine(type: EngineType): TunnelEngine {
        return when (type) {
            EngineType.SING_BOX -> singBoxEngine
            EngineType.TOR -> torEngine
        }
    }
}
