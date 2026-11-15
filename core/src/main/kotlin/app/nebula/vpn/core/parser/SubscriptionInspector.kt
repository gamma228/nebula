package app.nebula.vpn.core.parser

import app.nebula.vpn.core.model.Result
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream

object SubscriptionInspector {

    data class InspectionReport(
        val httpStatus: Int,
        val httpMessage: String,
        val redirectsFollowed: Int,
        val selectedHeaders: Map<String, String>,
        val bodySizeBytes: Int,
        val detectedFormat: String,
        val parsedProxiesByProtocol: Map<String, Int>,
        val totalParsedCount: Int,
        val totalSkippedCount: Int,
        val maskedBodySnippet: String,
        val rawBody: String = ""
    ) {
        fun formatReport(): String {
            val sb = StringBuilder()
            sb.appendLine("=== Subscription Inspection Report ===")
            sb.appendLine("HTTP Status: $httpStatus $httpMessage")
            sb.appendLine("Redirects Followed: $redirectsFollowed")
            sb.appendLine("Selected Response Headers:")
            if (selectedHeaders.isEmpty()) {
                sb.appendLine("  (None found)")
            } else {
                selectedHeaders.forEach { (k, v) ->
                    sb.appendLine("  $k: $v")
                }
            }
            sb.appendLine("Body Size: $bodySizeBytes bytes")
            sb.appendLine("Detected Format: $detectedFormat")
            sb.appendLine("Parsed Proxies per Protocol:")
            if (parsedProxiesByProtocol.isEmpty()) {
                sb.appendLine("  (None)")
            } else {
                parsedProxiesByProtocol.forEach { (proto, count) ->
                    sb.appendLine("  $proto: $count")
                }
            }
            sb.appendLine("Total Parsed: $totalParsedCount, Skipped: $totalSkippedCount")
            sb.appendLine("First 200 Characters (Sanitized):")
            sb.appendLine("--- BEGIN SNIPPET ---")
            sb.appendLine(maskedBodySnippet)
            sb.appendLine("--- END SNIPPET ---")
            return sb.toString()
        }
    }

