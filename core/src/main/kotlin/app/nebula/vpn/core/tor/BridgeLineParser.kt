package app.nebula.vpn.core.tor

import java.util.Locale

enum class BridgeTransport(val id: String, val displayName: String, val isSupported: Boolean = false) {
    DIRECT("direct", "Direct Tor (No Bridge)", true),
    OBFS4("obfs4", "obfs4", false),
    WEBTUNNEL("webtunnel", "WebTunnel", false),
    SNOWFLAKE("snowflake", "Snowflake", false),
    VANILLA("vanilla", "Vanilla Bridge", true);

    companion object {
        fun fromString(name: String): BridgeTransport {
            return when (name.lowercase(Locale.ROOT).trim()) {
                "obfs4" -> OBFS4
                "webtunnel" -> WEBTUNNEL
                "snowflake" -> SNOWFLAKE
                "direct", "none", "" -> DIRECT
                else -> VANILLA
            }
        }
    }
}

data class ParsedBridge(
    val raw: String,
    val transport: BridgeTransport,
    val host: String,
    val port: Int,
    val fingerprint: String?,
    val params: Map<String, String>,
    val isValid: Boolean,
    val errorReason: String? = null,
    val isSupported: Boolean = transport.isSupported
) {
    /**
     * Returns an anonymized / masked version of this bridge suitable for logs and UI status.
     * Hard Rule 5: Secrets (IPs, certificates, fingerprints) never appear in logs.
     */
    fun masked(): String {
        if (transport == BridgeTransport.DIRECT) return "Direct Tor"
        val maskedHost = if (host.contains(":")) "[IPv6]:$port" else "${host.take(3)}***:$port"
        val maskedFp = fingerprint?.let { "${it.take(4)}...${it.takeLast(4)}" } ?: "no-fp"
        return "${transport.id} $maskedHost $maskedFp"
    }
}

object BridgeLineParser {

    private val HEX_40_REGEX = Regex("^[0-9a-fA-F]{40}$")
    private val IPV4_REGEX = Regex("^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}$")

    /**
     * Parses a single Tor bridge line.
     * Formats supported:
     * - "Bridge obfs4 192.0.2.1:443 43A2... cert=... iat-mode=0"
     * - "obfs4 192.0.2.1:443 43A2... cert=... iat-mode=0"
     * - "webtunnel 192.0.2.1:443 43A2... url=https://example.com/..."
     * - "snowflake 192.0.2.1:1 2B28... url=... front=... ice=..."
     * - "192.0.2.1:443 43A2..."
     */
    fun parseLine(rawLine: String): ParsedBridge {
        val trimmed = rawLine.trim()
        if (trimmed.isEmpty()) {
            return ParsedBridge(
                raw = rawLine,
                transport = BridgeTransport.DIRECT,
                host = "",
                port = 0,
                fingerprint = null,
                params = emptyMap(),
                isValid = false,
                errorReason = "Empty line"
            )
        }

        // Strip optional leading "Bridge " keyword if copied from torrc
        val lineWithoutPrefix = if (trimmed.startsWith("Bridge ", ignoreCase = true)) {
            trimmed.substring(7).trim()
        } else {
            trimmed
        }

        val tokens = lineWithoutPrefix.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) {
            return ParsedBridge(rawLine, BridgeTransport.DIRECT, "", 0, null, emptyMap(), false, "Empty tokens")
        }

        var tokenIndex = 0
        val firstToken = tokens[0].lowercase(Locale.ROOT)

        val transport: BridgeTransport
        if (firstToken in listOf("obfs4", "webtunnel", "snowflake")) {
            transport = BridgeTransport.fromString(firstToken)
            tokenIndex++
        } else if (firstToken.contains(":") || tokens.size > 1 && tokens[1].contains("cert=")) {
            transport = BridgeTransport.VANILLA
        } else {
            transport = BridgeTransport.fromString(firstToken)
            tokenIndex++
        }

        if (tokenIndex >= tokens.size) {
            return ParsedBridge(rawLine, transport, "", 0, null, emptyMap(), false, "Missing address and port")
        }

        // Parse host:port
        val addrToken = tokens[tokenIndex++]
        val host: String
        val port: Int

