package app.nebula.vpn.core.proxy

import app.nebula.vpn.core.model.ProxyProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

sealed interface ServerTestState {
    data object Pending : ServerTestState
    data object Testing : ServerTestState
    data class Ok(val latencyMs: Long, val method: ProbeMethod) : ServerTestState
    data object Timeout : ServerTestState
    data class Error(val message: String) : ServerTestState
}

enum class ProbeMethod {
    TCP_HANDSHAKE,
    TLS_HANDSHAKE,
    HTTP_GET
}

data class BatchTestProgress(
    val completed: Int = 0,
    val total: Int = 0,
    val isRunning: Boolean = false,
    val currentServerStates: Map<String, ServerTestState> = emptyMap()
)

interface LatencyProbe {
    suspend fun probe(profile: ProxyProfile, method: ProbeMethod, timeoutMs: Long): Long
}

@Singleton
class DefaultLatencyProbe @Inject constructor() : LatencyProbe {
    override suspend fun probe(profile: ProxyProfile, method: ProbeMethod, timeoutMs: Long): Long = withContext(Dispatchers.IO) {
        val tMs = timeoutMs.toInt().coerceAtLeast(100)
        when (method) {
            ProbeMethod.TCP_HANDSHAKE -> {
                val start = System.currentTimeMillis()
                Socket().use { socket ->
                    socket.tcpNoDelay = true
                    socket.soTimeout = tMs
                    socket.connect(InetSocketAddress(profile.server, profile.port), tMs)
                }
                (System.currentTimeMillis() - start).coerceAtLeast(1L)
            }
            ProbeMethod.TLS_HANDSHAKE -> {
                val start = System.currentTimeMillis()
                Socket().use { baseSocket ->
                    baseSocket.tcpNoDelay = true
                    baseSocket.soTimeout = tMs
                    baseSocket.connect(InetSocketAddress(profile.server, profile.port), tMs)

                    val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
                    (sslFactory.createSocket(baseSocket, profile.tls.serverName.ifEmpty { profile.server }, profile.port, true) as SSLSocket).use { sslSocket ->
                        sslSocket.soTimeout = tMs
                        val sni = profile.tls.serverName.ifEmpty { profile.server }
                        if (sni.isNotEmpty()) {
                            val params = sslSocket.sslParameters ?: SSLParameters()
                            params.serverNames = listOf(SNIHostName(sni))
                            sslSocket.sslParameters = params
                        }
                        sslSocket.startHandshake()
                    }
                }
                (System.currentTimeMillis() - start).coerceAtLeast(1L)
            }
            ProbeMethod.HTTP_GET -> {
                val start = System.currentTimeMillis()
                val url = URL("https://cp.cloudflare.com/generate_204")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = tMs
                conn.readTimeout = tMs
                conn.instanceFollowRedirects = false
                conn.requestMethod = "GET"
                try {
                    conn.connect()
                    val code = conn.responseCode
                    if (code in 200..399) {
                        (System.currentTimeMillis() - start).coerceAtLeast(1L)
                    } else {
                        throw RuntimeException("HTTP $code")
                    }
                } finally {
                    conn.disconnect()
                }
            }
        }
    }
}

interface LatencyTester {
    val progress: StateFlow<BatchTestProgress>
    suspend fun testBatch(
        profiles: List<ProxyProfile>,
        method: ProbeMethod = ProbeMethod.TCP_HANDSHAKE,
        perServerTimeoutMs: Long = 5000L,
        globalTimeoutMs: Long = 30000L
    )
    fun cancel()
}

@Singleton
class DefaultLatencyTester(
    private val probe: LatencyProbe,
    private val dispatcher: CoroutineDispatcher,
    private val parallelism: Int,
    private val perServerTimeoutMs: Long,
    private val globalTimeoutMs: Long
) : LatencyTester {

    @Inject
    constructor(probe: LatencyProbe) : this(
        probe = probe,
        dispatcher = Dispatchers.IO,
        parallelism = 12,
        perServerTimeoutMs = 1500L,
        globalTimeoutMs = 20000L
    )

    private val _progress = MutableStateFlow(BatchTestProgress())
    override val progress: StateFlow<BatchTestProgress> = _progress.asStateFlow()

    private var activeBatchJob: Job? = null

    override suspend fun testBatch(
        profiles: List<ProxyProfile>,
        method: ProbeMethod,
        perServerTimeoutMs: Long,
        globalTimeoutMs: Long
    ): Unit = withContext(dispatcher) {
        cancel() // Cancel any ongoing batch

        val initialStates: MutableMap<String, ServerTestState> = profiles.associate { 
            it.id to (ServerTestState.Pending as ServerTestState) 
        }.toMutableMap()
        _progress.value = BatchTestProgress(
            completed = 0,
            total = profiles.size,
            isRunning = true,
            currentServerStates = initialStates
        )

        val semaphore = Semaphore(parallelism)
        var completedCount = 0
        var lastEmitTime = 0L

        fun maybeEmitProgress(force: Boolean = false) {
            val now = System.currentTimeMillis()
            if (force || now - lastEmitTime >= 150L) {
                lastEmitTime = now
                _progress.value = BatchTestProgress(
                    completed = completedCount,
                    total = profiles.size,
                    isRunning = true,
                    currentServerStates = HashMap(initialStates)
                )
            }
        }

        val job = coroutineContext[Job]
        activeBatchJob = job

        try {
            withTimeoutOrNull(globalTimeoutMs) {
                profiles.map { profile ->
                    async(dispatcher) {
                        if (!isActive) return@async
                        semaphore.withPermit {
                            if (!isActive) return@withPermit
                            synchronized(initialStates) {
                                initialStates[profile.id] = ServerTestState.Testing
                                maybeEmitProgress(force = false)
                            }

                            val state = try {
                                withTimeout(perServerTimeoutMs) {
                                    val ms = probe.probe(profile, method, perServerTimeoutMs)
                                    if (ms >= 0) ServerTestState.Ok(ms, method) else ServerTestState.Timeout
                                }
                            } catch (e: TimeoutCancellationException) {
                                ServerTestState.Timeout
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                ServerTestState.Error(e.message ?: "Failed")
                            }

                            synchronized(initialStates) {
                                initialStates[profile.id] = state
                                completedCount++
                                maybeEmitProgress(force = false)
                            }
                        }
                    }
                }.awaitAll()
            }
        } finally {
            synchronized(initialStates) {
                // Ensure no server remains in Testing state
                initialStates.forEach { (id, state) ->
                    if (state is ServerTestState.Testing) {
                        initialStates[id] = ServerTestState.Timeout
                    }
                }
                _progress.value = _progress.value.copy(
                    isRunning = false,
                    completed = completedCount,
                    currentServerStates = HashMap(initialStates)
                )
            }
            activeBatchJob = null
        }
        Unit
    }

    override fun cancel() {
        activeBatchJob?.cancel()
        activeBatchJob = null
        val current = _progress.value
        val cleaned = current.currentServerStates.mapValues { (_, state) ->
            if (state is ServerTestState.Testing) ServerTestState.Timeout else state
        }
        _progress.value = current.copy(isRunning = false, currentServerStates = cleaned)
    }
}
