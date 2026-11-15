package app.nebula.vpn.core.routing

import app.nebula.vpn.core.model.RoutingRule
import app.nebula.vpn.core.model.RuleAction
import app.nebula.vpn.core.model.RuleType

data class SimulationResult(
    val matchedRule: RoutingRule?,
    val ruleIndex: Int?,
    val action: RuleAction,
    val reason: String
)

object RuleSimulatorEngine {

    fun simulate(queryRaw: String, rules: List<RoutingRule>): SimulationResult {
        val query = queryRaw.trim().lowercase()
        if (query.isEmpty()) {
            return SimulationResult(
                matchedRule = null,
                ruleIndex = null,
                action = RuleAction.PROXY,
                reason = "Empty target query"
            )
        }

        rules.forEachIndexed { idx, rule ->
            val ruleVal = rule.value.trim().lowercase()

            val isMatch = when (rule.type) {
                RuleType.DOMAIN -> query == ruleVal
                RuleType.DOMAIN_SUFFIX -> query == ruleVal || query.endsWith(".$ruleVal")
                RuleType.DOMAIN_KEYWORD -> query.contains(ruleVal)
                RuleType.DOMAIN_REGEX -> try {
                    Regex(ruleVal).containsMatchIn(query)
                } catch (_: Exception) {
                    false
                }
                RuleType.IP_CIDR -> matchesCidr(query, ruleVal)
                RuleType.PROCESS_NAME -> query == ruleVal || query.endsWith(ruleVal) || query.endsWith("\\$ruleVal")
                RuleType.PROCESS_PATH -> query.contains(ruleVal)
                RuleType.PACKAGE_NAME -> query.contains(ruleVal)
                RuleType.RULE_SET -> false
            }

            if (isMatch) {
                return SimulationResult(
                    matchedRule = rule,
                    ruleIndex = idx + 1,
                    action = rule.action,
                    reason = "Matched Rule #${idx + 1} (${rule.type.name}: '${rule.value}')"
                )
            }
        }

        return SimulationResult(
            matchedRule = null,
            ruleIndex = null,
            action = RuleAction.PROXY,
            reason = "No custom rules matched. Falling back to default proxy outbound."
        )
    }

    private fun matchesCidr(ipStr: String, cidrStr: String): Boolean {
        if (ipStr == cidrStr) return true
        val parts = cidrStr.split("/")
        if (parts.size != 2) return ipStr == cidrStr
        val baseIp = parts[0]
        val prefixLen = parts[1].toIntOrNull() ?: return ipStr == baseIp
        val ipLong = parseIpv4ToLong(ipStr) ?: return false
        val baseLong = parseIpv4ToLong(baseIp) ?: return false
        if (prefixLen <= 0) return true
        if (prefixLen >= 32) return ipLong == baseLong
        val mask = (0xFFFFFFFFL shl (32 - prefixLen)) and 0xFFFFFFFFL
        return (ipLong and mask) == (baseLong and mask)
    }

    private fun parseIpv4ToLong(ip: String): Long? {
        val octets = ip.split(".")
        if (octets.size != 4) return null
        var res = 0L
        for (oct in octets) {
            val num = oct.toLongOrNull() ?: return null
            if (num !in 0..255) return null
            res = (res shl 8) or num
        }
        return res
    }
}
