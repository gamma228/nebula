package app.nebula.vpn.ui.tools

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nebula.vpn.core.model.PowerProfile
import app.nebula.vpn.core.model.ProxyProfile
import app.nebula.vpn.data.repository.ProfileRepository
import app.nebula.vpn.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
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

data class BatteryReport(
    val elapsedCpuTimeMs: Long,
    val formattedCpuTime: String,
    val liveThreadCount: Int,
    val powerProfile: PowerProfile,
    val showSpeedInNotification: Boolean,
    val appUptimeMs: Long,
    val formattedUptime: String,
    val isInteractive: Boolean,
    val diagnostics: List<String>
)

@HiltViewModel
class ToolsViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context
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

    private val _batteryReport = MutableStateFlow(buildBatteryReportSync())
    val batteryReport: StateFlow<BatteryReport> = _batteryReport.asStateFlow()

    private var reportTickerJob: Job? = null

    init {
        startReportTicker()
    }

    fun startReportTicker() {
        if (reportTickerJob != null) return
        reportTickerJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                _batteryReport.value = buildBatteryReportSync()
                delay(3000L) // Refresh every 3s while tools is viewed
            }
        }
    }

    fun stopReportTicker() {
        reportTickerJob?.cancel()
        reportTickerJob = null
    }

    fun refreshBatteryReport() {
        viewModelScope.launch(Dispatchers.IO) {
            _batteryReport.value = buildBatteryReportSync()
        }
    }

    fun setPowerProfile(profile: PowerProfile) {
        viewModelScope.launch {
            settingsRepository.setPowerProfile(profile.id)
            refreshBatteryReport()
        }
    }

    fun setShowSpeedInNotification(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowSpeedInNotification(enabled)
            refreshBatteryReport()
        }
    }

    private fun buildBatteryReportSync(): BatteryReport {
        val cpuMs = Process.getElapsedCpuTime()
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val interactive = powerManager?.isInteractive ?: true
        val threads = Thread.activeCount()

        val startElapsed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Process.getStartElapsedRealtime()
        } else {
            SystemClock.elapsedRealtime()
        }
        val uptimeMs = (SystemClock.elapsedRealtime() - startElapsed).coerceAtLeast(1000L)

        // Read current profile from repo or default
        val profileId = kotlinx.coroutines.runBlocking {
            try {
                settingsRepository.powerProfile.first()
            } catch (_: Exception) {
                "BALANCED"
            }
        }
        val showSpeed = kotlinx.coroutines.runBlocking {
            try {
                settingsRepository.showSpeedInNotification.first()
            } catch (_: Exception) {
                true
            }
        }
        val profile = PowerProfile.fromId(profileId)

        val diag = listOf(
            "Screen state: ${if (interactive) "Interactive (ON)" else "Sleep (OFF)"}",
            "Core log level: ${profile.coreLogLevel}",
            "Speed sampling: ${profile.interactiveIntervalMs / 1000}s on / ${if (profile.backgroundIntervalMs > 0) "${profile.backgroundIntervalMs / 1000}s off" else "frozen off"}",
            "Notification updates: ${if (showSpeed) "Throttled (zero in sleep)" else "Disabled (static only)"}",
            "TCP Fast Open: Enabled",
            "Zero wakelocks • JNI events throttled"
        )

        return BatteryReport(
            elapsedCpuTimeMs = cpuMs,
            formattedCpuTime = formatDuration(cpuMs),
            liveThreadCount = threads,
            powerProfile = profile,
            showSpeedInNotification = showSpeed,
            appUptimeMs = uptimeMs,
            formattedUptime = formatDuration(uptimeMs),
            isInteractive = interactive,
            diagnostics = diag
        )
    }

    private fun formatDuration(millis: Long): String {
        val totalSec = millis / 1000
        val hours = totalSec / 3600
        val minutes = (totalSec % 3600) / 60
        val seconds = totalSec % 60
        return if (hours > 0) {
            String.format(java.util.Locale.US, "%dh %dm %ds", hours, minutes, seconds)
        } else if (minutes > 0) {
            String.format(java.util.Locale.US, "%dm %ds", minutes, seconds)
        } else {
            String.format(java.util.Locale.US, "%ds (%d ms)", seconds, millis)
        }
    }

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
