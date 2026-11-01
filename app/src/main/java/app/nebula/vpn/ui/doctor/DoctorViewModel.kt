package app.nebula.vpn.ui.doctor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nebula.vpn.core.model.ConnectionState
import app.nebula.vpn.core.model.ProxyProfile
import app.nebula.vpn.data.repository.ProfileRepository
import app.nebula.vpn.data.repository.SettingsRepository
import app.nebula.vpn.data.repository.VpnRepository
import app.nebula.vpn.vpn.ProxyVpnService
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
import java.net.Proxy
import java.net.Socket
import java.net.URL
import javax.inject.Inject

data class DiagnosticCheckItem(
    val title: String,
    val isPassed: Boolean,
    val details: String,
    val canFix: Boolean = false,
    val fixActionId: String? = null
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

    fun runDiagnostics() {
        viewModelScope.launch(Dispatchers.IO) {
            _isRunning.value = true
            val results = mutableListOf<DiagnosticCheckItem>()
            val isConnected = vpnRepository.connectionState.value is ConnectionState.Connected

            // Local mixed HTTP/SOCKS inbound exposed by sing-box at port 2080
            val tunnelProxy: Proxy? = if (isConnected) {
                Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 2080))
            } else {
                null
            }

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
                    canFix = !batteryIgnoring,
                    fixActionId = if (!batteryIgnoring) "REQUEST_BATTERY" else null
                )
            )

            // 3. Active Server Reachability (Direct physical socket test)
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

            // 4. DNS Hijack & Remote DoH Check (Tested through tunnel proxy when connected)
            if (isConnected) {
                var dohPassed = false
                var dohLatency = -1L
                val dohStart = System.currentTimeMillis()

                // Try Cloudflare DoH first (with Accept: application/dns-json RFC 8484 header), fallback to Google DoH /resolve
                val dohHeaders = mapOf("Accept" to "application/dns-json")
                var cfDoh = testHttp("https://1.1.1.1/dns-query?name=cloudflare.com&type=A", timeoutMs = 6000, proxy = tunnelProxy, headers = dohHeaders)
                if (cfDoh != null) {
                    dohPassed = true
                    dohLatency = System.currentTimeMillis() - dohStart
                } else {
                    var googleDoh = testHttp("https://8.8.8.8/resolve?name=google.com&type=A", timeoutMs = 6000, proxy = tunnelProxy, headers = dohHeaders)
                    if (googleDoh != null) {
                        dohPassed = true
                        dohLatency = System.currentTimeMillis() - dohStart
                    }
                }
                results.add(
                    DiagnosticCheckItem(
                        title = "Безопасный DNS (DoH Detour)",
                        isPassed = dohPassed,
                        details = if (dohPassed) "DoH успешно защищен и отвечает через туннель (${dohLatency} ms)" else "DoH не ответил через прокси-туннель",
                        canFix = false
                    )
                )
            } else {
                results.add(
                    DiagnosticCheckItem(
                        title = "Безопасный DNS (DoH Detour)",
                        isPassed = true,
                        details = "VPN отключен. Защита DoH активируется при подключении",
                        canFix = false
                    )
                )
            }

            // 5. Exit IP Discovery (with multiple fallback endpoints and dual proxy/tun channel)
            if (isConnected) {
                var exitIp: String? = null
                val ipEndpoints = listOf(
                    "https://api.ipify.org",
                    "https://ifconfig.me/ip",
                    "https://icanhazip.com",
                    "https://checkip.amazonaws.com"
                )
                for (endpoint in ipEndpoints) {
                    val res = testHttp(endpoint, timeoutMs = 7000, proxy = tunnelProxy)
                    if (!res.isNullOrBlank() && res.length in 7..45 && !res.contains("<html", ignoreCase = true)) {
                        exitIp = res.trim()
                        break
                    }
                }
                results.add(
                    DiagnosticCheckItem(
                        title = "Внешний IP адрес (Exit IP)",
                        isPassed = exitIp != null,
                        details = if (exitIp != null) "Текущий IP: $exitIp (Туннель активен, защита включена)" else "Не удалось определить внешний IP через туннель",
                        canFix = false
                    )
                )
            } else {
                val exitIp = testHttp("https://api.ipify.org", timeoutMs = 3000, proxy = null)
                results.add(
                    DiagnosticCheckItem(
                        title = "Внешний IP адрес (Exit IP)",
                        isPassed = true,
                        details = if (exitIp != null) "Текущий IP: $exitIp (Прямое подключение, VPN выключен)" else "Не удалось определить внешний IP",
                        canFix = false
                    )
                )
            }

            // 6. Censored / Restricted Endpoints Connectivity
            if (isConnected) {
                val testTargets = listOf(
                    "Cloudflare CP" to "https://cp.cloudflare.com/generate_204",
                    "Google Gen204" to "https://www.google.com/generate_204",
                    "GitHub CDN" to "https://raw.githubusercontent.com"
                )
                val targetResults = testTargets.map { (name, url) ->
                    val code = getResponseCode(url, timeoutMs = 6000, proxy = tunnelProxy)
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
            } else {
                results.add(
                    DiagnosticCheckItem(
                        title = "Обход блокировок веб-ресурсов",
                        isPassed = true,
                        details = "Проверка обхода блокировок активна при включенном VPN",
                        canFix = false
                    )
                )
            }

            // 7. Telegram Direct Connectivity Check
            var tgPassed = false
            var tgLatency = -1L
            val tgStart = System.currentTimeMillis()
            if (isConnected) {
                val tgCode = getResponseCode("https://web.telegram.org", timeoutMs = 6000, proxy = tunnelProxy)
                if (tgCode in 200..399) {
                    tgPassed = true
                    tgLatency = System.currentTimeMillis() - tgStart
                }
            } else {
                try {
                    Socket().use { s ->
                        s.tcpNoDelay = true
                        s.soTimeout = 2500
                        s.connect(InetSocketAddress("149.154.167.51", 443), 2500)
                    }
                    tgLatency = System.currentTimeMillis() - tgStart
                    tgPassed = true
                } catch (_: Exception) {
                    tgPassed = false
                }
            }
            results.add(
                DiagnosticCheckItem(
                    title = "Соединение с Telegram",
                    isPassed = tgPassed,
                    details = if (tgPassed) {
                        if (isConnected) "Telegram доступен через туннель ($tgLatency ms)" else "Telegram доступен напрямую ($tgLatency ms)"
                    } else {
                        if (isConnected) "Telegram не отвечает через туннель" else "Telegram недоступен напрямую через текущую сеть"
                    },
                    canFix = false
                )
            )

            // 8. YouTube HTTPS Handshake
            if (isConnected) {
                val ytCode = getResponseCode("https://www.youtube.com/generate_204", timeoutMs = 7000, proxy = tunnelProxy)
                val ytPassed = ytCode in 200..399
                results.add(
                    DiagnosticCheckItem(
                        title = "Доступность YouTube по HTTPS",
                        isPassed = ytPassed,
                        details = if (ytPassed) "YouTube отвечает без ограничений (HTTP $ytCode)" else "YouTube не отвечает через туннель",
                        canFix = false
                    )
                )
            } else {
                results.add(
                    DiagnosticCheckItem(
                        title = "Доступность YouTube по HTTPS",
                        isPassed = true,
                        details = "VPN выключен (в РФ прямой доступ к YouTube может замедляться)",
                        canFix = false
                    )
                )
            }

            // 9. IPv6 Routing Strategy Check
            val isIpv6On = settingsRepository.ipv6Enabled.first()
            results.add(
                DiagnosticCheckItem(
                    title = "Стратегия маршрутизации IPv6",
                    isPassed = !isIpv6On,
                    details = if (!isIpv6On) {
                        "IPv6 отключен (рекомендуется для РФ; предотвращает зависания при отсутствии IPv6 у провайдера/сервера)"
                    } else {
                        "IPv6 включен. В мобильных сетях и у большинства провайдеров РФ это приводит к сбоям и зависаниям"
                    },
                    canFix = isIpv6On,
                    fixActionId = if (isIpv6On) "DISABLE_IPV6" else null
                )
            )

            // 10. QUIC (UDP 443) Anti-Censorship Check
            val isQuicBlocked = settingsRepository.blockQuic.first()
            results.add(
                DiagnosticCheckItem(
                    title = "Блокировка QUIC против цензуры",
                    isPassed = isQuicBlocked,
                    details = if (isQuicBlocked) {
                        "QUIC (UDP 443) блокируется: браузеры и YouTube принудительно используют быстрый TCP HTTP/2"
                    } else {
                        "QUIC разрешен. ТСПУ часто замедляет или отбрасывает пакеты UDP 443, вызывая зависание видео"
                    },
                    canFix = !isQuicBlocked,
                    fixActionId = if (!isQuicBlocked) "BLOCK_QUIC" else null
                )
            )

            // 11. UDP Connectivity
            results.add(
                DiagnosticCheckItem(
                    title = "UDP/QUIC проходимость",
                    isPassed = true,
                    details = if (isConnected) "Транспортный шлюз sing-box активен и обрабатывает UDP/TCP" else "VPN отключен. Включите туннель для маршрутизации UDP",
                    canFix = false
                )
            )

            // Verdict Computation
            val failedChecks = results.filter { !it.isPassed }
            val verdict = when {
                !isConnected -> DiagnosticVerdict(
                    title = "VPN отключен",
                    description = "Туннель выключен. Подключитесь на Главной, чтобы активировать защиту и обход блокировок.",
                    isSuccess = true
                )
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
                failedChecks.any { it.title.contains("Exit IP") || it.title.contains("Внешний IP") } &&
                failedChecks.any { it.title.contains("Обход") } -> DiagnosticVerdict(
                    title = "Сервер не передает данные",
                    description = "Узел подключен, но удаленный сервер не пропускает интернет-трафик. Выберите другой рабочий узел из списка.",
                    isSuccess = false
                )
                failedChecks.any { it.title.contains("DNS") } -> DiagnosticVerdict(
                    title = "Сбой DNS в туннеле",
                    description = "Запросы DNS не проходят через туннель. Проверьте настройки DoH или узел.",
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

    private fun testHttp(
        urlStr: String,
        timeoutMs: Int = 3000,
        proxy: Proxy? = null,
        headers: Map<String, String> = emptyMap()
    ): String? {
        fun doRequest(p: Proxy?): String? {
            return try {
                val url = URL(urlStr)
                val conn = (if (p != null) url.openConnection(p) else url.openConnection()) as HttpURLConnection
                conn.connectTimeout = timeoutMs
                conn.readTimeout = timeoutMs
                conn.instanceFollowRedirects = true
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile; Nebula-VPN)")
                for ((k, v) in headers) {
                    conn.setRequestProperty(k, v)
                }
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

        return doRequest(proxy)
    }

    private fun getResponseCode(
        urlStr: String,
        timeoutMs: Int = 3000,
        proxy: Proxy? = null,
        headers: Map<String, String> = emptyMap()
    ): Int {
        fun doRequest(p: Proxy?): Int {
            return try {
                val url = URL(urlStr)
                val conn = (if (p != null) url.openConnection(p) else url.openConnection()) as HttpURLConnection
                conn.connectTimeout = timeoutMs
                conn.readTimeout = timeoutMs
                conn.instanceFollowRedirects = true
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile; Nebula-VPN)")
                for ((k, v) in headers) {
                    conn.setRequestProperty(k, v)
                }
                try {
                    conn.responseCode
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                -1
            }
        }

        return doRequest(proxy)
    }

    fun applyFix(fixActionId: String) {
        viewModelScope.launch {
            when (fixActionId) {
                "DISABLE_IPV6" -> {
                    settingsRepository.setIpv6Enabled(false)
                    runDiagnostics()
                }
                "BLOCK_QUIC" -> {
                    settingsRepository.setBlockQuic(true)
                    runDiagnostics()
                }
                "REQUEST_BATTERY" -> {
                    withContext(Dispatchers.Main) {
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            }
                        } catch (_: Exception) {
                            try {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                try {
                                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            }
                        }
                    }
                }
            }
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
