package app.nebula.vpn.vpn

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import app.nebula.vpn.core.model.ConnectionState
import app.nebula.vpn.data.repository.VpnRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@RequiresApi(Build.VERSION_CODES.N)
@AndroidEntryPoint
class VpnTileService : TileService() {

    @Inject lateinit var vpnRepository: VpnRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var listeningJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        listeningJob?.cancel()
        listeningJob = scope.launch {
            vpnRepository.connectionState.collect { state ->
                val tile = qsTile ?: return@collect
                when (state) {
                    is ConnectionState.Connected -> {
                        tile.state = Tile.STATE_ACTIVE
                        tile.label = state.profileName
                    }
                    is ConnectionState.Connecting -> {
                        tile.state = Tile.STATE_UNAVAILABLE
                        tile.label = "Connecting…"
                    }
                    else -> {
                        tile.state = Tile.STATE_INACTIVE
                        tile.label = "Nebula"
                    }
                }
                tile.updateTile()
            }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        listeningJob?.cancel()
        listeningJob = null
    }

    override fun onClick() {
        super.onClick()
        val currentState = vpnRepository.connectionState.value
        if (currentState is ConnectionState.Connected || currentState is ConnectionState.Connecting) {
            vpnRepository.stopVpn()
        } else {
            vpnRepository.startVpn("")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
