package app.nebula.vpn.core.parser

import app.nebula.vpn.core.model.ProtocolType
import app.nebula.vpn.core.model.ProxyProfile
import app.nebula.vpn.core.model.Result
import app.nebula.vpn.core.model.TlsConfig
import app.nebula.vpn.core.model.TransportConfig
import app.nebula.vpn.core.model.TransportType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

object SingBoxJsonParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    data class ParseResult(
        val profiles: List<ProxyProfile>,
        val totalCount: Int,
        val skippedCount: Int,
        val skippedReasons: List<Pair<String, String>>
    )

    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.content
    private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.content?.toIntOrNull()
    private fun JsonObject.bool(key: String): Boolean = this[key]?.jsonPrimitive?.content?.equals("true", true) == true

    fun parse(content: String): Result<ParseResult> {
        return try {
            val rootElement = json.parseToJsonElement(content.trim())
            val outboundsArray: JsonArray = when (rootElement) {
                is JsonObject -> rootElement["outbounds"]?.jsonArray
                    ?: return Result.Error(IllegalArgumentException("sing-box JSON missing 'outbounds' array"))
                is JsonArray -> rootElement
                else -> return Result.Error(IllegalArgumentException("Invalid JSON root: expected object or array"))
            }

            val profiles = mutableListOf<ProxyProfile>()
            val skipped = mutableListOf<Pair<String, String>>()
            var total = 0

            val ignoredHelperTypes = setOf("selector", "urltest", "direct", "block", "dns")

            for (elem in outboundsArray) {
                val obj = elem as? JsonObject ?: continue
                val type = obj.str("type")?.lowercase() ?: continue
                val tag = obj.str("tag") ?: "Node-${profiles.size + 1}"

                if (type in ignoredHelperTypes) {
                    // Not a proxy outbound (routing helper)
                    continue
                }

                total++
                val profile = parseOutbound(obj, type, tag)
                if (profile != null) {
                    profiles.add(profile)
                } else {
                    skipped.add(tag to "Unsupported outbound configuration or type: $type")
                }
            }

            Result.Success(
                ParseResult(
                    profiles = profiles,
                    totalCount = total,
                    skippedCount = skipped.size,
                    skippedReasons = skipped
                )
            )
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    private fun parseOutbound(obj: JsonObject, type: String, tag: String): ProxyProfile? {
        val server = obj.str("server") ?: ""
        val port = obj.int("server_port") ?: 443

        val protocol = when (type) {
            "vless" -> ProtocolType.VLESS
            "vmess" -> ProtocolType.VMESS
            "trojan" -> ProtocolType.TROJAN
            "shadowsocks" -> ProtocolType.SHADOWSOCKS
            "hysteria2" -> ProtocolType.HYSTERIA2
            "tuic" -> ProtocolType.TUIC
            else -> return null
        }

        val authSecret = when (protocol) {
            ProtocolType.VLESS, ProtocolType.VMESS -> obj.str("uuid") ?: ""
            ProtocolType.TROJAN, ProtocolType.HYSTERIA2 -> obj.str("password") ?: ""
            ProtocolType.TUIC -> obj.str("uuid") ?: obj.str("password") ?: ""
            ProtocolType.SHADOWSOCKS -> {
                val method = obj.str("method") ?: "aes-128-gcm"
                val password = obj.str("password") ?: ""
                "$method:$password"
            }
        }

        val flow = obj.str("flow") ?: ""
        val packetEncoding = obj.str("packet_encoding") ?: "xudp"

        // TLS Parsing
        val tlsObj = obj["tls"]?.jsonObject
        val tlsEnabled = if (tlsObj != null) {
            tlsObj.str("enabled")?.equals("true", true) ?: true
        } else {
            protocol == ProtocolType.HYSTERIA2 || protocol == ProtocolType.TUIC || protocol == ProtocolType.TROJAN
        }

        val serverName = tlsObj?.str("server_name") ?: ""
        val insecure = tlsObj?.bool("insecure") ?: false
        val alpnList = tlsObj?.get("alpn")?.jsonArray?.mapNotNull { it.jsonPrimitive.content } ?: emptyList()

        val utlsObj = tlsObj?.get("utls")?.jsonObject
        val utlsFp = utlsObj?.str("fingerprint") ?: "chrome"

        val realityObj = tlsObj?.get("reality")?.jsonObject
        val realityEnabled = if (realityObj != null) realityObj.str("enabled")?.equals("true", true) ?: true else false
        val realityPubKey = if (realityEnabled) realityObj?.str("public_key") ?: "" else ""
        val realityShortId = if (realityEnabled) realityObj?.str("short_id") ?: "" else ""

        val tlsConfig = TlsConfig(
            enabled = tlsEnabled,
            serverName = serverName,
            insecure = insecure,
            alpn = alpnList,
            utlsFingerprint = utlsFp,
            realityPublicKey = realityPubKey,
            realityShortId = realityShortId,
            flow = flow
        )

        // Transport Parsing
        val transportObj = obj["transport"]?.jsonObject
        val transportTypeStr = transportObj?.str("type")?.lowercase() ?: "tcp"
        val transportType = when (transportTypeStr) {
            "ws", "websocket" -> TransportType.WS
            "grpc" -> TransportType.GRPC
            "httpupgrade", "xhttp", "splithttp" -> TransportType.HTTP_UPGRADE
            else -> TransportType.TCP
        }

        val path = transportObj?.str("path") ?: ""
        val headersObj = transportObj?.get("headers")?.jsonObject
        val hostHeader = headersObj?.str("Host") ?: headersObj?.str("host") ?: ""
        val serviceName = transportObj?.str("service_name") ?: ""

        val transportConfig = TransportConfig(
            type = transportType,
            path = path,
            host = hostHeader,
            serviceName = serviceName
        )

        val encryption = when (protocol) {
            ProtocolType.VMESS -> obj.str("security") ?: "auto"
            ProtocolType.SHADOWSOCKS -> obj.str("method") ?: "aes-128-gcm"
            else -> "none"
        }

        return ProxyProfile(
            id = UUID.randomUUID().toString(),
            name = tag,
            protocol = protocol,
            server = server,
            port = port,
            authSecret = authSecret,
            encryption = encryption,
            tls = tlsConfig,
            transport = transportConfig,
            packetEncoding = packetEncoding
        )
    }
}
