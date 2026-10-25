package app.nebula.vpn.core.builder

import app.nebula.vpn.core.model.AppRoutingMode
import app.nebula.vpn.core.model.GroupType
import app.nebula.vpn.core.model.ProtocolType
import app.nebula.vpn.core.model.ProxyGroup
import app.nebula.vpn.core.model.ProxyProfile
import app.nebula.vpn.core.model.RoutingRule
import app.nebula.vpn.core.model.RuleAction
import app.nebula.vpn.core.model.RuleType
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

    data class BuildConfig(
        val activeProfile: ProxyProfile?,
        val allProfiles: List<ProxyProfile> = emptyList(),
        val proxyGroups: List<ProxyGroup> = emptyList(),
        val customRules: List<RoutingRule> = emptyList(),
        val routingMode: AppRoutingMode = AppRoutingMode.GLOBAL,
        val targetPackages: Set<String> = emptySet(),
        val remoteDnsUrl: String = "https://1.1.1.1/dns-query",
        val fakeDnsEnabled: Boolean = false,
        val killSwitchEnabled: Boolean = false,
        val mtu: Int = 9000,
        val localPortMixedInbound: Int? = null // Optional local SOCKS/HTTP inbound
    )

    fun build(config: BuildConfig): String {
        val root = buildJsonObject {
            // 1. Log configuration
            putJsonObject("log") {
                put("level", "info")
                put("timestamp", true)
            }

            // 2. DNS configuration
            val hasProfiles = config.activeProfile != null || config.allProfiles.isNotEmpty()
            val proxyDetourTag = if (hasProfiles) "select" else "direct"

            putJsonObject("dns") {
                putJsonArray("servers") {
                    // Remote DNS (DoH or UDP) detoured to the proxy for complete bypass
                    add(buildJsonObject {
                        put("type", if (config.remoteDnsUrl.startsWith("https://")) "https" else "udp")
                        put("tag", "dns-remote")
                        put("server", config.remoteDnsUrl.removePrefix("https://").substringBefore("/"))
                        put("detour", proxyDetourTag)
                        put("domain_resolver", "dns-direct")
                    })
                    // Local direct DNS for resolving proxy node server addresses & local domains
                    add(buildJsonObject {
                        put("type", "local")
                        put("tag", "dns-direct")
                        put("detour", "direct")
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
                    if (config.fakeDnsEnabled) {
                        add(buildJsonObject {
                            put("inbound", "tun-in")
                            put("server", "dns-fakeip")
                        })
                    }
                    val directDomains = config.customRules
                        .filter { it.isEnabled && it.action == RuleAction.DIRECT }
                        .map { it.value }
                    if (directDomains.isNotEmpty()) {
                        add(buildJsonObject {
                            putJsonArray("domain_suffix") {
                                for (d in directDomains) add(JsonPrimitive(d))
                            }
                            put("server", "dns-direct")
                        })
                    }
                }
                put("strategy", "prefer_ipv4")
                if (config.fakeDnsEnabled) {
                    putJsonObject("fakeip") {
                        put("enabled", true)
                        put("inet4_range", "198.18.0.0/15")
                    }
                }
            }

            // 3. Inbounds
            putJsonArray("inbounds") {
                add(buildJsonObject {
                    put("type", "tun")
                    put("tag", "tun-in")
                    putJsonArray("address") {
                        add(JsonPrimitive("172.19.0.1/30"))
                        add(JsonPrimitive("fdfe:dcba:9876::1/126"))
                    }
                    put("mtu", config.mtu)
                    put("auto_route", true)
                    put("strict_route", config.killSwitchEnabled)
                    put("endpoint_independent_nat", true)
                    put("stack", "mixed")
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
                put("default_domain_resolver", "dns-direct")
                put("auto_detect_interface", true)
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

        return Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), root)
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
                    val flowVal = when {
                        profile.tls.flow.isNotEmpty() -> profile.tls.flow
                        profile.tls.realityPublicKey.isNotEmpty() && profile.transport.type == TransportType.TCP -> "xtls-rprx-vision"
                        else -> null
                    }
                    if (flowVal != null) {
                        put("flow", flowVal)
                    }
                    if (profile.tls.enabled) {
                        putJsonObject("tls") {
                            put("enabled", true)
                            put("server_name", profile.tls.serverName.ifEmpty { profile.server })
                            if (profile.tls.realityPublicKey.isNotEmpty() || profile.tls.utlsFingerprint.isNotEmpty()) {
                                putJsonObject("utls") {
                                    put("enabled", true)
                                    put("fingerprint", profile.tls.utlsFingerprint.ifEmpty { "chrome" })
                                }
                            }
                            if (profile.tls.realityPublicKey.isNotEmpty()) {
                                putJsonObject("reality") {
                                    put("enabled", true)
                                    put("public_key", profile.tls.realityPublicKey)
                                    put("short_id", profile.tls.realityShortId)
                                }
                            }
                        }
                    }
                    if (profile.transport.type != TransportType.TCP) {
                        putJsonObject("transport") {
                            put("type", when (profile.transport.type) {
                                TransportType.WS -> "ws"
                                TransportType.GRPC -> "grpc"
                                TransportType.HTTP_UPGRADE -> "httpupgrade"
                                else -> "tcp"
                            })
                            if (profile.transport.path.isNotEmpty()) {
                                put("path", profile.transport.path)
                            }
                            if (profile.transport.serviceName.isNotEmpty()) {
                                put("service_name", profile.transport.serviceName)
                            }
                        }
                    }
                }
                ProtocolType.VMESS -> {
                    put("type", "vmess")
                    put("tag", tag)
                    put("server", profile.server)
                    put("server_port", profile.port)
                    put("uuid", profile.authSecret)
                    put("security", profile.encryption.ifEmpty { "auto" })
                    if (profile.tls.enabled) {
                        putJsonObject("tls") {
                            put("enabled", true)
                            put("server_name", profile.tls.serverName.ifEmpty { profile.server })
                        }
                    }
                    if (profile.transport.type != TransportType.TCP) {
                        putJsonObject("transport") {
                            put("type", if (profile.transport.type == TransportType.WS) "ws" else "grpc")
                            if (profile.transport.path.isNotEmpty()) {
                                put("path", profile.transport.path)
                            }
                        }
                    }
                }
                ProtocolType.TROJAN -> {
                    put("type", "trojan")
                    put("tag", tag)
                    put("server", profile.server)
                    put("server_port", profile.port)
                    put("password", profile.authSecret)
                    putJsonObject("tls") {
                        put("enabled", true)
                        put("server_name", profile.tls.serverName.ifEmpty { profile.server })
                    }
                }
                ProtocolType.HYSTERIA2 -> {
                    put("type", "hysteria2")
                    put("tag", tag)
                    put("server", profile.server)
                    put("server_port", profile.port)
                    put("password", profile.authSecret)
                    putJsonObject("tls") {
                        put("enabled", true)
                        put("server_name", profile.tls.serverName.ifEmpty { profile.server })
                        if (profile.tls.insecure) {
                            put("insecure", true)
                        }
                    }
                }
            }
        }
    }
}
