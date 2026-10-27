package app.nebula.vpn.core.builder

import app.nebula.vpn.core.model.AppRoutingMode
import app.nebula.vpn.core.model.GroupType
import app.nebula.vpn.core.model.ProtocolType
import app.nebula.vpn.core.model.ProxyGroup
import app.nebula.vpn.core.model.ProxyProfile
import app.nebula.vpn.core.model.RoutingRule
import app.nebula.vpn.core.model.RuleAction
import app.nebula.vpn.core.model.RuleType
import app.nebula.vpn.core.model.TlsConfig
import app.nebula.vpn.core.model.TransportConfig
import app.nebula.vpn.core.model.TransportType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Builds standard sing-box JSON configuration strictly compliant with sing-box v1.14.x.
 * Verified with `sing-box check -c`.
 */
object SingBoxConfigBuilder {

    private val jsonSerializer = Json { prettyPrint = true }

    data class BuildConfig(
        val activeProfile: ProxyProfile?,
        val allProfiles: List<ProxyProfile> = emptyList(),
        val proxyGroups: List<ProxyGroup> = emptyList(),
        val customRules: List<RoutingRule> = emptyList(),
        val routingMode: AppRoutingMode = AppRoutingMode.GLOBAL,
        val targetPackages: Set<String> = emptySet(),
        val remoteDnsUrl: String = "https://1.1.1.1/dns-query",
        val fakeDnsEnabled: Boolean = true,
        val killSwitchEnabled: Boolean = false,
        val mtu: Int = 1500,
        val localPortMixedInbound: Int? = null, // Optional local SOCKS/HTTP inbound
        val ipv6Enabled: Boolean = false,
        val blockQuic: Boolean = true,
        val logLevel: String = "warn"
    )

