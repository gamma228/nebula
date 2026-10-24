package app.nebula.vpn.core.parser

import app.nebula.vpn.core.model.GroupType
import app.nebula.vpn.core.model.ProtocolType
import app.nebula.vpn.core.model.ProxyGroup
import app.nebula.vpn.core.model.ProxyProfile
import app.nebula.vpn.core.model.Result
import app.nebula.vpn.core.model.RoutingRule
import app.nebula.vpn.core.model.RuleAction
import app.nebula.vpn.core.model.RuleType
import app.nebula.vpn.core.model.TlsConfig
import app.nebula.vpn.core.model.TransportConfig
import app.nebula.vpn.core.model.TransportType
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import java.util.UUID

object ClashYamlParser {

    data class ClashParsedResult(
        val profiles: List<ProxyProfile>,
        val groups: List<ProxyGroup>,
        val rules: List<RoutingRule>
    )

    fun parse(yamlContent: String): Result<ClashParsedResult> = try {
        val settings = LoadSettings.builder().build()
        val load = Load(settings)
        val data = load.loadFromString(yamlContent) as? Map<*, *>
            ?: return Result.Error(IllegalArgumentException("YAML root must be a map"))

        val profiles = mutableListOf<ProxyProfile>()
        val profileNameToId = mutableMapOf<String, String>()

        // 1. Parse Proxies
        val rawProxies = data["proxies"] as? List<*>
        if (rawProxies != null) {
            for (item in rawProxies) {
                val map = item as? Map<*, *> ?: continue
                parseProxyItem(map)?.let { profile ->
                    profiles.add(profile)
                    profileNameToId[profile.name] = profile.id
                }
            }
        }

        // 2. Parse Proxy Groups
        val groups = mutableListOf<ProxyGroup>()
        val rawGroups = data["proxy-groups"] as? List<*>
        if (rawGroups != null) {
            for (item in rawGroups) {
                val map = item as? Map<*, *> ?: continue
                val name = map["name"]?.toString() ?: continue
                val typeStr = map["type"]?.toString()?.lowercase() ?: "select"
                val groupType = when (typeStr) {
                    "url-test" -> GroupType.URL_TEST
                    "fallback" -> GroupType.FALLBACK
                    "load-balance" -> GroupType.LOAD_BALANCE
                    else -> GroupType.SELECTOR
                }
                val rawMembers = map["proxies"] as? List<*> ?: emptyList<Any>()
                val memberIds = rawMembers.mapNotNull { profileNameToId[it.toString()] }
                val url = map["url"]?.toString() ?: "https://www.gstatic.com/generate_204"
                val interval = (map["interval"] as? Number)?.toInt() ?: 300

                groups.add(
                    ProxyGroup(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        type = groupType,
                        profileIds = memberIds,
                        testUrl = url,
                        testIntervalSeconds = interval
                    )
                )
            }
        }

        // 3. Parse Rules
        val rules = mutableListOf<RoutingRule>()
        val rawRules = data["rules"] as? List<*>
        if (rawRules != null) {
            var priority = 0
            for (ruleItem in rawRules) {
                val line = ruleItem?.toString() ?: continue
                val parts = line.split(",")
                if (parts.size >= 3) {
                    val typeStr = parts[0].trim().uppercase()
                    val payload = parts[1].trim()
                    val target = parts[2].trim()

                    val ruleType = when (typeStr) {
                        "DOMAIN-SUFFIX" -> RuleType.DOMAIN_SUFFIX
                        "DOMAIN-KEYWORD" -> RuleType.DOMAIN_KEYWORD
                        "DOMAIN" -> RuleType.DOMAIN
                        "IP-CIDR", "IP-CIDR6" -> RuleType.IP_CIDR
                        "GEOIP" -> RuleType.RULE_SET
                        else -> null
                    }
                    if (ruleType != null) {
                        val action = when (target.uppercase()) {
                            "DIRECT" -> RuleAction.DIRECT
                            "REJECT" -> RuleAction.BLOCK
                            else -> RuleAction.PROXY
                        }
                        rules.add(
                            RoutingRule(
                                id = UUID.randomUUID().toString(),
                                type = ruleType,
                                value = payload,
                                action = action,
                                targetOutbound = target,
                                priority = priority++
                            )
                        )
                    }
                }
            }
        }

        Result.Success(ClashParsedResult(profiles, groups, rules))
    } catch (e: Exception) {
        Result.Error(e, "Failed to parse Clash YAML: ${e.message}")
    }

