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

object XrayJsonParser {

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
            val outboundsToInspect = mutableListOf<JsonObject>()

            when (rootElement) {
                is JsonObject -> {
                    val outbounds = rootElement["outbounds"]?.jsonArray
                    if (outbounds != null) {
                        for (item in outbounds) {
                            (item as? JsonObject)?.let { outboundsToInspect.add(it) }
                        }
                    } else if (rootElement.containsKey("protocol")) {
                        outboundsToInspect.add(rootElement)
                    }
                }
                is JsonArray -> {
                    for (item in rootElement) {
                        when (item) {
                            is JsonObject -> {
                                val subOutbounds = item["outbounds"]?.jsonArray
                                if (subOutbounds != null) {
                                    for (subItem in subOutbounds) {
                                        (subItem as? JsonObject)?.let { outboundsToInspect.add(it) }
                                    }
                                } else if (item.containsKey("protocol")) {
                                    outboundsToInspect.add(item)
                                }
                            }
                            else -> {}
                        }
                    }
                }
                else -> return Result.Error(IllegalArgumentException("Invalid JSON root: expected object or array"))
            }

            if (outboundsToInspect.isEmpty()) {
                return Result.Error(IllegalArgumentException("No Xray outbounds found in JSON"))
            }

            val profiles = mutableListOf<ProxyProfile>()
            val skipped = mutableListOf<Pair<String, String>>()
            var total = 0

            val ignoredProtocols = setOf("freedom", "blackhole", "dns", "loopback")