    fun build(config: BuildConfig): String {
        val root = buildJsonObject {
            // 1. Log configuration
            putJsonObject("log") {
                put("level", config.logLevel)
                put("timestamp", true)
            }

            // 2. DNS configuration
            val hasProfiles = config.activeProfile != null || config.allProfiles.isNotEmpty()
            val proxyDetourTag = if (hasProfiles) "select" else "direct"

            putJsonObject("dns") {
                putJsonArray("servers") {
                    // 1. Primary remote DNS (DoH over TCP 443 through proxy) - works over 100% of proxies (including Cloudflare CDN WebSockets/gRPC)
                    add(buildJsonObject {
                        put("type", "https")
                        put("tag", "dns-remote-doh")
                        put("server", "1.1.1.1")
                        put("path", "/dns-query")
                        put("detour", proxyDetourTag)
                    })
                    // 2. Secondary remote DNS (Google DoH over TCP 443 through proxy)
                    add(buildJsonObject {
                        put("type", "https")
                        put("tag", "dns-remote-google")
                        put("server", "8.8.8.8")
                        put("path", "/dns-query")
                        put("detour", proxyDetourTag)
                    })
                    // 3. User configured remote DNS (DoH or custom UDP)
                    if (config.remoteDnsUrl.startsWith("https://")) {
                        val withoutScheme = config.remoteDnsUrl.removePrefix("https://")
                        val serverHost = withoutScheme.substringBefore("/")
                        val path = "/" + withoutScheme.substringAfter("/", "dns-query")
                        add(buildJsonObject {
                            put("type", "https")
                            put("tag", "dns-remote-custom")
                            put("server", serverHost)
                            put("path", path)
                            put("detour", proxyDetourTag)
                        })
                    } else if (config.remoteDnsUrl.isNotBlank()) {
                        val serverHost = config.remoteDnsUrl.removePrefix("udp://").substringBefore(":")
                        add(buildJsonObject {
                            put("type", "udp")
                            put("tag", "dns-remote-custom")
                            put("server", serverHost)
                            put("detour", proxyDetourTag)
                        })
                    }
                    // 4. Fast UDP remote DNS (for nodes with native UDP support)
                    add(buildJsonObject {
                        put("type", "udp")
                        put("tag", "dns-remote-udp")
                        put("server", "8.8.8.8")
                        put("detour", proxyDetourTag)
                    })
                    // 5. Direct DNS via Android system native resolver
                    add(buildJsonObject {
                        put("type", "local")
                        put("tag", "dns-local")
                        put("detour", "direct")
                    })
                    // 6. Direct DNS fallback (Yandex 77.88.8.8)
                    add(buildJsonObject {
                        put("type", "udp")
                        put("tag", "dns-direct-yandex")
                        put("server", "77.88.8.8")
                    })
                    // 7. Direct DNS fallback (Google 8.8.8.8)
                    add(buildJsonObject {
                        put("type", "udp")
                        put("tag", "dns-direct-google")
                        put("server", "8.8.8.8")
                    })
                    if (config.fakeDnsEnabled) {
                        add(buildJsonObject {
                            put("type", "fakeip")
                            put("tag", "dns-fakeip")
                            put("inet4_range", "198.18.0.0/15")
                        })
                    }
                }
                putJsonArray("rules") {
                    // Local / Private domains resolve directly via local DNS
                    add(buildJsonObject {
                        putJsonArray("domain_suffix") {
                            add(JsonPrimitive("local"))
                            add(JsonPrimitive("lan"))
                            add(JsonPrimitive("home.arpa"))
                            add(JsonPrimitive("internal"))
                        }
                        put("server", "dns-local")
                    })
                    // Russian domestic domains resolve directly via local/direct DNS
                    add(buildJsonObject {
                        putJsonArray("domain_suffix") {
                            add(JsonPrimitive("ru"))
                            add(JsonPrimitive("su"))
                            add(JsonPrimitive("xn--p1ai"))
                        }
                        put("server", "dns-local")
                    })
                    // User configured direct domains
                    val directDomains = config.customRules
                        .filter { it.isEnabled && it.action == RuleAction.DIRECT }
                        .map { it.value }
                    if (directDomains.isNotEmpty()) {
                        add(buildJsonObject {
                            putJsonArray("domain_suffix") {
                                for (d in directDomains) add(JsonPrimitive(d))
                            }
                            put("server", "dns-local")
                        })
                    }
                    // Android Private DNS popular domains resolve through remote proxy DoH
                    add(buildJsonObject {
                        putJsonArray("domain") {
                            add(JsonPrimitive("dns.google"))
                            add(JsonPrimitive("1dot1dot1dot1.cloudflare-dns.com"))
                            add(JsonPrimitive("cloudflare-dns.com"))
                            add(JsonPrimitive("one.one.one.one"))
                            add(JsonPrimitive("dns.alidns.com"))
                        }
                        put("server", "dns-remote-doh")
                    })
                    // FakeIP for all other traffic when enabled
                    if (config.fakeDnsEnabled) {
                        add(buildJsonObject {
                            putJsonArray("inbound") {
                                add(JsonPrimitive("tun-in"))
                            }
                            put("server", "dns-fakeip")
                        })
                    }
                }
                put("strategy", if (config.ipv6Enabled) "prefer_ipv4" else "ipv4_only")
            }

            // 3. Inbounds
            putJsonArray("inbounds") {
                add(buildJsonObject {
                    put("type", "tun")
                    put("tag", "tun-in")
                    putJsonArray("address") {
                        add(JsonPrimitive("172.19.0.1/30"))
                        if (config.ipv6Enabled) {
                            add(JsonPrimitive("fdfe:dcba:9876::1/126"))
                        }
                    }
                    put("mtu", config.mtu)
                    put("auto_route", true)
                    put("strict_route", config.killSwitchEnabled)
                    put("endpoint_independent_nat", true)
                    put("stack", "gvisor")
                })

                if (config.localPortMixedInbound != null && config.localPortMixedInbound > 0) {
                    add(buildJsonObject {
                        put("type", "mixed")
                        put("tag", "mixed-in")
                        put("listen", "127.0.0.1")
                        put("listen_port", config.localPortMixedInbound)
                    })
                }
            }

            // 4. Outbounds
            val outboundsList = mutableListOf<JsonObject>()
            val allProfilesList = if (config.allProfiles.isNotEmpty()) {
                config.allProfiles
            } else if (config.activeProfile != null) {
                listOf(config.activeProfile)
            } else {
                emptyList()
            }

            if (allProfilesList.isNotEmpty()) {
                val activeId = config.activeProfile?.id ?: allProfilesList.first().id
                val profileTags = allProfilesList.map { "proxy-${it.id}" }

                // Selector outbound for instant, seamless hot-switching
                outboundsList.add(buildJsonObject {
                    put("type", "selector")
                    put("tag", "select")
                    putJsonArray("outbounds") {
                        for (tag in profileTags) add(JsonPrimitive(tag))
                    }
                    put("default", "proxy-$activeId")
                })

                // Add all individual node outbounds
                for (p in allProfilesList) {
                    outboundsList.add(buildOutboundForProfile(p, "proxy-${p.id}"))
                }
            }

            // Built-in outbounds
            outboundsList.add(buildJsonObject {
                put("type", "direct")
                put("tag", "direct")
            })
            outboundsList.add(buildJsonObject {
                put("type", "block")
                put("tag", "block")
            })

            putJsonArray("outbounds") {
                for (outbound in outboundsList) {
                    add(outbound)
                }
            }

            // 5. Route rules
            val finalTarget = if (allProfilesList.isNotEmpty()) "select" else "direct"

            putJsonObject("route") {
                put("default_domain_resolver", "dns-local")
                put("final", finalTarget)

                val referencedRuleSets = config.customRules
                    .filter { it.isEnabled && it.type == RuleType.RULE_SET }
                    .map { it.value }
                    .distinct()

                if (referencedRuleSets.isNotEmpty()) {
                    putJsonArray("rule_set") {
                        for (rs in referencedRuleSets) {
                            add(buildJsonObject {
                                put("tag", rs)
                                put("type", "remote")
                                put("format", "binary")
                                put("url", "https://raw.githubusercontent.com/SagerNet/sing-geoip/rule-set/$rs.srs")
                                put("download_detour", "direct")
                            })
                        }
                    }
                }

                putJsonArray("rules") {
                    // Protocol sniffing
                    add(buildJsonObject {
                        put("action", "sniff")
                    })

                    // Block/Reject QUIC (UDP 443) to force fast TCP HTTP/2 fallback on filtered networks
                    if (config.blockQuic) {
                        add(buildJsonObject {
                            put("protocol", "quic")
                            put("action", "reject")
                        })
                        add(buildJsonObject {
                            put("network", "udp")
                            putJsonArray("port") {
                                add(JsonPrimitive(443))
                            }
                            put("action", "reject")
                        })
                    }

                    // Intercept DNS
                    add(buildJsonObject {
                        put("protocol", "dns")
                        put("action", "hijack-dns")
                    })
                    add(buildJsonObject {
                        putJsonArray("port") {
                            add(JsonPrimitive(53))
                        }
                        put("action", "hijack-dns")
                    })

                    // Always bypass private / LAN networks
                    add(buildJsonObject {
                        put("ip_is_private", true)
                        put("outbound", "direct")
                    })

                    // Route Telegram direct IP ranges through proxy
                    add(buildJsonObject {
                        putJsonArray("ip_cidr") {
                            add(JsonPrimitive("91.108.4.0/22"))
                            add(JsonPrimitive("91.108.8.0/22"))
                            add(JsonPrimitive("91.108.12.0/22"))
                            add(JsonPrimitive("91.108.16.0/22"))
                            add(JsonPrimitive("91.108.20.0/22"))
                            add(JsonPrimitive("91.108.56.0/22"))
                            add(JsonPrimitive("149.154.160.0/20"))
                            add(JsonPrimitive("149.154.164.0/22"))
                            add(JsonPrimitive("149.154.168.0/22"))
                            add(JsonPrimitive("149.154.172.0/22"))
                        }
                        put("outbound", finalTarget)
                    })

                    // Per-App routing rules (at the sing-box route layer)
                    if (config.targetPackages.isNotEmpty()) {
                        when (config.routingMode) {
                            AppRoutingMode.FORWARD_APPS -> {
                                add(buildJsonObject {
                                    putJsonArray("package_name") {
                                        for (pkg in config.targetPackages) add(JsonPrimitive(pkg))
                                    }
                                    put("outbound", finalTarget)
                                })
                                add(buildJsonObject {
                                    put("outbound", "direct")
                                })
                            }
                            AppRoutingMode.BYPASS_APPS -> {
                                add(buildJsonObject {
                                    putJsonArray("package_name") {
                                        for (pkg in config.targetPackages) add(JsonPrimitive(pkg))
                                    }
                                    put("outbound", "direct")
                                })
                            }
                            AppRoutingMode.GLOBAL -> { /* no package rules needed */ }
                        }
                    }

                    // User custom rules (sorted by priority)
                    for (rule in config.customRules.sortedBy { it.priority }) {
                        if (!rule.isEnabled) continue
                        val ruleTarget = when (rule.action) {
                            RuleAction.DIRECT -> "direct"
                            RuleAction.BLOCK -> "block"
                            RuleAction.PROXY -> rule.targetOutbound ?: finalTarget
                        }

                        add(buildJsonObject {
                            when (rule.type) {
                                RuleType.DOMAIN -> putJsonArray("domain") { add(JsonPrimitive(rule.value)) }
                                RuleType.DOMAIN_SUFFIX -> putJsonArray("domain_suffix") { add(JsonPrimitive(rule.value)) }
                                RuleType.DOMAIN_KEYWORD -> putJsonArray("domain_keyword") { add(JsonPrimitive(rule.value)) }
                                RuleType.DOMAIN_REGEX -> putJsonArray("domain_regex") { add(JsonPrimitive(rule.value)) }
                                RuleType.IP_CIDR -> putJsonArray("ip_cidr") { add(JsonPrimitive(rule.value)) }
                                RuleType.PACKAGE_NAME -> putJsonArray("package_name") { add(JsonPrimitive(rule.value)) }
                                RuleType.RULE_SET -> putJsonArray("rule_set") { add(JsonPrimitive(rule.value)) }
                            }
                            put("outbound", ruleTarget)
                        })
                    }
                }
            }
        }

        return jsonSerializer.encodeToString(JsonObject.serializer(), root)
    }