    fun inspect(
        urlStr: String,
        userAgent: String,
        extraHeaders: Map<String, String> = emptyMap()
    ): Result<InspectionReport> {
        return try {
            var currentUrl = urlStr.trim()
            if (!currentUrl.startsWith("http://", ignoreCase = true) && !currentUrl.startsWith("https://", ignoreCase = true)) {
                currentUrl = "https://$currentUrl"
            }

            var redirects = 0
            val maxRedirects = 10
            var connection: HttpURLConnection? = null
            var responseCode = -1
            var responseMessage = ""

            while (redirects < maxRedirects) {
                val targetUrl = URI(currentUrl).toURL()
                val port = if (targetUrl.port > 0) targetUrl.port else targetUrl.defaultPort
                app.nebula.vpn.core.tor.NetworkPolicy.checkAllowed(targetUrl.host, port)

                val conn = targetUrl.openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 15000
                conn.readTimeout = 20000
                conn.setRequestProperty("User-Agent", userAgent)
                conn.setRequestProperty("Accept", "*/*")
                conn.setRequestProperty("Accept-Encoding", "gzip, deflate")
                extraHeaders.forEach { (k, v) ->
                    conn.setRequestProperty(k, v)
                }

                conn.connect()
                responseCode = conn.responseCode
                responseMessage = conn.responseMessage ?: ""

                if (responseCode in 300..399) {
                    val location = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (location.isNullOrBlank()) {
                        return Result.Error(Exception("HTTP redirect $responseCode with empty Location header"))
                    }
                    currentUrl = if (location.startsWith("http://", ignoreCase = true) || location.startsWith("https://", ignoreCase = true)) {
                        location
                    } else {
                        targetUrl.toURI().resolve(location).toString()
                    }
                    redirects++
                } else {
                    connection = conn
                    break
                }
            }

            if (connection == null) {
                return Result.Error(Exception("Too many HTTP redirects (exceeded $maxRedirects)"))
            }

            // Collect only allowed headers
            val selectedHeaders = mutableMapOf<String, String>()
            val headerCount = connection.headerFields.size
            for (i in 0 until headerCount) {
                val key = connection.getHeaderFieldKey(i) ?: continue
                val lowerKey = key.lowercase()
                val value = connection.getHeaderField(i) ?: continue

                val isAllowed = lowerKey in listOf(
                    "content-type",
                    "content-encoding",
                    "content-length",
                    "subscription-userinfo",
                    "profile-title",
                    "profile-update-interval"
                ) || lowerKey == "support-url" || lowerKey.contains("hwid") || lowerKey.contains("device")

                if (isAllowed) {
                    selectedHeaders[lowerKey] = if (lowerKey == "support-url") "present" else value
                }
            }

            val contentEncoding = connection.getHeaderField("Content-Encoding")?.lowercase() ?: ""
            val stream: InputStream = if (contentEncoding.contains("gzip")) {
                GZIPInputStream(connection.inputStream)
            } else if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }

            val rawBody = stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            connection.disconnect()

            val bodyBytes = rawBody.toByteArray(StandardCharsets.UTF_8).size
            val detectedFormat = detectFormat(rawBody)

            val parsedCounts = mutableMapOf<String, Int>()
            var totalParsed = 0
            var totalSkipped = 0

            // Try decoding to count parsed vs skipped
            when (val decodeResult = SubscriptionDecoder.decode(rawBody, selectedHeaders)) {
                is Result.Success -> {
                    totalParsed = decodeResult.data.profiles.size
                    decodeResult.data.profiles.forEach { p ->
                        val proto = p.protocol.name
                        parsedCounts[proto] = (parsedCounts[proto] ?: 0) + 1
                    }
                }
                is Result.Error -> {
                    totalParsed = 0
                }
            }

            val snippet = maskSensitiveData(rawBody).take(200)

            Result.Success(
                InspectionReport(
                    httpStatus = responseCode,
                    httpMessage = responseMessage,
                    redirectsFollowed = redirects,
                    selectedHeaders = selectedHeaders,
                    bodySizeBytes = bodyBytes,
                    detectedFormat = detectedFormat,
                    parsedProxiesByProtocol = parsedCounts,
                    totalParsedCount = totalParsed,
                    totalSkippedCount = totalSkipped,
                    maskedBodySnippet = snippet,
                    rawBody = rawBody
                )
            )
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    fun detectFormat(body: String): String {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return "EMPTY"
        if (trimmed.startsWith("<!DOCTYPE html", ignoreCase = true) ||
            trimmed.startsWith("<html", ignoreCase = true) ||
            trimmed.contains("<head>", ignoreCase = true)
        ) {
            return "HTML_PAGE"
        }
        if (trimmed.startsWith("proxies:") || trimmed.contains("\nproxies:") || trimmed.contains("proxy-groups:")) {
            return "CLASH_YAML"
        }
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            if (trimmed.contains("\"outbounds\"")) {
                return if (trimmed.contains("\"inbounds\"") || trimmed.contains("\"route\"")) "SING_BOX_JSON" else "XRAY_JSON"
            }
            if (trimmed.contains("\"servers\"")) {
                return "SIP008_SHADOWSOCKS_JSON"
            }
            return "GENERIC_JSON"
        }
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            return "JSON_ARRAY"
        }
        val isBase64 = isBase64String(trimmed)
        if (isBase64) {
            return "BASE64_SHARE_LINKS"
        }
        if (trimmed.contains("vless://") || trimmed.contains("vmess://") || trimmed.contains("trojan://") ||
            trimmed.contains("ss://") || trimmed.contains("hy2://") || trimmed.contains("hysteria2://")
        ) {
            return "PLAIN_SHARE_LINKS"
        }
        return "UNKNOWN_TEXT"
    }

    private fun isBase64String(input: String): Boolean {
        val cleaned = input.trim().replace("\n", "").replace("\r", "").replace(" ", "")
        if (cleaned.length < 16) return false
        val base64Pattern = Regex("^[A-Za-z0-9+/=_-]+$")
        return base64Pattern.matches(cleaned)
    }

    fun maskSensitiveData(input: String): String {
        var s = input
        // Mask full HTTP/HTTPS URLs to guarantee tokens, paths, and domains never leak
        s = s.replace(Regex("https?://[^\\s\"']+"), "[URL_REDACTED]")
        // Mask UUIDs
        s = s.replace(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"), "[UUID]")
        // Mask IPv4
        s = s.replace(Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b"), "[IPV4]")
        // Mask IPv6
        s = s.replace(Regex("\\b(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}\\b"), "[IPV6]")
        // Mask passwords in URLs (e.g. trojan://pass@host:port or vless://uuid@host:port)
        s = s.replace(Regex("://([^@:]+)(:[^@]+)?@([^:/?#]+)"), "://[AUTH]@\$3")
        // Mask secret parameters in query strings
        s = s.replace(Regex("(pbk|sid|password|secret|key|uuid|token)=([^&\\s#]+)"), "\$1=****")
        // Mask potential domain names (2+ labels with common TLDs)
        s = s.replace(Regex("([a-zA-Z0-9-]+\\.)+(live|com|net|org|xyz|online|site|ru|io|me|app)"), "[DOMAIN]")
        return s
    }
}
