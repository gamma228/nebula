package app.nebula.vpn.core.parser

import app.nebula.vpn.core.model.ProtocolType
import app.nebula.vpn.core.model.ProxyProfile
import app.nebula.vpn.core.model.Result
import app.nebula.vpn.core.model.TlsConfig
import app.nebula.vpn.core.model.TransportConfig
import app.nebula.vpn.core.model.TransportType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLDecoder
import java.util.Base64
import java.util.UUID

object LinkParser {

    private val json = Json { ignoreUnknownKeys = true }

    private data class SimpleUri(
        val userInfo: String,
        val host: String,
        val port: Int,
        val queryParams: Map<String, String>,
        val fragment: String?
    ) {
        fun getQueryParameter(key: String): String? = queryParams[key]

        companion object {
            fun parse(raw: String): SimpleUri {
                val fragment = if (raw.contains("#")) raw.substringAfter("#") else null
                val withoutFrag = raw.substringBefore("#")
                val schemeIndex = withoutFrag.indexOf("://")
                val afterScheme = if (schemeIndex >= 0) withoutFrag.substring(schemeIndex + 3) else withoutFrag
                val queryStr = if (afterScheme.contains("?")) afterScheme.substringAfter("?") else ""
                val authority = afterScheme.substringBefore("?")

                val userInfo = if (authority.contains("@")) authority.substringBeforeLast("@") else ""
                val hostPort = if (authority.contains("@")) authority.substringAfterLast("@") else authority

                val host: String
                val port: Int
                if (hostPort.startsWith("[")) {
                    host = hostPort.substringAfter("[").substringBefore("]")
                    val afterBracket = hostPort.substringAfter("]")
                    port = if (afterBracket.startsWith(":")) afterBracket.substring(1).toIntOrNull() ?: -1 else -1
                } else if (hostPort.contains(":")) {
                    host = hostPort.substringBefore(":")
                    port = hostPort.substringAfter(":").toIntOrNull() ?: -1
                } else {
                    host = hostPort
                    port = -1
                }

                val queryMap = mutableMapOf<String, String>()
                if (queryStr.isNotEmpty()) {
                    for (param in queryStr.split("&")) {
                        val eq = param.indexOf('=')
                        if (eq >= 0) {
                            val k = URLDecoder.decode(param.substring(0, eq), "UTF-8")
                            val v = URLDecoder.decode(param.substring(eq + 1), "UTF-8")
                            queryMap[k] = v
                        } else if (param.isNotEmpty()) {
                            queryMap[URLDecoder.decode(param, "UTF-8")] = ""
                        }
                    }
                }

                return SimpleUri(userInfo, host, port, queryMap, fragment)
            }
        }
    }

    fun parse(rawLink: String): Result<ProxyProfile> = try {
        val trimmed = rawLink.trim()
        when {
            trimmed.startsWith("vless://", ignoreCase = true) -> parseVless(trimmed)
            trimmed.startsWith("vmess://", ignoreCase = true) -> parseVmess(trimmed)
            trimmed.startsWith("trojan://", ignoreCase = true) -> parseTrojan(trimmed)
            trimmed.startsWith("ss://", ignoreCase = true) -> parseShadowsocks(trimmed)
            trimmed.startsWith("hy2://", ignoreCase = true) || trimmed.startsWith("hysteria2://", ignoreCase = true) -> parseHysteria2(trimmed)
            else -> Result.Error(IllegalArgumentException("Unsupported link scheme in: $trimmed"))
        }
    } catch (e: Exception) {
        Result.Error(e, "Failed to parse link: ${e.message}")
    }

    private fun parseVless(link: String): Result<ProxyProfile> {
        val uri = SimpleUri.parse(link)
        val userInfo = uri.userInfo
        val host = uri.host
        val port = if (uri.port > 0) uri.port else 443
        val name = decodeFragment(uri.fragment, "VLESS-$host:$port")

        val security = uri.getQueryParameter("security") ?: "none"
        val isTls = security.equals("tls", true) || security.equals("reality", true)
        val sni = uri.getQueryParameter("sni") ?: host
        val pbk = uri.getQueryParameter("pbk") ?: ""
        val sid = uri.getQueryParameter("sid") ?: ""
        val fp = uri.getQueryParameter("fp") ?: "chrome"

        val typeParam = uri.getQueryParameter("type") ?: "tcp"
        val transportType = when (typeParam.lowercase()) {
            "ws" -> TransportType.WS
            "grpc" -> TransportType.GRPC
            "httpupgrade" -> TransportType.HTTP_UPGRADE
            else -> TransportType.TCP
        }
        val path = uri.getQueryParameter("path") ?: ""
        val serviceName = uri.getQueryParameter("serviceName") ?: ""

        val rawFlow = uri.getQueryParameter("flow") ?: ""
        val flow = when {
            rawFlow.isNotEmpty() -> rawFlow
            pbk.isNotEmpty() && transportType == TransportType.TCP -> "xtls-rprx-vision"
            else -> ""
        }

        val profile = ProxyProfile(
            id = UUID.randomUUID().toString(),
            name = name,
            protocol = ProtocolType.VLESS,
            server = host,
            port = port,
            authSecret = userInfo,
            encryption = "none",
            tls = TlsConfig(
                enabled = isTls,
                serverName = sni,
                utlsFingerprint = fp,
                realityPublicKey = pbk,
                realityShortId = sid,
                flow = flow
            ),
            transport = TransportConfig(
                type = transportType,
                path = path,
                serviceName = serviceName
            )
        )
        return Result.Success(profile)
    }

