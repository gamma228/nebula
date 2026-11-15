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
) {
    val isStub: Boolean
        get() = server == "0.0.0.0" || (server == "127.0.0.1" && port == 1) || name.contains("HWID", ignoreCase = true) || name.contains("⚠️") || name.contains("не поддерживается", ignoreCase = true)

    fun toShareLink(): String {
        val encodedName = java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20")
        return when (protocol) {
            ProtocolType.VLESS -> {
                val params = mutableListOf<String>()
                val transportStr = when (transport.type) {
                    TransportType.WS -> "ws"
                    TransportType.GRPC -> "grpc"
                    TransportType.HTTP_UPGRADE -> "httpupgrade"
                    TransportType.TCP -> "tcp"
                }
                params.add("type=$transportStr")
                if (tls.enabled) {
                    if (tls.realityPublicKey.isNotEmpty()) {
                        params.add("security=reality")
                        params.add("pbk=${tls.realityPublicKey}")
                        if (tls.realityShortId.isNotEmpty()) params.add("sid=${tls.realityShortId}")
                        if (tls.flow.isNotEmpty()) params.add("flow=${tls.flow}")
                    } else {
                        params.add("security=tls")
                    }
                    if (tls.serverName.isNotEmpty()) params.add("sni=${tls.serverName}")
                    if (tls.utlsFingerprint.isNotEmpty()) params.add("fp=${tls.utlsFingerprint}")
                    if (tls.alpn.isNotEmpty()) params.add("alpn=${tls.alpn.joinToString(",")}")
                } else {
                    params.add("security=none")
                }
                if (transport.type == TransportType.WS) {
                    if (transport.path.isNotEmpty()) params.add("path=${java.net.URLEncoder.encode(transport.path, "UTF-8")}")
                    if (transport.host.isNotEmpty()) params.add("host=${transport.host}")
                } else if (transport.type == TransportType.GRPC) {
                    if (transport.serviceName.isNotEmpty()) params.add("serviceName=${transport.serviceName}")
                }
                "vless://$authSecret@$server:$port?${params.joinToString("&")}#$encodedName"
            }
            ProtocolType.VMESS -> {
                val json = buildString {
                    append("{\"v\":\"2\",\"ps\":\"$name\",\"add\":\"$server\",\"port\":$port,\"id\":\"$authSecret\",")
                    append("\"aid\":0,\"scy\":\"${encryption.ifEmpty { "auto" }}\",\"net\":\"${transport.type.name.lowercase()}\",")
                    append("\"type\":\"none\",\"host\":\"${transport.host}\",\"path\":\"${transport.path}\",\"tls\":\"${if (tls.enabled) "tls" else "none"}\"}")
                }
                val base64 = java.util.Base64.getEncoder().encodeToString(json.toByteArray(Charsets.UTF_8))
                "vmess://$base64"
            }
            ProtocolType.TROJAN -> {
                val params = mutableListOf<String>()
                if (tls.enabled && tls.serverName.isNotEmpty()) params.add("sni=${tls.serverName}")
                val query = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
                "trojan://$authSecret@$server:$port$query#$encodedName"
            }
            ProtocolType.SHADOWSOCKS -> {
                val raw = if (authSecret.contains(":")) authSecret else "${encryption.ifEmpty { "aes-128-gcm" }}:$authSecret"
                val base64 = java.util.Base64.getEncoder().encodeToString(raw.toByteArray(Charsets.UTF_8))
                "ss://$base64@$server:$port#$encodedName"
            }
            ProtocolType.HYSTERIA2 -> {
                val params = mutableListOf<String>()
                if (tls.serverName.isNotEmpty()) params.add("sni=${tls.serverName}")
                if (tls.insecure) params.add("insecure=1")
                val query = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
                "hy2://$authSecret@$server:$port$query#$encodedName"
            }
            ProtocolType.TUIC -> {
                val params = mutableListOf<String>()
                if (tls.serverName.isNotEmpty()) params.add("sni=${tls.serverName}")
                params.add("alpn=h3")
                "tuic://$authSecret@$server:$port?${params.joinToString("&")}#$encodedName"
            }
        }
    }
}