    private fun buildTlsObject(
        tls: TlsConfig,
        defaultServerName: String,
        transportType: TransportType = TransportType.TCP
    ): JsonObject {
        return buildJsonObject {
            put("enabled", true)
            val sni = tls.serverName.ifEmpty { defaultServerName }
            if (sni.isNotEmpty()) {
                put("server_name", sni)
            }
            if (tls.realityPublicKey.isEmpty() && tls.insecure) {
                put("insecure", true)
            }
            val alpnToUse = if (tls.alpn.isNotEmpty()) {
                tls.alpn
            } else if (transportType == TransportType.WS) {
                listOf("http/1.1")
            } else {
                emptyList()
            }
            if (alpnToUse.isNotEmpty()) {
                putJsonArray("alpn") {
                    for (a in alpnToUse) add(JsonPrimitive(a))
                }
            }
            val rawFp = tls.utlsFingerprint.trim().lowercase()
            val supportedFp = setOf("chrome", "firefox", "edge", "safari", "360", "qq", "ios", "android", "random", "randomized")
            val fpToUse = when {
                rawFp in supportedFp -> rawFp
                rawFp in setOf("unsafe", "none", "off", "disable", "disabled") -> null
                tls.realityPublicKey.isNotEmpty() -> "chrome"
                rawFp.isEmpty() -> "chrome"
                else -> null
            }
            if (fpToUse != null) {
                putJsonObject("utls") {
                    put("enabled", true)
                    put("fingerprint", fpToUse)
                }
            }
            if (tls.realityPublicKey.isNotEmpty()) {
                putJsonObject("reality") {
                    put("enabled", true)
                    put("public_key", tls.realityPublicKey)
                    if (tls.realityShortId.isNotEmpty()) {
                        put("short_id", tls.realityShortId)
                    }
                }
            }
        }
    }

