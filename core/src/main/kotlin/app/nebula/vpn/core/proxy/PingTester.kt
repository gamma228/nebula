package app.nebula.vpn.core.proxy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

enum class PingType {
    TCP,
    HTTP
}

@Singleton
class PingTester @Inject constructor() {

    /**
     * Measures round-trip latency to a server's TCP socket port.
     * Returns latency in milliseconds, or -1 if unreachable/timeout.
     */
    suspend fun pingTcp(host: String, port: Int, timeoutMs: Int = 1500): Long = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            app.nebula.vpn.core.tor.NetworkPolicy.checkAllowed(host, port)
            Socket().use { socket ->
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress(host, port), timeoutMs)
            }
            (System.currentTimeMillis() - startTime).coerceAtLeast(1L)
        } catch (e: Exception) {
            -1L
        }
    }

    /**
     * Measures HTTP response latency to a generate_204 endpoint.
     * Returns latency in milliseconds, or -1 if unreachable/timeout.
     */
    suspend fun pingHttp(urlStr: String = "https://cp.cloudflare.com/generate_204", timeoutMs: Int = 1800): Long = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val url = URL(urlStr)
            val port = if (url.port > 0) url.port else url.defaultPort
            app.nebula.vpn.core.tor.NetworkPolicy.checkAllowed(url.host, port)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.instanceFollowRedirects = false
            conn.requestMethod = "GET"
            conn.connect()
            val code = conn.responseCode
            conn.disconnect()
            if (code in 200..399) {
                (System.currentTimeMillis() - startTime).coerceAtLeast(1L)
            } else {
                -1L
            }
        } catch (e: Exception) {
            -1L
        }
    }
}
