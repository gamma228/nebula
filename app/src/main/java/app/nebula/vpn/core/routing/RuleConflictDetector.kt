package app.nebula.vpn.core.routing

import app.nebula.vpn.core.model.RoutingRule
import app.nebula.vpn.core.model.RuleType

object RuleConflictDetector {

    /**
     * Identifies rules in the given list that are shadowed (i.e. will never match)
     * by an earlier higher-priority enabled rule.
     * Returns a Set of rule IDs that are shadowed.
     */
    fun findShadowedRules(rules: List<RoutingRule>): Set<String> {
        val shadowed = mutableSetOf<String>()
        val sorted = rules.sortedBy { it.priority }

        for (i in sorted.indices) {
            val candidate = sorted[i]
            if (!candidate.isEnabled) continue

            for (j in 0 until i) {
                val prior = sorted[j]
                if (!prior.isEnabled) continue

                if (isShadowedBy(candidate, prior)) {
                    shadowed.add(candidate.id)
                    break
                }
            }
        }

        return shadowed
    }

    private fun isShadowedBy(candidate: RoutingRule, prior: RoutingRule): Boolean {
        // Exact duplicate
        if (candidate.type == prior.type && candidate.value.equals(prior.value, ignoreCase = true)) {
            return true
        }

        // Domain rules
        val candVal = candidate.value.lowercase()
        val priorVal = prior.value.lowercase()

        when (prior.type) {
            RuleType.DOMAIN_KEYWORD -> {
                if (candidate.type == RuleType.DOMAIN ||
                    candidate.type == RuleType.DOMAIN_SUFFIX ||
                    candidate.type == RuleType.DOMAIN_KEYWORD
                ) {
                    if (candVal.contains(priorVal)) return true
                }
            }
            RuleType.DOMAIN_SUFFIX -> {
                if (candidate.type == RuleType.DOMAIN_SUFFIX || candidate.type == RuleType.DOMAIN) {
                    if (candVal == priorVal || candVal.endsWith(".$priorVal")) {
                        return true
                    }
                }
            }
            RuleType.IP_CIDR -> {
                if (candidate.type == RuleType.IP_CIDR) {
                    if (isSubnetContained(candidate.value, prior.value)) {
                        return true
                    }
                }
            }
            else -> {}
        }

        return false
    }

    private fun isSubnetContained(childCidr: String, parentCidr: String): Boolean {
        try {
            val (childIp, childMask) = parseCidr(childCidr)
            val (parentIp, parentMask) = parseCidr(parentCidr)

            if (childMask < parentMask) return false

            val maskBit = if (parentMask == 0) 0 else (-1 shl (32 - parentMask))
            return (childIp and maskBit) == (parentIp and maskBit)
        } catch (e: Exception) {
            return false
        }
    }

    private fun parseCidr(cidr: String): Pair<Int, Int> {
        val parts = cidr.split("/")
        val ipParts = parts[0].split(".").map { it.toInt() }
        val ip = (ipParts[0] shl 24) or (ipParts[1] shl 16) or (ipParts[2] shl 8) or ipParts[3]
        val mask = if (parts.size > 1) parts[1].toInt() else 32
        return Pair(ip, mask)
    }
}