    private fun parseProxyItem(map: Map<*, *>): ProxyProfile? {
        val name = map["name"]?.toString() ?: return null
        val type = map["type"]?.toString()?.lowercase() ?: return null
        val server = map["server"]?.toString() ?: return null
        val port = (map["port"] as? Number)?.toInt() ?: return null

        val id = UUID.randomUUID().toString()
        val isTls = (map["tls"] as? Boolean) ?: false
        val sni = map["sni"]?.toString() ?: server

        val network = map["network"]?.toString()?.lowercase() ?: "tcp"
        val transportType = when (network) {
            "ws" -> TransportType.WS
            "grpc" -> TransportType.GRPC
            "httpupgrade" -> TransportType.HTTP_UPGRADE
            else -> TransportType.TCP
        }
        val wsOpts = map["ws-opts"] as? Map<*, *>
        val wsPath = wsOpts?.get("path")?.toString() ?: ""

        val realityOpts = map["reality-opts"] as? Map<*, *>
        val pbk = realityOpts?.get("public-key")?.toString() ?: ""
        val sid = realityOpts?.get("short-id")?.toString() ?: ""

        return when (type) {
            "ss" -> {
                val cipher = map["cipher"]?.toString() ?: "aes-128-gcm"
                val password = map["password"]?.toString() ?: ""
                ProxyProfile(
                    id = id,
                    name = name,
                    protocol = ProtocolType.SHADOWSOCKS,
                    server = server,
                    port = port,
                    authSecret = password,
                    encryption = cipher
                )
            }
            "vmess" -> {
                val uuid = map["uuid"]?.toString() ?: ""
                val cipher = map["cipher"]?.toString() ?: "auto"
                ProxyProfile(
                    id = id,
                    name = name,
                    protocol = ProtocolType.VMESS,
                    server = server,
                    port = port,
                    authSecret = uuid,
                    encryption = cipher,
                    tls = TlsConfig(enabled = isTls, serverName = sni),
                    transport = TransportConfig(type = transportType, path = wsPath)
                )
            }
            "vless" -> {
                val uuid = map["uuid"]?.toString() ?: ""
                ProxyProfile(
                    id = id,
                    name = name,
                    protocol = ProtocolType.VLESS,
                    server = server,
                    port = port,
                    authSecret = uuid,
                    tls = TlsConfig(
                        enabled = isTls || pbk.isNotEmpty(),
                        serverName = sni,
                        realityPublicKey = pbk,
                        realityShortId = sid
                    ),
                    transport = TransportConfig(type = transportType, path = wsPath)
                )
            }
            "trojan" -> {
                val password = map["password"]?.toString() ?: ""
                ProxyProfile(
                    id = id,
                    name = name,
                    protocol = ProtocolType.TROJAN,
                    server = server,
                    port = port,
                    authSecret = password,
                    tls = TlsConfig(enabled = true, serverName = sni),
                    transport = TransportConfig(type = transportType, path = wsPath)
                )
            }
            "hysteria2", "hy2" -> {
                val password = map["password"]?.toString() ?: ""
                ProxyProfile(
                    id = id,
                    name = name,
                    protocol = ProtocolType.HYSTERIA2,
                    server = server,
                    port = port,
                    authSecret = password,
                    tls = TlsConfig(enabled = true, serverName = sni)
                )
            }
            else -> null
        }
    }
}
