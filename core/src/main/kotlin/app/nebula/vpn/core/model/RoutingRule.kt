package app.nebula.vpn.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class RuleType {
    DOMAIN,
    DOMAIN_SUFFIX,
    DOMAIN_KEYWORD,
    DOMAIN_REGEX,
    IP_CIDR,
    PACKAGE_NAME,
    RULE_SET,
    PROCESS_NAME,
    PROCESS_PATH
}

@Serializable
enum class RuleAction {
    PROXY,
    DIRECT,
    BLOCK
}

@Serializable
data class RoutingRule(
    val id: String,
    val type: RuleType,
    val value: String,
    val action: RuleAction = RuleAction.PROXY,
    val targetOutbound: String? = null,
    val priority: Int = 0,
    val isEnabled: Boolean = true
)

@Serializable
enum class AppRoutingMode {
    GLOBAL,       // Proxy everything
    BYPASS_APPS,  // Blacklist (proxy all apps EXCEPT selected)
    FORWARD_APPS  // Whitelist (proxy ONLY selected apps)
}
