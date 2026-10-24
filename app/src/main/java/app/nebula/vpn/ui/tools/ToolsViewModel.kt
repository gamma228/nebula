package app.nebula.vpn.ui.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nebula.vpn.core.model.ProxyProfile
import app.nebula.vpn.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject

data class ConnectionItem(
    val id: String,
    val destination: String,
    val protocol: String,
    val outbound: String,
    val uptimeSeconds: Long
)

@HiltViewModel
class ToolsViewModel @Inject constructor(
    private val profileRepository: ProfileRepository
) : ViewModel() {

    val profiles: StateFlow<List<ProxyProfile>> = profileRepository.allProfiles.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    private val _logs = MutableStateFlow<List<String>>(listOf(
        "[INFO] sing-box core initialized",
        "[INFO] DNS servers configured",
        "[INFO] TUN interface ready"
    ))
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _activeConnections = MutableStateFlow<List<ConnectionItem>>(emptyList())
    val activeConnections: StateFlow<List<ConnectionItem>> = _activeConnections.asStateFlow()

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun batchPingAll() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = profiles.value
            for (p in list) {
                val start = System.currentTimeMillis()
                try {
                    val socket = Socket()
                    socket.connect(InetSocketAddress(p.server, p.port), 2000)
                    socket.close()
                    val latency = System.currentTimeMillis() - start
                    profileRepository.updatePing(p.id, latency)
                } catch (e: Exception) {
                    profileRepository.updatePing(p.id, -1)
                }
            }
        }
    }
}
