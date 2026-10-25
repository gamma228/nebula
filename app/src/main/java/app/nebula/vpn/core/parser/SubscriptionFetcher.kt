package app.nebula.vpn.core.parser

import app.nebula.vpn.core.model.ProxyProfile
import app.nebula.vpn.core.model.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.zip.GZIPInputStream

object SubscriptionFetcher {

    const val DEFAULT_USER_AGENT = "v2rayN/6.23 sing-box/1.14.4 ClashMeta/1.18.0"
    private const val CONNECT_TIMEOUT_MS = 15000
    private const val READ_TIMEOUT_MS = 20000
    private const val MAX_REDIRECTS = 10

    data class SubscriptionFetchResult(
        val profiles: List<ProxyProfile>,
        val title: String,
        val uploadBytes: Long? = null,
        val downloadBytes: Long? = null,
        val totalBytes: Long? = null,
        val expireEpochSeconds: Long? = null,
        val updateIntervalHours: Int = 24,
        val supportUrl: String? = null,
        val webPageUrl: String? = null,
        val rawContent: String = ""
    )

    suspend fun fetch(
        urlStr: String,
        customUserAgent: String? = null
    ): Result<SubscriptionFetchResult> = withContext(Dispatchers.IO) {
        try {
            var currentUrl = urlStr.trim()
            if (!currentUrl.startsWith("http://", ignoreCase = true) && !currentUrl.startsWith("https://", ignoreCase = true)) {
                currentUrl = "https://$currentUrl"
            }

            var redirects = 0
            var connection: HttpURLConnection? = null
            var responseCode: Int
            val userAgent = customUserAgent?.ifBlank { null } ?: DEFAULT_USER_AGENT

            while (redirects < MAX_REDIRECTS) {
                val targetUrl = URI(currentUrl).toURL()
                val conn = targetUrl.openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.connectTimeout = CONNECT_TIMEOUT_MS
                conn.readTimeout = READ_TIMEOUT_MS
                conn.setRequestProperty("User-Agent", userAgent)
                conn.setRequestProperty("Accept", "*/*")
                conn.setRequestProperty("Accept-Encoding", "gzip, deflate")
                conn.connect()

                responseCode = conn.responseCode
                if (responseCode in 300..399) {
                    val location = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (location.isNullOrBlank()) {
                        return@withContext Result.Error(Exception("HTTP redirect $responseCode with empty Location header"))
                    }
                    currentUrl = if (location.startsWith("http://", ignoreCase = true) || location.startsWith("https://", ignoreCase = true)) {
                        location
                    } else {
                        targetUrl.toURI().resolve(location).toString()
                    }
                    redirects++
                } else if (responseCode in 200..299) {
                    connection = conn
                    break
                } else {
                    conn.disconnect()
                    return@withContext Result.Error(Exception("HTTP error $responseCode: ${conn.responseMessage}"))
                }
            }

            if (connection == null) {
                return@withContext Result.Error(Exception("Too many HTTP redirects (exceeded $MAX_REDIRECTS)"))
            }

            val headers = mutableMapOf<String, String>()
            for (i in 0 until connection.headerFields.size) {
                val key = connection.getHeaderFieldKey(i)
                val value = connection.getHeaderField(i)
                if (key != null && value != null) {
                    headers[key.lowercase()] = value
                }
            }

            val contentEncoding = headers["content-encoding"]?.lowercase() ?: ""
            val rawInputStream: InputStream = connection.inputStream
            val stream: InputStream = if (contentEncoding.contains("gzip")) {
                GZIPInputStream(rawInputStream)
            } else {
                rawInputStream
            }

            val body = stream.bufferedReader(StandardCharsets.UTF_8).use(BufferedReader::readText)
            connection.disconnect()

            if (body.isBlank()) {
                return@withContext Result.Error(Exception("Subscription returned empty body"))
            }

            // Extract metadata from headers
            var title: String? = null
            var uploadBytes: Long? = null
            var downloadBytes: Long? = null
            var totalBytes: Long? = null
            var expireEpochSeconds: Long? = null
            var updateIntervalHours = 24
            var supportUrl: String? = null
            var webPageUrl: String? = null

            headers["profile-title"]?.let {
                title = decodeProfileTitle(it)
            }
            if (title == null) {
                headers["content-disposition"]?.let { disp ->
                    val match = Regex("""filename[*]?=(?:UTF-8'')?["']?([^"';]+)["']?""").find(disp)
                    match?.groupValues?.get(1)?.let { filename ->
                        title = try {
                            URLDecoder.decode(filename, "UTF-8")
                        } catch (e: Exception) {
                            filename
                        }
                    }
                }
            }

            headers["subscription-userinfo"]?.let { info ->
                for (part in info.split(";")) {
                    val kv = part.trim().split("=")
                    if (kv.size == 2) {
                        val k = kv[0].trim().lowercase()
                        val v = kv[1].trim().toLongOrNull() ?: continue
                        when (k) {
                            "upload" -> uploadBytes = v
                            "download" -> downloadBytes = v
                            "total" -> totalBytes = v
                            "expire" -> expireEpochSeconds = v
                        }
                    }
                }
            }

            headers["profile-update-interval"]?.let { interval ->
                val hours = interval.trim().toIntOrNull()
                if (hours != null && hours > 0) {
                    updateIntervalHours = hours
                }
            }

            supportUrl = headers["support-url"]
            webPageUrl = headers["web-page-url"]

            // Decode profiles from body
            when (val decoded = SubscriptionDecoder.decode(body, headers)) {
                is Result.Success -> {
                    val finalTitle = title
                        ?: decoded.data.title
                        ?: extractHostAsTitle(urlStr)

                    val result = SubscriptionFetchResult(
                        profiles = decoded.data.profiles,
                        title = finalTitle,
                        uploadBytes = uploadBytes ?: decoded.data.uploadBytes,
                        downloadBytes = downloadBytes ?: decoded.data.downloadBytes,
                        totalBytes = totalBytes ?: decoded.data.totalBytes,
                        expireEpochSeconds = expireEpochSeconds ?: decoded.data.expireEpochSeconds,
                        updateIntervalHours = updateIntervalHours,
                        supportUrl = supportUrl,
                        webPageUrl = webPageUrl,
                        rawContent = body
                    )
                    Result.Success(result)
                }
                is Result.Error -> decoded
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    private fun decodeProfileTitle(headerVal: String): String {
        val trimmed = headerVal.trim()
        if (trimmed.startsWith("base64:", ignoreCase = true)) {
            val b64 = trimmed.substring(7).trim()
            try {
                return String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8)
            } catch (ignored: Exception) {}
        }
        return try {
            URLDecoder.decode(trimmed, "UTF-8")
        } catch (e: Exception) {
            trimmed
        }
    }

    private fun extractHostAsTitle(urlStr: String): String {
        return try {
            val uri = URI(urlStr)
            val host = uri.host ?: urlStr
            host.removePrefix("www.")
        } catch (e: Exception) {
            "Subscription"
        }
    }
}
