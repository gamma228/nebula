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
import app.nebula.vpn.core.model.ProtocolType
import app.nebula.vpn.core.model.ProxyProfile
import app.nebula.vpn.core.model.TransportType
import app.nebula.vpn.core.proxy.ProxyCore
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
import java.io.File
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.crypto.BadPaddingException
import javax.inject.Inject
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLException

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

data class HttpProbeResult(
    val statusCode: Int = -1,
    val body: String? = null,
    val latencyMs: Long = -1L,
    val errorClass: String? = null
)

@HiltViewModel
class DoctorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profileRepository: ProfileRepository,
    private val settingsRepository: SettingsRepository,
    private val vpnRepository: VpnRepository,
    private val proxyCore: ProxyCore
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

            // Retrieve active or selected profile
            val savedProfileId = settingsRepository.selectedProfileId.first()
            val activeProfile = savedProfileId?.let { profileRepository.getProfileById(it) }
                ?: profileRepository.allProfiles.first().firstOrNull()
            activeProfileCached = activeProfile

            // 1. VPN Permission Check (Pre-check)
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

            // 2. Battery Restrictions (Pre-check)
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

            // 3. Server Direct TCP Socket Reachability
            var tcpPortReachable = false
            var tcpLatencyMs = -1L
            if (activeProfile != null) {
                val start = System.currentTimeMillis()
                var tcpError: String? = null
                try {
                    Socket().use { socket ->
                        socket.tcpNoDelay = true
                        socket.soTimeout = 3000
                        socket.connect(InetSocketAddress(activeProfile.server, activeProfile.port), 3000)
                    }
                    tcpLatencyMs = System.currentTimeMillis() - start
                    tcpPortReachable = true
                } catch (e: Exception) {
                    tcpError = classifyException(e)
                }

                if (!isConnected) {
                    // Pre-check while disconnected: TCP alone shows port open, data not verified
                    results.add(
                        DiagnosticCheckItem(
                            title = "Сервер прокси (${activeProfile.name})",
                            isPassed = tcpPortReachable,
                            details = if (tcpPortReachable) {
                                "Порт открыт: ${activeProfile.server}:${activeProfile.port} (${tcpLatencyMs} ms, данные не проверены)"
                            } else {
                                "Порт закрыт или недоступен: ${activeProfile.server}:${activeProfile.port} ($tcpError)"
                            },
                            canFix = false
                        )
                    )
                }
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

            // 4. IPv6 Strategy (Pre-check)
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

            // 5. QUIC Blocking (Pre-check)
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

            // If disconnected, complete diagnostics with pre-checks only. Do NOT run tunnel tests.
            if (!isConnected) {
                val preCheckFailed = results.filter { !it.isPassed }
                val verdict = DiagnosticVerdict(
                    title = "VPN не подключен",
                    description = if (preCheckFailed.isEmpty()) {
                        "Предварительные проверки пройдены. Подключите VPN на Главной для проверки защищенного интернет-канала."
                    } else {
                        "VPN выключен. Обнаружены замечания в предварительных настройках (${preCheckFailed.size})."
                    },
                    isSuccess = preCheckFailed.isEmpty()
                )

                withContext(Dispatchers.Main) {
                    _checks.value = results
                    _verdict.value = verdict
                    _isRunning.value = false
                }
                return@launch
            }

            // ==========================================
            // TUNNEL TESTS (RUN ONLY WHEN CONNECTED)
            // ==========================================
            val tunnelProxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 2080))

            // 6. Profile Configuration Integrity & Suspicion Check
            val suspiciousFields = mutableListOf<String>()
            if (activeProfile != null) {
                if (activeProfile.transport.type == TransportType.GRPC && activeProfile.transport.serviceName.isBlank()) {
                    suspiciousFields.add("gRPC serviceName пустой (сервер отклонит запрос с 404)")
                }
                if (activeProfile.protocol == ProtocolType.VLESS && activeProfile.tls.realityPublicKey.isEmpty() && activeProfile.tls.flow.contains("vision")) {
                    suspiciousFields.add("Vision flow указан без Reality Public Key")
                }
                if (activeProfile.tls.enabled && activeProfile.tls.alpn.isEmpty() && activeProfile.transport.type == TransportType.GRPC) {
                    suspiciousFields.add("gRPC TLS без ALPN [h2]")
                }
            }
            if (suspiciousFields.isNotEmpty()) {
                results.add(
                    DiagnosticCheckItem(
                        title = "Конфигурация профиля",
                        isPassed = false,
                        details = "Подозрительные параметры: ${suspiciousFields.joinToString("; ")}",
                        canFix = false
                    )
                )
            }

            // 7. Active Node Data Path Verification (Real HTTPS GET 204 through tunnel)
            var nodeDataPassed = false
            val gen204Probe = probeHttp("https://cp.cloudflare.com/generate_204", timeoutMs = 5000, proxy = tunnelProxy)
            val gen204Fallback = if (gen204Probe.statusCode !in 200..399) {
                probeHttp("https://www.google.com/generate_204", timeoutMs = 5000, proxy = tunnelProxy)
            } else gen204Probe

            if (gen204Probe.statusCode in 200..399 || gen204Fallback.statusCode in 200..399) {
                nodeDataPassed = true
                val latency = if (gen204Probe.statusCode in 200..399) gen204Probe.latencyMs else gen204Fallback.latencyMs
                val code = if (gen204Probe.statusCode in 200..399) gen204Probe.statusCode else gen204Fallback.statusCode
                results.add(
                    DiagnosticCheckItem(
                        title = "Сервер прокси (${activeProfile?.name ?: "Активный"})",
                        isPassed = true,
                        details = "Передача данных подтверждена: HTTP $code (${latency} ms)",
                        canFix = false
                    )
                )
            } else {
                val err = gen204Probe.errorClass ?: gen204Fallback.errorClass ?: "Сбой передачи данных"
                results.add(
                    DiagnosticCheckItem(
                        title = "Сервер прокси (${activeProfile?.name ?: "Активный"})",
                        isPassed = false,
                        details = if (tcpPortReachable) {
                            "Порт открыт ($tcpLatencyMs ms), но данные не передаются через прокси ($err)"
                        } else {
                            "Сервер не отвечает: $err"
                        },
                        canFix = false
                    )
                )
            }

            // 8. DNS Detour Check (DoH via proxy)
            val dohHeaders = mapOf("Accept" to "application/dns-json")
            val cfDoh = probeHttp("https://1.1.1.1/dns-query?name=cloudflare.com&type=A", timeoutMs = 5000, proxy = tunnelProxy, headers = dohHeaders)
            val dohPassed: Boolean
            val dohDetails: String
            if (cfDoh.statusCode in 200..399) {
                dohPassed = true
                dohDetails = "DoH успешно защищен и отвечает через туннель (${cfDoh.latencyMs} ms)"
            } else {
                val googleDoh = probeHttp("https://8.8.8.8/resolve?name=google.com&type=A", timeoutMs = 5000, proxy = tunnelProxy, headers = dohHeaders)
                if (googleDoh.statusCode in 200..399) {
                    dohPassed = true
                    dohDetails = "DoH (Google) успешно защищен и отвечает через туннель (${googleDoh.latencyMs} ms)"
                } else {
                    dohPassed = false
                    val dohErr = cfDoh.errorClass ?: googleDoh.errorClass ?: "DoH не ответил"
                    dohDetails = "DoH не ответил через прокси-туннель: $dohErr"
                }
            }
            results.add(
                DiagnosticCheckItem(
                    title = "Безопасный DNS (DoH Detour)",
                    isPassed = dohPassed,
                    details = dohDetails,
                    canFix = false
                )
            )

            // 9. Exit IP Discovery
            var exitIp: String? = null
            var ipError: String? = null
            val ipEndpoints = listOf(
                "https://api.ipify.org",
                "https://ifconfig.me/ip",
                "https://icanhazip.com",
                "https://checkip.amazonaws.com"
            )
            for (endpoint in ipEndpoints) {
                val probe = probeHttp(endpoint, timeoutMs = 5000, proxy = tunnelProxy)
                if (probe.body != null && probe.body.length in 7..45 && !probe.body.contains("<html", ignoreCase = true)) {
                    exitIp = probe.body.trim()
                    break
                } else if (probe.errorClass != null && ipError == null) {
                    ipError = probe.errorClass
                }
            }
            results.add(
                DiagnosticCheckItem(
                    title = "Внешний IP адрес (Exit IP)",
                    isPassed = exitIp != null,
                    details = if (exitIp != null) "Текущий IP: $exitIp (Туннель активен, защита включена)" else "Не удалось определить внешний IP через туннель (${ipError ?: "Таймаут"})",
                    canFix = false
                )
            )

            // 10. Web Resources Bypass
            val testTargets = listOf(
                "Cloudflare CP" to "https://cp.cloudflare.com/generate_204",
                "Google Gen204" to "https://www.google.com/generate_204",
                "GitHub CDN" to "https://raw.githubusercontent.com"
            )
            val targetResults = testTargets.map { (name, url) ->
                val probe = probeHttp(url, timeoutMs = 5000, proxy = tunnelProxy)
                name to probe
            }
            val anyPassed = targetResults.any { it.second.statusCode in 200..399 }
            val targetDetails = targetResults.joinToString(", ") { (name, p) ->
                "$name: ${if (p.statusCode > 0) "HTTP ${p.statusCode}" else (p.errorClass ?: "Сбой")}"
            }
            results.add(
                DiagnosticCheckItem(
                    title = "Обход блокировок веб-ресурсов",
                    isPassed = anyPassed,
                    details = targetDetails,
                    canFix = false
                )
            )

            // 11. Telegram Connectivity
            var tgPassed = false
            var tgLatency = -1L
            var tgError: String? = null
            val tgStart = System.currentTimeMillis()
            try {
                val socksProxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 2080))
                Socket(socksProxy).use { s ->
                    s.tcpNoDelay = true
                    s.soTimeout = 4000
                    s.connect(InetSocketAddress("149.154.167.50", 443), 4000)
                }
                tgPassed = true
                tgLatency = System.currentTimeMillis() - tgStart
            } catch (e: Exception) {
                tgError = classifyException(e)
            }
            if (!tgPassed) {
                val webTg = probeHttp("https://web.telegram.org", timeoutMs = 5000, proxy = tunnelProxy)
                if (webTg.statusCode in 200..399) {
                    tgPassed = true
                    tgLatency = webTg.latencyMs
                } else {
                    val apiTg = probeHttp("https://api.telegram.org", timeoutMs = 4000, proxy = tunnelProxy)
                    if (apiTg.statusCode in 200..499) {
                        tgPassed = true
                        tgLatency = apiTg.latencyMs
                    }
                }
            }
            results.add(
                DiagnosticCheckItem(
                    title = "Соединение с Telegram",
                    isPassed = tgPassed,
                    details = if (tgPassed) "Telegram доступен через туннель ($tgLatency ms)" else "Telegram не отвечает через туннель (${tgError ?: "Сбой подключения"})",
                    canFix = false
                )
            )

            // 12. YouTube HTTPS Handshake
            val ytProbe = probeHttp("https://www.youtube.com/generate_204", timeoutMs = 5000, proxy = tunnelProxy)
            val mYtProbe = if (ytProbe.statusCode !in 200..399 && ytProbe.statusCode != 429) {
                probeHttp("https://m.youtube.com", timeoutMs = 5000, proxy = tunnelProxy)
            } else ytProbe
            val ytPassed = ytProbe.statusCode in 200..399 || ytProbe.statusCode == 429 || mYtProbe.statusCode in 200..399 || mYtProbe.statusCode == 429
            val ytCode = if (ytProbe.statusCode > 0) ytProbe.statusCode else mYtProbe.statusCode
            results.add(
                DiagnosticCheckItem(
                    title = "Доступность YouTube по HTTPS",
                    isPassed = ytPassed,
                    details = if (ytPassed) {
                        if (ytCode == 429) "YouTube отвечает через туннель (HTTP 429)" else "YouTube отвечает без ограничений (HTTP $ytCode, ${ytProbe.latencyMs} ms)"
                    } else "YouTube не отвечает через туннель: ${ytProbe.errorClass ?: "Сбой"}",
                    canFix = false
                )
            )

            // 13. Verdict Computation (strictly backed by real results)
            val failedChecks = results.filter { !it.isPassed }
            val verdict = when {
                suspiciousFields.isNotEmpty() -> DiagnosticVerdict(
                    title = "Проблема в профиле",
                    description = "Обнаружены некорректные параметры: ${suspiciousFields.first()}. Проверьте настройки узла.",
                    isSuccess = false
                )
                failedChecks.isEmpty() -> DiagnosticVerdict(
                    title = "Все системы в норме",
                    description = "Туннель, DNS и маршрутизация функционируют исправно.",
                    isSuccess = true
                )
                !nodeDataPassed -> DiagnosticVerdict(
                    title = "Сервер не передает данные",
                    description = "Узел подключен, но удаленный сервер не пропускает трафик. Проверьте правильность ключей или выберите другой узел.",
                    isSuccess = false
                )
                !dohPassed -> DiagnosticVerdict(
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

    private fun probeHttp(
        urlStr: String,
        timeoutMs: Int = 4000,
        proxy: Proxy? = null,
        headers: Map<String, String> = emptyMap()
    ): HttpProbeResult {
        val start = System.currentTimeMillis()
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            conn = (if (proxy != null) url.openConnection(proxy) else url.openConnection()) as HttpURLConnection
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.instanceFollowRedirects = true
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile; Nebula-VPN)")
            for ((k, v) in headers) {
                conn.setRequestProperty(k, v)
            }
            val code = conn.responseCode
            val latency = (System.currentTimeMillis() - start).coerceAtLeast(1L)
            val body = if (code in 200..399) {
                try {
                    conn.inputStream.bufferedReader().use { it.readText().trim() }
                } catch (_: Exception) { null }
            } else null
            HttpProbeResult(statusCode = code, body = body, latencyMs = latency)
        } catch (e: Exception) {
            val latency = (System.currentTimeMillis() - start).coerceAtLeast(1L)
            HttpProbeResult(statusCode = -1, latencyMs = latency, errorClass = classifyException(e))
        } finally {
            try {
                conn?.disconnect()
            } catch (_: Exception) {}
        }
    }

    private fun classifyException(e: Throwable): String {
        return when (e) {
            is SocketTimeoutException -> "Таймаут (Timeout)"
            is ConnectException -> "Отказ соединения (Connection Refused/Reset)"
            is UnknownHostException -> "Ошибка DNS (Unknown Host)"
            is SSLHandshakeException -> "Сбой TLS рукопожатия (TLS Handshake Failed)"
            is SSLException -> "Ошибка SSL/TLS (SSL Exception)"
            is BadPaddingException -> "Ошибка шифрования (Bad Padding)"
            else -> e.message?.take(60) ?: e.javaClass.simpleName
        }
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
                        val packageName = context.packageName
                        val isXiaomi = Build.MANUFACTURER.contains("Xiaomi", ignoreCase = true) ||
                                       Build.MANUFACTURER.contains("Redmi", ignoreCase = true) ||
                                       Build.MANUFACTURER.contains("POCO", ignoreCase = true)
                        val isSamsung = Build.MANUFACTURER.contains("Samsung", ignoreCase = true)

                        val intents = mutableListOf<Intent>()

                        if (isXiaomi) {
                            intents.add(Intent().apply {
                                component = android.content.ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
                                putExtra("package_name", packageName)
                                putExtra("package_label", "Nebula")
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            })
                            intents.add(Intent().apply {
                                component = android.content.ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsContainerManagementActivity")
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            })
                        } else if (isSamsung) {
                            intents.add(Intent().apply {
                                component = android.content.ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity")
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            })
                            intents.add(Intent().apply {
                                component = android.content.ComponentName("com.samsung.android.sm", "com.samsung.android.sm.battery.ui.BatteryActivity")
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            })
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            intents.add(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:$packageName")
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            })
                        }

                        intents.add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:$packageName")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        })

                        intents.add(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        })

                        for (intent in intents) {
                            try {
                                context.startActivity(intent)
                                break
                            } catch (_: Exception) {}
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
            sb.appendLine("Packet Encoding: ${profile.packetEncoding}")
            sb.appendLine("Header Type: ${profile.headerType.ifEmpty { "[NONE]" }}")
            sb.appendLine("TLS Enabled: ${profile.tls.enabled}")
            sb.appendLine("TLS SNI: ${profile.tls.serverName}")
            sb.appendLine("TLS ALPN: ${profile.tls.alpn}")
            sb.appendLine("TLS Insecure: ${profile.tls.insecure}")
            sb.appendLine("uTLS Fingerprint: ${profile.tls.utlsFingerprint}")
            sb.appendLine("Reality PubKey: ${if (profile.tls.realityPublicKey.isNotEmpty()) "[CONFIGURED]" else "[NONE]"}")
            sb.appendLine("Reality ShortId: ${profile.tls.realityShortId.ifEmpty { "[NONE]" }}")
            sb.appendLine("Reality SpiderX: ${profile.tls.spiderX.ifEmpty { "[NONE]" }}")
            sb.appendLine("Flow: ${profile.tls.flow.ifEmpty { "[NONE]" }}")
            sb.appendLine("Transport Type: ${profile.transport.type}")
            sb.appendLine("Transport Path: ${profile.transport.path.ifEmpty { "[NONE]" }}")
            sb.appendLine("Transport Host: ${profile.transport.host.ifEmpty { "[NONE]" }}")
            sb.appendLine("Transport ServiceName: ${profile.transport.serviceName.ifEmpty { "[NONE]" }}")
            sb.appendLine("Transport Mode: ${profile.transport.mode}")
            sb.appendLine("Transport Authority: ${profile.transport.authority.ifEmpty { "[NONE]" }}")
        } else {
            sb.appendLine("No active profile")
        }
        sb.appendLine()
        sb.appendLine("--- SANITIZED GENERATED CONFIG ---")
        try {
            val configFile = File(context.filesDir, "last_generated_config.json")
            if (configFile.exists()) {
                sb.appendLine(configFile.readText())
            } else {
                sb.appendLine("[No generated config on disk yet]")
            }
        } catch (e: Exception) {
            sb.appendLine("[Error reading generated config: ${e.message}]")
        }
        sb.appendLine()
        sb.appendLine("--- RECENT SING-BOX LOGS (LAST 100 LINES) ---")
        try {
            val recentLogs = proxyCore.getRecentLogs(100)
            if (recentLogs.isNotEmpty()) {
                recentLogs.forEach { sb.appendLine(it) }
            } else {
                sb.appendLine("[No logs available in buffer]")
            }
        } catch (e: Exception) {
            sb.appendLine("[Error reading recent logs: ${e.message}]")
        }
        sb.appendLine("=========================================")
        return sb.toString()
    }
}