    private fun buildTransportObject(
        transport: TransportConfig,
        defaultHost: String = "",
        sni: String = ""
    ): JsonObject? {
        if (transport.type == TransportType.TCP) return null
        return buildJsonObject {
            when (transport.type) {
                TransportType.WS -> {
                    put("type", "ws")
                    val rawPath = transport.path
                    val path = when {
                        rawPath.isEmpty() -> "/"
                        rawPath.startsWith("/") -> rawPath
                        else -> "/$rawPath"
                    }
                    put("path", path)
                    val hostHeader = when {
                        transport.host.isNotEmpty() -> transport.host
                        sni.isNotEmpty() -> sni
                        defaultHost.isNotEmpty() -> defaultHost
                        else -> ""
                    }
                    if (hostHeader.isNotEmpty()) {
                        putJsonObject("headers") {
                            put("Host", hostHeader)
                        }
                    }
                    if (path.contains("ed=") || path.contains("early_data")) {
                        put("max_early_data", 2048)
                        put("early_data_header_name", "Sec-WebSocket-Protocol")
                    }
                }
                TransportType.GRPC -> {
                    put("type", "grpc")
                    if (transport.serviceName.isNotEmpty()) {
                        put("service_name", transport.serviceName)
                    }
                }
                TransportType.HTTP_UPGRADE -> {
                    put("type", "httpupgrade")
                    val rawPath = transport.path
                    val path = when {
                        rawPath.isEmpty() -> "/"
                        rawPath.startsWith("/") -> rawPath
                        else -> "/$rawPath"
                    }
                    put("path", path)
                    val hostHeader = when {
                        transport.host.isNotEmpty() -> transport.host
                        sni.isNotEmpty() -> sni
                        defaultHost.isNotEmpty() -> defaultHost
                        else -> ""
                    }
                    if (hostHeader.isNotEmpty()) {
                        put("host", hostHeader)
                    }
                }
                else -> {}
            }
        }
    }