            for (outbound in outboundsToInspect) {
                val protocolStr = outbound.str("protocol")?.lowercase() ?: continue
                val tag = outbound.str("tag") ?: "Xray-${profiles.size + 1}"

                if (protocolStr in ignoredProtocols) {
                    continue
                }

                total++
                val parsedProfiles = parseOutbound(outbound, protocolStr, tag)
                if (parsedProfiles.isNotEmpty()) {
                    profiles.addAll(parsedProfiles)
                } else {
                    skipped.add(tag to "Unsupported or empty Xray outbound for protocol: $protocolStr")
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

    private fun parseOutbound(outbound: JsonObject, protocolStr: String, tag: String): List<ProxyProfile> {
        val settings = outbound["settings"]?.jsonObject ?: return emptyList()
        val streamSettings = outbound["streamSettings"]?.jsonObject

        val protocol = when (protocolStr) {
            "vless" -> ProtocolType.VLESS
            "vmess" -> ProtocolType.VMESS
            "trojan" -> ProtocolType.TROJAN
            "shadowsocks" -> ProtocolType.SHADOWSOCKS
            else -> return emptyList()
        }

        // Parse Stream Settings (Transport & TLS)
        val network = streamSettings?.str("network")?.lowercase() ?: "tcp"
        val security = streamSettings?.str("security")?.lowercase() ?: "none"

        val transportType = when (network) {
            "ws", "websocket" -> TransportType.WS
            "grpc" -> TransportType.GRPC
            "httpupgrade", "xhttp", "splithttp" -> TransportType.HTTP_UPGRADE
            else -> TransportType.TCP
        }

        var path = ""
        var hostHeader = ""
        var serviceName = ""

        when (transportType) {
            TransportType.WS -> {
                val wsSettings = streamSettings?.get("wsSettings")?.jsonObject
                path = wsSettings?.str("path") ?: ""
                val headers = wsSettings?.get("headers")?.jsonObject
                hostHeader = headers?.str("Host") ?: headers?.str("host") ?: ""
            }
            TransportType.GRPC -> {
                val grpcSettings = streamSettings?.get("grpcSettings")?.jsonObject
                serviceName = grpcSettings?.str("serviceName") ?: ""
            }
            TransportType.HTTP_UPGRADE -> {
                val huSettings = streamSettings?.get("httpupgradeSettings")?.jsonObject
                    ?: streamSettings?.get("xhttpSettings")?.jsonObject
                path = huSettings?.str("path") ?: ""
                hostHeader = huSettings?.str("host") ?: ""
            }
            TransportType.TCP -> {}
        }

        // TLS & Reality
        val isReality = security == "reality"
        val isTls = security == "tls" || isReality

        var serverName = ""
        var insecure = false
        var alpnList = emptyList<String>()
        var utlsFp = "chrome"
        var realityPubKey = ""
        var realityShortId = ""

        if (isReality) {
            val realityObj = streamSettings?.get("realitySettings")?.jsonObject
            serverName = realityObj?.str("serverName") ?: ""
            realityPubKey = realityObj?.str("publicKey") ?: ""
            realityShortId = realityObj?.str("shortId") ?: ""
            utlsFp = realityObj?.str("fingerprint") ?: "chrome"
        } else if (isTls) {
            val tlsObj = streamSettings?.get("tlsSettings")?.jsonObject
            serverName = tlsObj?.str("serverName") ?: ""
            insecure = tlsObj?.bool("allowInsecure") ?: false
            alpnList = tlsObj?.get("alpn")?.jsonArray?.mapNotNull { it.jsonPrimitive.content } ?: emptyList()
            utlsFp = tlsObj?.str("fingerprint") ?: "chrome"
        }

        val result = mutableListOf<ProxyProfile>()

        if (protocol == ProtocolType.VLESS || protocol == ProtocolType.VMESS) {
            val vnextList = settings["vnext"]?.jsonArray ?: return emptyList()
            for (vnextElem in vnextList) {
                val vnext = vnextElem.jsonObject
                val address = vnext.str("address") ?: continue
                val port = vnext.int("port") ?: 443
                val users = vnext["users"]?.jsonArray ?: continue

                for (userElem in users) {
                    val user = userElem.jsonObject
                    val uuid = user.str("id") ?: continue
                    val flow = user.str("flow") ?: ""
                    val encryption = user.str("encryption") ?: if (protocol == ProtocolType.VMESS) "auto" else "none"

                    val tlsConfig = TlsConfig(
                        enabled = isTls,
                        serverName = serverName.ifBlank { address },
                        insecure = insecure,
                        alpn = alpnList,
                        utlsFingerprint = utlsFp,
                        realityPublicKey = realityPubKey,
                        realityShortId = realityShortId,
                        flow = flow
                    )

                    val transportConfig = TransportConfig(
                        type = transportType,
                        path = path,
                        host = hostHeader,
                        serviceName = serviceName
                    )

                    result.add(
                        ProxyProfile(
                            id = UUID.randomUUID().toString(),
                            name = tag,
                            protocol = protocol,
                            server = address,
                            port = port,
                            authSecret = uuid,
                            encryption = encryption,
                            tls = tlsConfig,
                            transport = transportConfig
                        )
                    )
                }
            }
        } else if (protocol == ProtocolType.TROJAN) {
            val servers = settings["servers"]?.jsonArray ?: return emptyList()
            for (sElem in servers) {
                val s = sElem.jsonObject
                val address = s.str("address") ?: continue
                val port = s.int("port") ?: 443
                val password = s.str("password") ?: continue

                val tlsConfig = TlsConfig(
                    enabled = true,
                    serverName = serverName.ifBlank { address },
                    insecure = insecure,
                    alpn = alpnList,
                    utlsFingerprint = utlsFp
                )

                val transportConfig = TransportConfig(
                    type = transportType,
                    path = path,
                    host = hostHeader,
                    serviceName = serviceName
                )

                result.add(
                    ProxyProfile(
                        id = UUID.randomUUID().toString(),
                        name = tag,
                        protocol = ProtocolType.TROJAN,
                        server = address,
                        port = port,
                        authSecret = password,
                        tls = tlsConfig,
                        transport = transportConfig
                    )
                )
            }
        } else if (protocol == ProtocolType.SHADOWSOCKS) {
            val servers = settings["servers"]?.jsonArray ?: return emptyList()
            for (sElem in servers) {
                val s = sElem.jsonObject
                val address = s.str("address") ?: continue
                val port = s.int("port") ?: 8388
                val password = s.str("password") ?: continue
                val method = s.str("method") ?: "aes-128-gcm"

                result.add(
                    ProxyProfile(
                        id = UUID.randomUUID().toString(),
                        name = tag,
                        protocol = ProtocolType.SHADOWSOCKS,
                        server = address,
                        port = port,
                        authSecret = "$method:$password",
                        encryption = method
                    )
                )
            }
        }

        return result
    }
}