    private fun parseVmess(link: String): Result<ProxyProfile> {
        val b64 = link.substring(8).trim()
        val jsonStr = String(decodeBase64Safe(b64), Charsets.UTF_8)
        val jsonObj = json.parseToJsonElement(jsonStr).jsonObject

        val host = jsonObj["add"]?.jsonPrimitive?.content ?: ""
        val port = jsonObj["port"]?.jsonPrimitive?.content?.toIntOrNull() ?: 443
        val uuid = jsonObj["id"]?.jsonPrimitive?.content ?: ""
        val name = jsonObj["ps"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() } ?: "VMess-$host:$port"
        val net = jsonObj["net"]?.jsonPrimitive?.content ?: "tcp"
        val path = jsonObj["path"]?.jsonPrimitive?.content ?: ""
        val tlsStr = jsonObj["tls"]?.jsonPrimitive?.content ?: ""
        val sni = jsonObj["sni"]?.jsonPrimitive?.content ?: host

        val transportType = when (net.lowercase()) {
            "ws" -> TransportType.WS
            "grpc" -> TransportType.GRPC
            "httpupgrade" -> TransportType.HTTP_UPGRADE
            else -> TransportType.TCP
        }

        val profile = ProxyProfile(
            id = UUID.randomUUID().toString(),
            name = name,
            protocol = ProtocolType.VMESS,
            server = host,
            port = port,
            authSecret = uuid,
            encryption = jsonObj["scy"]?.jsonPrimitive?.content ?: "auto",
            tls = TlsConfig(
                enabled = tlsStr.equals("tls", true),
                serverName = sni
            ),
            transport = TransportConfig(
                type = transportType,
                path = path
            )
        )
        return Result.Success(profile)
    }

    private fun parseTrojan(link: String): Result<ProxyProfile> {
        val uri = SimpleUri.parse(link)
        val password = uri.userInfo
        val host = uri.host
        val port = if (uri.port > 0) uri.port else 443
        val name = decodeFragment(uri.fragment, "Trojan-$host:$port")
        val sni = uri.getQueryParameter("sni") ?: host

        val profile = ProxyProfile(
            id = UUID.randomUUID().toString(),
            name = name,
            protocol = ProtocolType.TROJAN,
            server = host,
            port = port,
            authSecret = password,
            tls = TlsConfig(
                enabled = true,
                serverName = sni
            )
        )
        return Result.Success(profile)
    }

    private fun parseShadowsocks(link: String): Result<ProxyProfile> {
        val withoutScheme = link.substring(5).trim()
        val fragment = withoutScheme.substringAfter('#', "")
        val mainPart = withoutScheme.substringBefore('#')

        val (auth, hostPort) = if (mainPart.contains("@")) {
            val atIndex = mainPart.lastIndexOf('@')
            val authPart = mainPart.substring(0, atIndex)
            val hpPart = mainPart.substring(atIndex + 1)
            val decodedAuth = if (authPart.contains(":")) authPart else String(decodeBase64Safe(authPart), Charsets.UTF_8)
            decodedAuth to hpPart
        } else {
            val decoded = String(decodeBase64Safe(mainPart), Charsets.UTF_8)
            val atIndex = decoded.lastIndexOf('@')
            decoded.substring(0, atIndex) to decoded.substring(atIndex + 1)
        }

        val (method, password) = if (auth.contains(":")) {
            auth.substringBefore(':') to auth.substringAfter(':')
        } else {
            "aes-128-gcm" to auth
        }

        val host = hostPort.substringBefore(':')
        val port = hostPort.substringAfter(':').toIntOrNull() ?: 8388
        val name = decodeFragment(fragment, "SS-$host:$port")

        val profile = ProxyProfile(
            id = UUID.randomUUID().toString(),
            name = name,
            protocol = ProtocolType.SHADOWSOCKS,
            server = host,
            port = port,
            authSecret = password,
            encryption = method
        )
        return Result.Success(profile)
    }

    private fun parseHysteria2(link: String): Result<ProxyProfile> {
        val uri = SimpleUri.parse(link)
        val password = uri.userInfo
        val host = uri.host
        val port = if (uri.port > 0) uri.port else 443
        val name = decodeFragment(uri.fragment, "Hy2-$host:$port")
        val sni = uri.getQueryParameter("sni") ?: host
        val insecure = uri.getQueryParameter("insecure")?.let { it == "1" || it.equals("true", true) } ?: false

        val profile = ProxyProfile(
            id = UUID.randomUUID().toString(),
            name = name,
            protocol = ProtocolType.HYSTERIA2,
            server = host,
            port = port,
            authSecret = password,
            tls = TlsConfig(
                enabled = true,
                serverName = sni,
                insecure = insecure
            )
        )
        return Result.Success(profile)
    }

    private fun decodeFragment(fragment: String?, default: String): String {
        if (fragment.isNullOrEmpty()) return default
        return try {
            URLDecoder.decode(fragment, "UTF-8")
        } catch (e: Exception) {
            fragment
        }
    }

    private fun decodeBase64Safe(str: String): ByteArray {
        val cleaned = str.trim().replace("\n", "").replace("\r", "").replace(" ", "")
            .replace('-', '+').replace('_', '/')
        val padded = when (cleaned.length % 4) {
            2 -> "$cleaned=="
            3 -> "$cleaned="
            else -> cleaned
        }
        return Base64.getDecoder().decode(padded)
    }
}
