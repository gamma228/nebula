package app.nebula.vpn.core.windows

import app.nebula.vpn.core.contract.CoreController
import app.nebula.vpn.core.model.ConnectionState
import app.nebula.vpn.core.model.ProxyProfile
import app.nebula.vpn.core.model.TrafficStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@Serializable
data class SessionLock(
    val pid: Long,
    val profileId: String,
    val mode: String,
    val startTimeMs: Long = System.currentTimeMillis()
)

class WindowsCoreController(
    val singBoxBinary: File = resolveDefaultSingBoxBinary(),
    val workingDir: File = resolveDefaultWorkingDir(),
    val systemProxy: WindowsSystemProxy = WindowsSystemProxy(workingDir),
    val clashApiPort: Int = 9090
) : CoreController {

    companion object {
        fun resolveDefaultWorkingDir(): File {
            val appData = System.getenv("APPDATA") ?: System.getProperty("user.home")
            val dir = File(appData, "Nebula")
            dir.mkdirs()
            return dir
        }

        fun resolveDefaultSingBoxBinary(): File {
            // Check local tools folder, then workingDir, then current directory
            val candidates = listOf(
                File("C:\\Users\\User\\tools\\sing-box.exe"),
                File(resolveDefaultWorkingDir(), "sing-box.exe"),
                File("tools\\sing-box.exe"),
                File("sing-box.exe")
            )
            for (c in candidates) {
                if (c.exists() && c.isFile) return c
            }
            return File(resolveDefaultWorkingDir(), "sing-box.exe")
        }
    }

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _trafficStats = MutableStateFlow(TrafficStats())
    override val trafficStats: StateFlow<TrafficStats> = _trafficStats.asStateFlow()

    private var childProcess: Process? = null
    private var statsJob: Job? = null
    private val isStopping = AtomicBoolean(false)

    private val logBufferLock = Any()
    private val logRingBuffer = ArrayDeque<String>(120)

    val activeConfigFile: File = File(workingDir, "active_config.json")
    val sessionFile: File = File(workingDir, "active_session.json")

    init {
        cleanupStaleSession()
    }

    fun getRecentLogs(): List<String> {
        synchronized(logBufferLock) {
            return logRingBuffer.toList()
        }
    }

    private fun appendLog(line: String) {
        synchronized(logBufferLock) {
            if (logRingBuffer.size >= 100) {
                logRingBuffer.removeFirst()
            }
            logRingBuffer.addLast(line)
        }
    }

    override fun isRunning(): Boolean {
        return childProcess?.isAlive == true
    }

    override suspend fun start(profile: ProxyProfile, configJson: String): Result<Unit> {
        return try {
            if (isRunning()) {
                stop()
            }
            isStopping.set(false)
            _connectionState.value = ConnectionState.Connecting

            if (!singBoxBinary.exists()) {
                throw IllegalStateException("sing-box binary not found at ${singBoxBinary.absolutePath}")
            }

            // 1. Run Pre-flight syntax and routing check
            val tempCheckFile = File.createTempFile("singbox_chk_", ".json", workingDir)
            try {
                tempCheckFile.writeText(configJson)
                val checkProcess = ProcessBuilder(singBoxBinary.absolutePath, "check", "-c", tempCheckFile.absolutePath)
                    .redirectErrorStream(true)
                    .start()
                val checkOutput = checkProcess.inputStream.bufferedReader().readText()
                val checkExit = checkProcess.waitFor()
                if (checkExit != 0) {
                    throw IllegalStateException("sing-box configuration check failed (exit code $checkExit): $checkOutput")
                }
            } finally {
                tempCheckFile.delete()
            }

            // 2. Write verified config to working directory
            activeConfigFile.writeText(configJson)

            // 3. Determine if running in System Proxy Mode or TUN Mode
            val isTun = configJson.contains("\"type\": \"tun\"") || configJson.contains("\"type\":\"tun\"")
            val modeStr = if (isTun) "TUN" else "SYSTEM_PROXY"

            // 4. In System Proxy mode, enable Windows WinINET system proxy
            if (!isTun) {
                // Parse mixed inbound port from config or default to 2080
                val parsedPort = try {
                    val root = json.decodeFromString<JsonObject>(configJson)
                    val inbounds = root["inbounds"]?.jsonArray
                    val mixedIn = inbounds?.mapNotNull { it.jsonObject }?.firstOrNull {
                        it["type"]?.jsonPrimitive?.contentOrNull == "mixed"
                    }
                    mixedIn?.get("listen_port")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 2080
                } catch (_: Exception) {
                    2080
                }
                systemProxy.enableProxy("127.0.0.1", parsedPort)
            }

            // 5. Spawn child process
            val pb = ProcessBuilder(singBoxBinary.absolutePath, "run", "-c", activeConfigFile.absolutePath)
            pb.directory(workingDir)
            pb.redirectErrorStream(true)
            val proc = pb.start()
            childProcess = proc

            val pid = proc.pid()
            sessionFile.writeText(json.encodeToString(SessionLock(pid, profile.id, modeStr)))

            // 6. Log stream reader thread
            Thread({
                try {
                    val reader = BufferedReader(InputStreamReader(proc.inputStream))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        line?.let { appendLog(it) }
                    }
                } catch (_: Exception) {}
            }, "SingBox-LogReader-$pid").start()

            // 7. Verify process didn't die immediately on start
            delay(350)
            if (!proc.isAlive) {
                val exit = proc.exitValue()
                val logs = getRecentLogs().takeLast(10).joinToString("\n")
                if (!isTun) {
                    systemProxy.disableProxy()
                }
                sessionFile.delete()
                throw IllegalStateException("sing-box process exited immediately with code $exit:\n$logs")
            }

            // 8. Process exit monitor
            proc.onExit().thenAccept { deadProc ->
                if (!isStopping.get()) {
                    appendLog("Sing-box process terminated unexpectedly with code ${deadProc.exitValue()}")
                    if (!isTun) {
                        systemProxy.disableProxy()
                    }
                    sessionFile.delete()
                    _connectionState.value = ConnectionState.Failed(
                        "Connection terminated unexpectedly (code ${deadProc.exitValue()})"
                    )
                    statsJob?.cancel()
                }
            }

            // 9. Start traffic stats polling
            startStatsPolling()

            _connectionState.value = ConnectionState.Connected(profileName = profile.name)
            Result.success(Unit)
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Failed(e.message ?: "Failed to start sing-box")
            Result.failure(e)
        }
    }

    override suspend fun stop(): Result<Unit> {
        return try {
            isStopping.set(true)
            _connectionState.value = ConnectionState.Disconnecting
            statsJob?.cancel()

            val proc = childProcess
            if (proc != null && proc.isAlive) {
                proc.destroy()
                val terminated = proc.waitFor(3, TimeUnit.SECONDS)
                if (!terminated) {
                    proc.destroyForcibly()
                }
            }
            childProcess = null

            // Clean up system proxy and session lock
            systemProxy.disableProxy()
            sessionFile.delete()

            _connectionState.value = ConnectionState.Disconnected
            _trafficStats.value = TrafficStats()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            isStopping.set(false)
        }
    }

    override suspend fun reloadConfig(newConfigJson: String): Result<Unit> {
        return try {
            // Write to temp file and pre-check
            val tempCheckFile = File.createTempFile("singbox_chk_", ".json", workingDir)
            try {
                tempCheckFile.writeText(newConfigJson)
                val checkProcess = ProcessBuilder(singBoxBinary.absolutePath, "check", "-c", tempCheckFile.absolutePath)
                    .redirectErrorStream(true)
                    .start()
                val checkExit = checkProcess.waitFor()
                if (checkExit != 0) {
                    val out = checkProcess.inputStream.bufferedReader().readText()
                    throw IllegalStateException("New configuration check failed: $out")
                }
            } finally {
                tempCheckFile.delete()
            }

            // Stop current process and restart
            val currentProfile = ProxyProfile(
                id = "reloaded",
                name = "Reloaded",
                protocol = app.nebula.vpn.core.model.ProtocolType.VLESS,
                server = "127.0.0.1",
                port = 443,
                authSecret = "00000000-0000-0000-0000-000000000000"
            )
            start(currentProfile, newConfigJson)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun startStatsPolling() {
        statsJob?.cancel()
        statsJob = scope.launch {
            var lastDownload: Long = 0
            var lastUpload: Long = 0
            var lastTimeMs: Long = System.currentTimeMillis()

            while (isActive && isRunning()) {
                try {
                    val url = URL("http://127.0.0.1:$clashApiPort/connections")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 800
                    conn.readTimeout = 800
                    conn.requestMethod = "GET"

                    if (conn.responseCode == 200) {
                        val body = conn.inputStream.bufferedReader().readText()
                        val root = json.decodeFromString<JsonObject>(body)
                        val dlTotal = root["downloadTotal"]?.jsonPrimitive?.longOrNull ?: 0L
                        val ulTotal = root["uploadTotal"]?.jsonPrimitive?.longOrNull ?: 0L
                        val connsArray = root["connections"]?.jsonArray
                        val activeCount = connsArray?.size ?: 0

                        val now = System.currentTimeMillis()
                        val elapsedSec = ((now - lastTimeMs) / 1000.0).coerceAtLeast(0.5)

                        val dlSpeed = if (lastDownload > 0 && dlTotal >= lastDownload) {
                            ((dlTotal - lastDownload) / elapsedSec).toLong()
                        } else 0L

                        val ulSpeed = if (lastUpload > 0 && ulTotal >= lastUpload) {
                            ((ulTotal - lastUpload) / elapsedSec).toLong()
                        } else 0L

                        lastDownload = dlTotal
                        lastUpload = ulTotal
                        lastTimeMs = now

                        _trafficStats.value = TrafficStats(
                            uploadSpeedBps = ulSpeed,
                            downloadSpeedBps = dlSpeed,
                            totalUploadBytes = ulTotal,
                            totalDownloadBytes = dlTotal,
                            memoryUsageBytes = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory(),
                            activeConnections = activeCount
                        )
                    }
                    conn.disconnect()
                } catch (_: Exception) {
                    // Ignore transient network poll failures
                }
                delay(1000)
            }
        }
    }

    @Synchronized
    fun cleanupStaleSession() {
        try {
            if (sessionFile.exists()) {
                val lock = try {
                    json.decodeFromString<SessionLock>(sessionFile.readText())
                } catch (_: Exception) { null }

                if (lock != null && lock.pid > 0) {
                    ProcessHandle.of(lock.pid).ifPresent { handle ->
                        try {
                            handle.destroyForcibly()
                        } catch (_: Exception) {}
                    }
                }
                sessionFile.delete()
            }
            systemProxy.cleanupStaleState()
        } catch (e: Exception) {
            System.err.println("Warning: Error during stale session cleanup: ${e.message}")
        }
    }
}
