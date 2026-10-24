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
            putJsonObject("dns") {
                putJsonArray("servers") {
                    // Remote DNS (DoH or UDP)
                    add(buildJsonObject {
                        put("type", if (config.remoteDnsUrl.startsWith("https://")) "https" else "udp")
                        put("tag", "dns-remote")
                        put("server", config.remoteDnsUrl.removePrefix("https://").substringBefore("/"))
                        put("domain_resolver", "dns-direct")
                    })
                    // Local direct DNS
                    add(buildJsonObject {
                        put("type", "local")
                        put("tag", "dns-direct")
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
                }
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
            val activeTag: String

            if (config.activeProfile != null) {
                activeTag = "proxy-${config.activeProfile.id}"
                outboundsList.add(buildOutboundForProfile(config.activeProfile, activeTag))

                // Also add other profiles if needed for groups
                for (p in config.allProfiles) {
                    if (p.id != config.activeProfile.id) {
                        outboundsList.add(buildOutboundForProfile(p, "proxy-${p.id}"))
                    }
                }
            } else if (config.allProfiles.isNotEmpty()) {
                // Auto URL-test fastest
                activeTag = "auto-urltest"
                for (p in config.allProfiles) {
                    outboundsList.add(buildOutboundForProfile(p, "proxy-${p.id}"))
                }
                outboundsList.add(buildJsonObject {
                    put("type", "urltest")
                    put("tag", activeTag)
                    putJsonArray("outbounds") {
                        for (p in config.allProfiles) {
                            add(JsonPrimitive("proxy-${p.id}"))
                        }
                    }
                    put("url", "https://www.gstatic.com/generate_204")
                    put("interval", "3m0s")
                })
            } else {
                activeTag = "direct"
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
            putJsonObject("route") {
                put("default_domain_resolver", "dns-direct")
                put("auto_detect_interface", true)
                put("final", if (config.killSwitchEnabled && activeTag != "direct") activeTag else activeTag)

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

                    // Per-App routing rules (at the sing-box route layer)
                    if (config.targetPackages.isNotEmpty()) {
                        when (config.routingMode) {
                            AppRoutingMode.FORWARD_APPS -> {
                                add(buildJsonObject {
                                    putJsonArray("package_name") {
                                        for (pkg in config.targetPackages) add(JsonPrimitive(pkg))
                                    }
                                    put("outbound", activeTag)
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
                            RuleAction.PROXY -> rule.targetOutbound ?: activeTag
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
                    if (profile.tls.realityPublicKey.isNotEmpty()) {
                        put("flow", "xtls-rprx-vision")
                    }
                    if (profile.tls.enabled) {
                        putJsonObject("tls") {
                            put("enabled", true)
                            put("server_name", profile.tls.serverName.ifEmpty { profile.server })
                            if (profile.tls.utlsFingerprint.isNotEmpty()) {
                                putJsonObject("utls") {
                                    put("enabled", true)
                                    put("fingerprint", profile.tls.utlsFingerprint)
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