        try {
            if (addrToken.startsWith("[")) {
                // IPv6 format: [2001:db8::1]:443
                val closingBracket = addrToken.indexOf(']')
                if (closingBracket == -1 || closingBracket >= addrToken.length - 2 || addrToken[closingBracket + 1] != ':') {
                    return ParsedBridge(rawLine, transport, "", 0, null, emptyMap(), false, "Malformed IPv6 address: $addrToken")
                }
                host = addrToken.substring(1, closingBracket)
                port = addrToken.substring(closingBracket + 2).toInt()
            } else {
                val colon = addrToken.lastIndexOf(':')
                if (colon == -1) {
                    return ParsedBridge(rawLine, transport, addrToken, 0, null, emptyMap(), false, "Missing port in address: $addrToken")
                }
                host = addrToken.substring(0, colon)
                port = addrToken.substring(colon + 1).toInt()
            }

            if (port !in 1..65535) {
                return ParsedBridge(rawLine, transport, host, port, null, emptyMap(), false, "Port out of range 1..65535: $port")
            }
        } catch (_: Exception) {
            return ParsedBridge(rawLine, transport, "", 0, null, emptyMap(), false, "Invalid host:port specification: $addrToken")
        }

        // Remaining tokens: could be fingerprint or key=value params
        var fingerprint: String? = null
        val params = mutableMapOf<String, String>()

        while (tokenIndex < tokens.size) {
            val token = tokens[tokenIndex++]
            val eqIndex = token.indexOf('=')
            if (eqIndex != -1) {
                val k = token.substring(0, eqIndex).lowercase(Locale.ROOT)
                val v = token.substring(eqIndex + 1)
                params[k] = v
            } else if (fingerprint == null && HEX_40_REGEX.matches(token)) {
                fingerprint = token.uppercase(Locale.ROOT)
            } else if (fingerprint == null && token.length == 40) {
                // Fingerprint candidate with non-hex?
                return ParsedBridge(rawLine, transport, host, port, token, params, false, "Fingerprint is not a valid 40-char hexadecimal value")
            }
        }

        // Validate transport-specific required parameters
        when (transport) {
            BridgeTransport.OBFS4 -> {
                if (!params.containsKey("cert")) {
                    return ParsedBridge(rawLine, transport, host, port, fingerprint, params, false, "obfs4 bridge requires 'cert=' parameter")
                }
                if (fingerprint == null) {
                    return ParsedBridge(rawLine, transport, host, port, fingerprint, params, false, "obfs4 bridge requires a 40-character fingerprint")
                }
            }
            BridgeTransport.WEBTUNNEL -> {
                if (!params.containsKey("url")) {
                    return ParsedBridge(rawLine, transport, host, port, fingerprint, params, false, "WebTunnel bridge requires 'url=' parameter")
                }
                val url = params["url"] ?: ""
                if (!url.startsWith("https://", ignoreCase = true)) {
                    return ParsedBridge(rawLine, transport, host, port, fingerprint, params, false, "WebTunnel url must start with https://")
                }
                if (fingerprint == null) {
                    return ParsedBridge(rawLine, transport, host, port, fingerprint, params, false, "WebTunnel bridge requires a 40-character fingerprint")
                }
            }
            BridgeTransport.SNOWFLAKE -> {
                // Snowflake typically has url, front, ice params or runs via broker
            }
            BridgeTransport.VANILLA -> {
                // Vanilla bridges require a valid host:port and optional fingerprint
            }
            BridgeTransport.DIRECT -> {
                // Direct Tor
            }
        }

        // Reject unsupported pluggable transports with clear honest message
        if (!transport.isSupported) {
            return ParsedBridge(
                raw = rawLine,
                transport = transport,
                host = host,
                port = port,
                fingerprint = fingerprint,
                params = params,
                isValid = false,
                errorReason = "Transport '${transport.id}' is not supported yet (in-process Go runtime limitation). Please use Vanilla Bridges or Carrier Mode.",
                isSupported = false
            )
        }

        return ParsedBridge(
            raw = rawLine,
            transport = transport,
            host = host,
            port = port,
            fingerprint = fingerprint,
            params = params,
            isValid = true,
            errorReason = null
        )
    }

    /**
     * Parses multiple bridge lines (e.g. from pasted text or clipboard), ignoring blank lines and comments.
     */
    fun parseLines(multiLineText: String): List<ParsedBridge> {
        return multiLineText.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { parseLine(it) }
            .toList()
    }
}
