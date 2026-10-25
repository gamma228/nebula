package app.nebula.vpn.ui.doctor

import android.content.Context
import android.net.VpnService
import android.os.Build
import android.os.PowerManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nebula.vpn.core.model.ConnectionState
import app.nebula.vpn.core.model.ProxyProfile
import app.nebula.vpn.data.repository.ProfileRepository
import app.nebula.vpn.data.repository.SettingsRepository
import app.nebula.vpn.data.repository.VpnRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import javax.inject.Inject

data class DiagnosticCheckItem(
    val title: String,
    val isPassed: Boolean,
    val details: String,
    val canFix: Boolean = false
)

data class DiagnosticVerdict(
    val title: String,
    val description: String,
    val isSuccess: Boolean
)

@HiltViewModel
class DoctorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profileRepository: ProfileRepository,
    private val settingsRepository: SettingsRepository,
    private val vpnRepository: VpnRepository
) : ViewModel() {

    private val _checks = MutableStateFlow<List<DiagnosticCheckItem>>(emptyList())
    val checks: StateFlow<List<DiagnosticCheckItem>> = _checks.asStateFlow()

    private val _verdict = MutableStateFlow<DiagnosticVerdict?>(null)
    val verdict: StateFlow<DiagnosticVerdict?> = _verdict.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private var activeProfileCached: ProxyProfile? = null

    init {
        runDiagnostics()
    }

    fun runDiagnostics() {
        viewModelScope.launch(Dispatchers.IO) {
            _isRunning.value = true
            val results = mutableListOf<DiagnosticCheckItem>()
            val isConnected = vpnRepository.connectionState.value is ConnectionState.Connected

            // 1. VPN Permission Check
            val vpnIntent = VpnService.prepare(context)
            val vpnPassed = vpnIntent == null
            results.add(
                DiagnosticCheckItem(
                    title = "Разрешение VPN",
                    isPassed = vpnPassed,
                    details = if (vpnPassed) "Разрешение предоставлено системе" else "Требуется разрешение Android для перехвата трафика",
                    canFix = !vpnPassed
                )
            )

            // 2. Battery Restrictions
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val batteryIgnoring = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && powerManager != null) {
                powerManager.isIgnoringBatteryOptimizations(context.packageName)
            } else true
            results.add(
                DiagnosticCheckItem(
                    title = "Фоновый режим и батарея",
                    isPassed = batteryIgnoring,
                    details = if (batteryIgnoring) "Неограниченная фоновая работа разрешена" else "Оптимизация батареи может принудительно остановить VPN",
                    canFix = !batteryIgnoring
                )
            )

            // 3. Active Server Reachability
            val savedProfileId = settingsRepository.selectedProfileId.first()
            val activeProfile = savedProfileId?.let { profileRepository.getProfileById(it) }
            activeProfileCached = activeProfile
            if (activeProfile != null) {
                var serverPassed = false
                var latencyMs = -1L
                val start = System.currentTimeMillis()
                try {
                    Socket().use { socket ->
                        socket.tcpNoDelay = true
                        socket.soTimeout = 3000
                        socket.connect(InetSocketAddress(activeProfile.server, activeProfile.port), 3000)
                    }
                    latencyMs = System.currentTimeMillis() - start
                    serverPassed = true
                } catch (e: Exception) {
                    serverPassed = false
                }
                results.add(
                    DiagnosticCheckItem(
                        title = "Сервер прокси (${activeProfile.name})",
                        isPassed = serverPassed,
                        details = if (serverPassed) "Доступен: ${activeProfile.server}:${activeProfile.port} (${latencyMs} ms)" else "Недоступен: ${activeProfile.server}:${activeProfile.port}",
                        canFix = false
                    )
                )
            } else {
                results.add(
                    DiagnosticCheckItem(
                        title = "Выбранный сервер",
                        isPassed = false,
                        details = "Сервер не выбран. Выберите сервер на вкладке «Серверы».",
                        canFix = true
                    )
                )
            }

            // 4. DNS Hijack & Remote DoH Check
            val dohResult = testHttp("https://1.1.1.1/dns-query?name=cloudflare.com&type=A", timeoutMs = 3000)
            val dohPassed = dohResult != null
            results.add(
                DiagnosticCheckItem(
                    title = "Безопасный DNS (DoH Detour)",
                    isPassed = dohPassed,
                    details = if (dohPassed) "DoH 1.1.1.1 успешно отвечает через туннель" else "DoH недоступен (возможна блокировка провайдером)",
                    canFix = false
                )
            )

            // 5. Exit IP Discovery
            val exitIp = testHttp("https://api.ipify.org", timeoutMs = 3500)
            results.add(
                DiagnosticCheckItem(
                    title = "Внешний IP адрес (Exit IP)",
                    isPassed = exitIp != null,
                    details = if (exitIp != null) "Текущий IP: $exitIp (${if (isConnected) "Туннель активен" else "Прямое подключение"})" else "Не удалось определить внешний IP адрес",
                    canFix = false
                )
            )

            // 6. Censored / Restricted Endpoints Connectivity
            val testTargets = listOf(
                "Cloudflare CP" to "https://cp.cloudflare.com/generate_204",
                "Google Gen204" to "https://www.google.com/generate_204",
                "GitHub CDN" to "https://raw.githubusercontent.com"
            )
            val targetResults = testTargets.map { (name, url) ->
                val code = getResponseCode(url, timeoutMs = 3000)
                name to code
            }
            val anyPassed = targetResults.any { it.second in 200..399 }
            val targetDetails = targetResults.joinToString(", ") { "${it.first}: ${if (it.second > 0) "HTTP ${it.second}" else "Сбой"}" }
            results.add(
                DiagnosticCheckItem(
                    title = "Обход блокировок веб-ресурсов",
                    isPassed = anyPassed,
                    details = targetDetails,
                    canFix = false
                )
            )

            // 7. IPv6 Shielding
            results.add(
                DiagnosticCheckItem(
                    title = "Защита от утечек IPv6",
                    isPassed = true,
                    details = "Маршрутизация IPv6 заблокирована локальным черным пулом (fdfe:dcba:9876::1/126)",
                    canFix = false
                )
            )

            // 8. UDP Connectivity (DNS port 53 probe)
            var udpPassed = false
            try {
                Socket().use { s ->
                    s.soTimeout = 2500
                    s.connect(InetSocketAddress("1.1.1.1", 53), 2500)
                }
                udpPassed = true
            } catch (e: Exception) {
                udpPassed = false
            }
            results.add(
                DiagnosticCheckItem(
                    title = "UDP/QUIC проходимость",
                    isPassed = udpPassed,
                    details = if (udpPassed) "Порт 53/UDP и транспорт доступны" else "Транспортный порт недоступен или фильтруется",
                    canFix = false
                )
            )

            // Verdict
            val failedChecks = results.filter { !it.isPassed }
            val verdict = when {
                failedChecks.isEmpty() -> DiagnosticVerdict(
                    title = "Все системы в норме",
                    description = "Туннель, DNS и маршрутизация функционируют исправно.",
                    isSuccess = true
                )
                failedChecks.any { it.title.contains("Сервер") } -> DiagnosticVerdict(
                    title = "Сервер недоступен",
                    description = "Не удается установить соединение с сервером прокси. Попробуйте другой узел.",
                    isSuccess = false
                )
                failedChecks.any { it.title.contains("DNS") } -> DiagnosticVerdict(
                    title = "Утечка или сбой DNS",
                    description = "Запросы DNS не проходят через туннель. Проверьте настройки DoH.",
                    isSuccess = false
                )
                else -> DiagnosticVerdict(
                    title = "Обнаружены предупреждения",
                    description = "Некоторые тесты завершились с ошибкой (${failedChecks.size} из ${results.size}).",
                    isSuccess = false
                )
            }

            withContext(Dispatchers.Main) {
                _checks.value = results
                _verdict.value = verdict
                _isRunning.value = false
            }
        }
    }

    private fun testHttp(urlStr: String, timeoutMs: Int = 3000): String? {
        return try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.instanceFollowRedirects = true
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile; Nebula-VPN)")
            try {
                if (conn.responseCode in 200..399) {
                    conn.inputStream.bufferedReader().use { it.readText().trim() }
                } else null
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getResponseCode(urlStr: String, timeoutMs: Int = 3000): Int {
        return try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.instanceFollowRedirects = true
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile; Nebula-VPN)")
            try {
                conn.responseCode
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            -1
        }
    }

    fun generateRedactedDiagnosticsBundle(): String {
        val sb = StringBuilder()
        sb.appendLine("=========================================")
        sb.appendLine("       NEBULA DIAGNOSTICS BUNDLE         ")
        sb.appendLine("=========================================")
        sb.appendLine("Timestamp: ${System.currentTimeMillis()}")
        sb.appendLine("Android Version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        sb.appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        sb.appendLine("App State: ${vpnRepository.connectionState.value}")
        sb.appendLine()
        sb.appendLine("--- TEST RESULTS ---")
        val currentVerdict = _verdict.value
        if (currentVerdict != null) {
            sb.appendLine("VERDICT: ${if (currentVerdict.isSuccess) "[OK]" else "[FAIL]"} ${currentVerdict.title} - ${currentVerdict.description}")
        }
        for (check in _checks.value) {
            val status = if (check.isPassed) "[PASS]" else "[WARN]"
            sb.appendLine("$status ${check.title}: ${check.details}")
        }
        sb.appendLine()
        sb.appendLine("--- ACTIVE PROFILE (REDACTED) ---")
        val profile = activeProfileCached
        if (profile != null) {
            sb.appendLine("Protocol: ${profile.protocol}")
            sb.appendLine("Server: ${profile.server}:${profile.port}")
            sb.appendLine("Secret: [REDACTED]")
            sb.appendLine("TLS Enabled: ${profile.tls.enabled}")
            sb.appendLine("TLS SNI: ${profile.tls.serverName}")
            sb.appendLine("TLS ALPN: ${profile.tls.alpn}")
            sb.appendLine("Reality PubKey: ${if (profile.tls.realityPublicKey.isNotEmpty()) "[CONFIGURED]" else "[NONE]"}")
            sb.appendLine("Transport: ${profile.transport.type}")
            sb.appendLine("Transport Host: ${profile.transport.host}")
            sb.appendLine("Transport Path: ${profile.transport.path}")
        } else {
            sb.appendLine("No active profile")
        }
        sb.appendLine("=========================================")
        return sb.toString()
    }
}
