package app.nebula.vpn.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ProtocolType {
    VLESS,
    VMESS,
    TROJAN,
    SHADOWSOCKS,
    HYSTERIA2,
    TUIC
}

@Serializable
enum class TransportType {
    TCP,
    WS,
    GRPC,
    HTTP_UPGRADE
}

@Serializable
data class TlsConfig(
    val enabled: Boolean = false,
    val serverName: String = "",
    val insecure: Boolean = false,
    val alpn: List<String> = emptyList(),
    val utlsFingerprint: String = "chrome",
    val realityPublicKey: String = "",
    val realityShortId: String = "",
    val spiderX: String = "",
    val flow: String = ""
)

@Serializable
data class TransportConfig(
    val type: TransportType = TransportType.TCP,
    val path: String = "",
    val host: String = "",
    val serviceName: String = "",
    val mode: String = "gun",
    val authority: String = ""
)

@Serializable
data class ProxyProfile(
    val id: String,
    val name: String,
    val protocol: ProtocolType,
    val server: String,
    val port: Int,
    val authSecret: String, // UUID, password, or SS method:password
    val encryption: String = "none", // SS cipher or VMess security
    val tls: TlsConfig = TlsConfig(),
    val transport: TransportConfig = TransportConfig(),
    val packetEncoding: String = "xudp",
    val headerType: String = "",
    val detour: String? = null,
    val subscriptionId: String? = null,
    val isFavorite: Boolean = false,
    val lastPingMs: Long? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
