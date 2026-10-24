package app.nebula.vpn.core.parser

import app.nebula.vpn.core.model.ProxyGroup
import app.nebula.vpn.core.model.ProxyProfile
import app.nebula.vpn.core.model.Result
import app.nebula.vpn.core.model.RoutingRule
import java.util.Base64

object SubscriptionDecoder {

    data class SubscriptionResult(
        val profiles: List<ProxyProfile>,
        val groups: List<ProxyGroup> = emptyList(),
        val rules: List<RoutingRule> = emptyList(),
        val title: String? = null,
        val uploadBytes: Long? = null,
        val downloadBytes: Long? = null,
        val totalBytes: Long? = null,
        val expireEpochSeconds: Long? = null
    )

    fun decode(
        rawBody: String,
        headers: Map<String, String> = emptyMap()
    ): Result<SubscriptionResult> {
        val trimmed = rawBody.trim()

        // 1. Check if it's Clash YAML
        if (trimmed.startsWith("proxies:") || trimmed.contains("\nproxies:") || trimmed.contains("proxy-groups:")) {
            return when (val clashResult = ClashYamlParser.parse(trimmed)) {
                is Result.Success -> {
                    val metadata = parseHeaders(headers)
                    Result.Success(
                        SubscriptionResult(
                            profiles = clashResult.data.profiles,
                            groups = clashResult.data.groups,
                            rules = clashResult.data.rules,
                            title = metadata.title,
                            uploadBytes = metadata.uploadBytes,
                            downloadBytes = metadata.downloadBytes,
                            totalBytes = metadata.totalBytes,
                            expireEpochSeconds = metadata.expireEpochSeconds
                        )
                    )
                }
                is Result.Error -> clashResult
            }
        }

        // 2. Try decoding as base64 list of proxy links
        val decodedText = tryDecodeBase64(trimmed) ?: trimmed

        // Parse line-by-line
        val profiles = mutableListOf<ProxyProfile>()
        for (line in decodedText.lines()) {
            val candidate = line.trim()
            if (candidate.isNotEmpty()) {
                val parsed = LinkParser.parse(candidate)
                if (parsed is Result.Success) {
                    profiles.add(parsed.data)
                }
            }
        }

        if (profiles.isEmpty()) {
            return Result.Error(IllegalArgumentException("No valid proxy profiles found in subscription"))
        }

        val metadata = parseHeaders(headers)
        return Result.Success(
            SubscriptionResult(
                profiles = profiles,
                title = metadata.title,
                uploadBytes = metadata.uploadBytes,
                downloadBytes = metadata.downloadBytes,
                totalBytes = metadata.totalBytes,
                expireEpochSeconds = metadata.expireEpochSeconds
            )
        )
    }

    private data class ParsedMetadata(
        val title: String?,
        val uploadBytes: Long?,
        val downloadBytes: Long?,
        val totalBytes: Long?,
        val expireEpochSeconds: Long?
    )

    private fun parseHeaders(headers: Map<String, String>): ParsedMetadata {
        var title: String? = null
        var upload: Long? = null
        var download: Long? = null
        var total: Long? = null
        var expire: Long? = null

        for ((key, value) in headers) {
            when (key.lowercase()) {
                "profile-title" -> title = value
                "subscription-userinfo" -> {
                    // format: upload=123; download=456; total=789; expire=123456789
                    val pairs = value.split(";")
                    for (pair in pairs) {
                        val kv = pair.split("=")
                        if (kv.size == 2) {
                            val k = kv[0].trim().lowercase()
                            val v = kv[1].trim().toLongOrNull()
                            when (k) {
                                "upload" -> upload = v
                                "download" -> download = v
                                "total" -> total = v
                                "expire" -> expire = v
                            }
                        }
                    }
                }
            }
        }

        return ParsedMetadata(title, upload, download, total, expire)
    }

    private fun tryDecodeBase64(input: String): String? {
        return try {
            val cleaned = input.trim().replace("\n", "").replace("\r", "").replace(" ", "")
                .replace('-', '+').replace('_', '/')
            val padded = when (cleaned.length % 4) {
                2 -> "$cleaned=="
                3 -> "$cleaned="
                else -> cleaned
            }
            val decodedBytes = Base64.getDecoder().decode(padded)
            String(decodedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }
}
