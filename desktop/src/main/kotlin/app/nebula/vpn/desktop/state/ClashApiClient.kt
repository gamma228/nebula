package app.nebula.vpn.desktop.state

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class ConnectionMetadata(
    val network: String = "",
    val type: String = "",
    val sourceIP: String = "",
    val destinationIP: String = "",
    val destinationPort: String = "",
    val host: String = "",
    val processPath: String = ""
)

@Serializable
data class ActiveConnection(
    val id: String,
    val metadata: ConnectionMetadata = ConnectionMetadata(),
    val upload: Long = 0,
    val download: Long = 0,
    val start: String = "",
    val chains: List<String> = emptyList(),
    val rule: String = ""
)

@Serializable
data class ConnectionsSnapshot(
    val downloadTotal: Long = 0,
    val uploadTotal: Long = 0,
    val connections: List<ActiveConnection> = emptyList()
)

object ClashApiClient {
    private val json = Json { ignoreUnknownKeys = true }

    fun fetchConnections(port: Int = 9090): ConnectionsSnapshot? {
        return try {
            val url = URL("http://127.0.0.1:$port/connections")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 1500
                readTimeout = 1500
                requestMethod = "GET"
            }
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().readText()
                json.decodeFromString<ConnectionsSnapshot>(body)
            } else null
        } catch (_: Exception) {
            null
        }
    }

    fun closeConnection(id: String, port: Int = 9090): Boolean {
        return try {
            val url = URL("http://127.0.0.1:$port/connections/$id")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 1500
                readTimeout = 1500
                requestMethod = "DELETE"
            }
            conn.responseCode in 200..299
        } catch (_: Exception) {
            false
        }
    }

    fun closeAllConnections(port: Int = 9090): Boolean {
        return try {
            val url = URL("http://127.0.0.1:$port/connections")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 1500
                readTimeout = 1500
                requestMethod = "DELETE"
            }
            conn.responseCode in 200..299
        } catch (_: Exception) {
            false
        }
    }
}
