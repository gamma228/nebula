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

    var warningLogger: ((String) -> Unit)? = null

    private fun logWarning(msg: String) {
        warningLogger?.invoke(msg) ?: System.err.println("[WARN LinkParser] $msg")
    }

    private data class SimpleUri(
        val userInfo: String,
        val host: String,
        val port: Int,
        val queryParams: Map<String, String>,
        val fragment: String?
    ) {
        fun getQueryParameter(vararg keys: String): String? {
            for (key in keys) {
                val value = queryParams[key]
                if (!value.isNullOrBlank()) return value
            }
            return null
        }

        fun hasQueryKey(vararg keys: String): Boolean {
            return keys.any { queryParams.containsKey(it) }
        }

        companion object {
            fun parse(raw: String): SimpleUri {
                val fragment = if (raw.contains("#")) raw.substringAfter("#") else null
                val withoutFrag = raw.substringBefore("#")
                val schemeIndex = withoutFrag.indexOf("://")
                val afterScheme = if (schemeIndex >= 0) withoutFrag.substring(schemeIndex + 3) else withoutFrag
                val queryStr = if (afterScheme.contains("?")) afterScheme.substringAfter("?") else ""
                var authority = afterScheme.substringBefore("?")
                while (authority.endsWith("/")) {
                    authority = authority.substring(0, authority.length - 1)
                }

                val rawUserInfo = if (authority.contains("@")) authority.substringBeforeLast("@") else ""
                val hostPort = if (authority.contains("@")) authority.substringAfterLast("@") else authority

                val userInfo = try {
                    URLDecoder.decode(rawUserInfo, "UTF-8")
                } catch (_: Exception) {
                    rawUserInfo
                }

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
            trimmed.startsWith("tuic://", ignoreCase = true) -> parseTuic(trimmed)
            else -> Result.Error(IllegalArgumentException("Unsupported link scheme in: $trimmed"))
        }
    } catch (e: Exception) {
        Result.Error(e, "Failed to parse link: ${e.message}")
    }

    private fun checkUnknownParams(uri: SimpleUri, knownKeys: Set<String>, protocol: String) {
        for (key in uri.queryParams.keys) {
            if (key !in knownKeys) {
                logWarning("Unknown parameter '$key=${uri.queryParams[key]}' in $protocol link")
            }
        }
    }

    private fun parseVless(link: String): Result<ProxyProfile> {
        val uri = SimpleUri.parse(link)
        val userInfo = uri.userInfo
        val host = uri.host
        val port = if (uri.port > 0) uri.port else 443
        if (host.isBlank() || port !in 1..65535) {
            return Result.Error(IllegalArgumentException("Invalid VLESS server endpoint: host='$host', port=$port"))
        }
        val name = decodeFragment(uri.fragment, "VLESS-$host:$port")

        val knownKeys = setOf(
            "security", "tls", "sni", "serverName", "peer", "host", "headerType", "headertype",
            "pbk", "publicKey", "public_key", "pk",
            "sid", "shortId", "short_id",
            "spx", "spiderX", "spider_x",
            "fp", "fingerprint", "client-fingerprint", "client_fingerprint",
            "alpn", "insecure", "allowInsecure", "allow_insecure", "skip-cert-verify",
            "pcs", "pinnedCertificateChainSha256", "peerCertificateSha256",
            "type", "net", "transport", "path", "serviceName", "service_name", "servicename", "service",
            "mode", "authority", "flow", "packetEncoding", "packet_encoding", "encryption"
        )
        checkUnknownParams(uri, knownKeys, "VLESS")

        val securityParam = uri.getQueryParameter("security", "tls") ?: "none"
        val pbk = uri.getQueryParameter("pbk", "publicKey", "public_key", "pk") ?: ""
        val sid = uri.getQueryParameter("sid", "shortId", "short_id") ?: ""
        val spx = uri.getQueryParameter("spx", "spiderX", "spider_x") ?: ""
        val isReality = securityParam.equals("reality", true) || pbk.isNotEmpty()
        val isTls = isReality || securityParam.equals("tls", true)

        val rawSni = uri.getQueryParameter("sni", "serverName", "peer")?.ifBlank { null }
            ?: uri.getQueryParameter("host")?.takeIf { !isIpAddress(it) }?.ifBlank { null }
            ?: host
        val sni = sanitizeSni(rawSni, host)

        val fp = uri.getQueryParameter("fp", "fingerprint", "client-fingerprint", "client_fingerprint") ?: "chrome"
        val alpnParam = uri.getQueryParameter("alpn")
        val insecure = isAllowInsecure(uri)

        val typeParam = uri.getQueryParameter("type", "net", "transport") ?: "tcp"
        val transportType = when (typeParam.lowercase()) {
            "ws" -> TransportType.WS
            "grpc" -> TransportType.GRPC
            "httpupgrade", "xhttp", "splithttp" -> TransportType.HTTP_UPGRADE
            else -> TransportType.TCP
        }

        val alpnList = when {
            !alpnParam.isNullOrEmpty() -> alpnParam.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            transportType == TransportType.GRPC -> listOf("h2")
            transportType == TransportType.WS -> listOf("http/1.1")
            transportType == TransportType.HTTP_UPGRADE -> listOf("http/1.1")
            else -> emptyList()
        }

        val path = uri.getQueryParameter("path") ?: ""
        val hostHeader = uri.getQueryParameter("host", "authority") ?: ""
        val headerType = uri.getQueryParameter("headerType", "headertype") ?: ""

        val rawServiceName = uri.getQueryParameter("serviceName", "service_name", "servicename", "service")
            ?: if (transportType == TransportType.GRPC) path else ""
        val serviceName = rawServiceName.removePrefix("/")

        val mode = uri.getQueryParameter("mode") ?: "gun"
        val authority = uri.getQueryParameter("authority") ?: hostHeader

        val rawFlow = uri.getQueryParameter("flow") ?: ""
        val flow = when {
            rawFlow.isNotEmpty() && transportType == TransportType.TCP -> rawFlow
            isReality && transportType == TransportType.TCP -> "xtls-rprx-vision"
            else -> ""
        }

        val packetEncoding = uri.getQueryParameter("packetEncoding", "packet_encoding") ?: "xudp"

        val profile = ProxyProfile(
            id = UUID.randomUUID().toString(),
            name = name,
            protocol = ProtocolType.VLESS,
            server = host,
            port = port,
            authSecret = userInfo,
            encryption = "none",
            packetEncoding = packetEncoding,
            headerType = headerType,
            tls = TlsConfig(
                enabled = isTls,
                serverName = sni,
                insecure = insecure,
                alpn = alpnList,
                utlsFingerprint = fp,
                realityPublicKey = pbk,
                realityShortId = sid,
                spiderX = spx,
                flow = flow
            ),
            transport = TransportConfig(
                type = transportType,
                path = path,
                host = hostHeader,
                serviceName = serviceName,
                mode = mode,
                authority = authority
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
        if (host.isBlank() || port !in 1..65535) {
            return Result.Error(IllegalArgumentException("Invalid VMess server endpoint: host='$host', port=$port"))
        }

        val uuid = jsonObj["id"]?.jsonPrimitive?.content ?: ""
        val name = jsonObj["ps"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: "VMess-$host:$port"
        val net = jsonObj["net"]?.jsonPrimitive?.content ?: "tcp"
        val path = jsonObj["path"]?.jsonPrimitive?.content ?: ""
        val hostHeader = jsonObj["host"]?.jsonPrimitive?.content ?: ""
        val tlsStr = jsonObj["tls"]?.jsonPrimitive?.content ?: ""
        val isTls = tlsStr.equals("tls", true) || tlsStr == "1" || tlsStr.equals("true", true)
        val rawSni = jsonObj["sni"]?.jsonPrimitive?.content?.ifBlank { null }
            ?: hostHeader.ifBlank { null }
            ?: host
        val sni = sanitizeSni(rawSni, host)
        val alpnStr = jsonObj["alpn"]?.jsonPrimitive?.content ?: ""

        val transportType = when (net.lowercase()) {
            "ws" -> TransportType.WS
            "grpc" -> TransportType.GRPC
            "httpupgrade", "xhttp", "splithttp" -> TransportType.HTTP_UPGRADE
            else -> TransportType.TCP
        }

        val alpnList = when {
            alpnStr.isNotEmpty() -> alpnStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            transportType == TransportType.GRPC -> listOf("h2")
            transportType == TransportType.WS -> listOf("http/1.1")
            else -> emptyList()
        }

        val serviceName = if (transportType == TransportType.GRPC) {
            path.removePrefix("/")
        } else {
            ""
        }

        val allowInsecure = jsonObj["allowInsecure"]?.jsonPrimitive?.content == "1" ||
                jsonObj["insecure"]?.jsonPrimitive?.content == "1" ||
                jsonObj["allowInsecure"]?.jsonPrimitive?.content.equals("true", true) ||
                jsonObj["skip-cert-verify"]?.jsonPrimitive?.content == "1" ||
                jsonObj["skip-cert-verify"]?.jsonPrimitive?.content.equals("true", true) ||
                jsonObj["verify_cert"]?.jsonPrimitive?.content.equals("false", true)

        val encryption = jsonObj["scy"]?.jsonPrimitive?.content?.ifBlank { null }
            ?: jsonObj["security"]?.jsonPrimitive?.content?.ifBlank { null }
            ?: "auto"

        val headerType = jsonObj["type"]?.jsonPrimitive?.content ?: ""

        val profile = ProxyProfile(
            id = UUID.randomUUID().toString(),
            name = name,
            protocol = ProtocolType.VMESS,
            server = host,
            port = port,
            authSecret = uuid,
            encryption = encryption,
            headerType = headerType,
            tls = TlsConfig(
                enabled = isTls,
                serverName = sni,
                insecure = allowInsecure,
                alpn = alpnList
            ),
            transport = TransportConfig(
                type = transportType,
                path = path,
                host = hostHeader,
                serviceName = serviceName
            )
        )
        return Result.Success(profile)
    }

    private fun parseTrojan(link: String): Result<ProxyProfile> {
        val uri = SimpleUri.parse(link)
        val password = uri.userInfo
        val host = uri.host
        val port = if (uri.port > 0) uri.port else 443
        if (host.isBlank() || port !in 1..65535) {
            return Result.Error(IllegalArgumentException("Invalid Trojan server endpoint: host='$host', port=$port"))
        }

        val name = decodeFragment(uri.fragment, "Trojan-$host:$port")

        val knownKeys = setOf(
            "security", "tls", "sni", "serverName", "peer", "host", "fp", "fingerprint", "alpn",
            "insecure", "allowInsecure", "allow_insecure", "skip-cert-verify",
            "type", "net", "transport", "path", "serviceName", "service_name", "servicename", "service",
            "mode", "authority", "headerType"
        )
        checkUnknownParams(uri, knownKeys, "Trojan")

        val rawSni = uri.getQueryParameter("sni", "serverName", "peer")?.ifBlank { null }
            ?: uri.getQueryParameter("host")?.takeIf { !isIpAddress(it) }?.ifBlank { null }
            ?: host
        val sni = sanitizeSni(rawSni, host)
        val fp = uri.getQueryParameter("fp", "fingerprint") ?: "chrome"
        val insecure = isAllowInsecure(uri)

        val typeParam = uri.getQueryParameter("type", "net", "transport") ?: "tcp"
        val transportType = when (typeParam.lowercase()) {
            "ws" -> TransportType.WS
            "grpc" -> TransportType.GRPC
            "httpupgrade", "xhttp", "splithttp" -> TransportType.HTTP_UPGRADE
            else -> TransportType.TCP
        }

        val alpnParam = uri.getQueryParameter("alpn")
        val alpnList = when {
            !alpnParam.isNullOrEmpty() -> alpnParam.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            transportType == TransportType.GRPC -> listOf("h2")
            transportType == TransportType.WS -> listOf("http/1.1")
            else -> emptyList()
        }

        val path = uri.getQueryParameter("path") ?: ""
        val hostHeader = uri.getQueryParameter("host") ?: sni
        val rawServiceName = uri.getQueryParameter("serviceName", "service_name", "servicename", "service")
            ?: if (transportType == TransportType.GRPC) path else ""
        val serviceName = rawServiceName.removePrefix("/")
        val mode = uri.getQueryParameter("mode") ?: "gun"
        val authority = uri.getQueryParameter("authority") ?: hostHeader

        val profile = ProxyProfile(
            id = UUID.randomUUID().toString(),
            name = name,
            protocol = ProtocolType.TROJAN,
            server = host,
            port = port,
            authSecret = password,
            tls = TlsConfig(
                enabled = true,
                serverName = sni,
                insecure = insecure,
                alpn = alpnList,
                utlsFingerprint = fp
            ),
            transport = TransportConfig(
                type = transportType,
                path = path,
                host = hostHeader,
                serviceName = serviceName,
                mode = mode,
                authority = authority
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

        val host = if (hostPort.startsWith("[")) {
            hostPort.substringAfter("[").substringBefore("]")
        } else if (hostPort.contains(":")) {
            hostPort.substringBefore(':')
        } else {
            hostPort
        }
        val port = if (hostPort.startsWith("[")) {
            val after = hostPort.substringAfter("]")
            if (after.startsWith(":")) after.substring(1).toIntOrNull() ?: 8388 else 8388
        } else if (hostPort.contains(":")) {
            hostPort.substringAfter(':').toIntOrNull() ?: 8388
        } else {
            8388
        }

        if (host.isBlank() || port !in 1..65535) {
            return Result.Error(IllegalArgumentException("Invalid Shadowsocks server endpoint: host='$host', port=$port"))
        }
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
        val rawSni = uri.getQueryParameter("sni") ?: host
        val sni = sanitizeSni(rawSni, host)
        val fp = uri.getQueryParameter("fp", "fingerprint") ?: "chrome"
        val insecure = isAllowInsecure(uri)
        val alpnParam = uri.getQueryParameter("alpn")
        val alpnList = if (!alpnParam.isNullOrEmpty()) {
            alpnParam.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            listOf("h3")
        }

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
                insecure = insecure,
                alpn = alpnList,
                utlsFingerprint = fp
            )
        )
        return Result.Success(profile)
    }

    private fun parseTuic(link: String): Result<ProxyProfile> {
        val uri = SimpleUri.parse(link)
        val host = uri.host
        val port = if (uri.port > 0) uri.port else 443
        if (host.isBlank() || port !in 1..65535) {
            return Result.Error(IllegalArgumentException("Invalid TUIC server endpoint: host='$host', port=$port"))
        }
        val name = decodeFragment(uri.fragment, "TUIC-$host:$port")
        val rawSni = uri.getQueryParameter("sni") ?: host
        val sni = sanitizeSni(rawSni, host)
        val alpnParam = uri.getQueryParameter("alpn")
        val alpnList = if (!alpnParam.isNullOrEmpty()) {
            alpnParam.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            listOf("h3")
        }
        val insecure = isAllowInsecure(uri)

        val profile = ProxyProfile(
            id = UUID.randomUUID().toString(),
            name = name,
            protocol = ProtocolType.TUIC,
            server = host,
            port = port,
            authSecret = uri.userInfo, // format: "uuid:password" or "uuid"
            tls = TlsConfig(
                enabled = true,
                serverName = sni,
                insecure = insecure,
                alpn = alpnList
            )
        )
        return Result.Success(profile)
    }

    private fun sanitizeSni(rawSni: String?, fallbackHost: String): String {
        if (rawSni.isNullOrBlank()) return fallbackHost
        val trimmed = rawSni.trim()
        if (trimmed.contains("://") || trimmed.contains("/") || trimmed.contains(" ")) {
            val stripped = trimmed.substringAfter("://").substringBefore("/").substringBefore(" ").substringBefore(":")
            return if (stripped.isNotBlank()) stripped else fallbackHost
        }
        return trimmed
    }

    private fun isIpAddress(host: String): Boolean {
        if (host.isEmpty()) return false
        val parts = host.split('.')
        if (parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 }) return true
        if (host.contains(':')) return true
        return false
    }

    private fun isAllowInsecure(uri: SimpleUri): Boolean {
        val keys = listOf(
            "insecure", "allowInsecure", "allow_insecure", "skip-cert-verify",
            "pcs", "pinnedCertificateChainSha256", "peerCertificateSha256"
        )
        return keys.any { key ->
            val v = uri.getQueryParameter(key)
            !v.isNullOrEmpty() && (v == "1" || v.equals("true", true) || key in listOf("pcs", "pinnedCertificateChainSha256", "peerCertificateSha256"))
        }
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