    private fun buildOutboundForProfile(profile: ProxyProfile, tag: String): JsonObject {
        return buildJsonObject {
            when (profile.protocol) {
                ProtocolType.SHADOWSOCKS -> {
                    put("type", "shadowsocks")
                    put("tag", tag)
                    put("server", profile.server)
                    put("server_port", profile.port)
                    put("method", profile.encryption.ifEmpty { "aes-128-gcm" })
                    put("password", profile.authSecret)
                }
                ProtocolType.VLESS -> {
                    put("type", "vless")
                    put("tag", tag)
                    put("server", profile.server)
                    put("server_port", profile.port)
                    put("uuid", profile.authSecret)
                    put("packet_encoding", "xudp")
                    val flowVal = when {
                        profile.transport.type == TransportType.TCP && profile.tls.flow.isNotEmpty() -> profile.tls.flow
                        profile.transport.type == TransportType.TCP && profile.tls.realityPublicKey.isNotEmpty() -> "xtls-rprx-vision"
                        else -> null
                    }
                    if (flowVal != null) {
                        put("flow", flowVal)
                    }
                    if (profile.tls.enabled) {
                        put("tls", buildTlsObject(profile.tls, profile.server, profile.transport.type))
                    }
                    buildTransportObject(profile.transport, profile.server, profile.tls.serverName)?.let {
                        put("transport", it)
                    }
                }
                ProtocolType.VMESS -> {
                    put("type", "vmess")
                    put("tag", tag)
                    put("server", profile.server)
                    put("server_port", profile.port)
                    put("uuid", profile.authSecret)
                    put("security", profile.encryption.ifEmpty { "auto" })
                    put("alter_id", 0)
                    put("packet_encoding", "xudp")
                    if (profile.tls.enabled) {
                        put("tls", buildTlsObject(profile.tls, profile.server, profile.transport.type))
                    }
                    buildTransportObject(profile.transport, profile.server, profile.tls.serverName)?.let {
                        put("transport", it)
                    }
                }
                ProtocolType.TROJAN -> {
                    put("type", "trojan")
                    put("tag", tag)
                    put("server", profile.server)
                    put("server_port", profile.port)
                    put("password", profile.authSecret)
                    val tlsToUse = if (profile.tls.enabled) profile.tls else profile.tls.copy(enabled = true)
                    put("tls", buildTlsObject(tlsToUse, profile.server, profile.transport.type))
                    buildTransportObject(profile.transport, profile.server, profile.tls.serverName)?.let {
                        put("transport", it)
                    }
                }
                ProtocolType.HYSTERIA2 -> {
                    put("type", "hysteria2")
                    put("tag", tag)
                    put("server", profile.server)
                    put("server_port", profile.port)
                    put("password", profile.authSecret)
                    if (profile.tls.enabled) {
                        put("tls", buildTlsObject(profile.tls, profile.server))
                    }
                }
            }
        }
    }
}
