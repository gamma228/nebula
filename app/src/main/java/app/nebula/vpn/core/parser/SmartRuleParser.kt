package app.nebula.vpn.core.parser

import app.nebula.vpn.core.model.RoutingRule
import app.nebula.vpn.core.model.RuleAction
import app.nebula.vpn.core.model.RuleType
import java.util.UUID

data class SmartParseResult(
    val rules: List<RoutingRule>,
    val invalidLines: List<String>
)

object SmartRuleParser {

    private val IPV4_CIDR_REGEX = Regex("""^(\d{1,3}\.){3}\d{1,3}(/\d{1,2})?$""")
    private val PACKAGE_REGEX = Regex("""^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+)+$""")
    private val DOMAIN_REGEX = Regex("""^([a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\.)+[a-zA-Z]{2,}$""")

    private val COMMON_TLDS = setOf(
        "com", "org", "net", "edu", "gov", "mil", "io", "ru", "su", "ua", "uk", "de", "cn", "jp",
        "app", "dev", "ai", "me", "info", "biz", "tv", "cc", "co", "cloud", "xyz", "online", "site", "top"
    )

    /**
     * Parses multi-line input text into a list of RoutingRules.
     * Auto-detects:
     * - Suffixes: e.g. ".ru", ".com", ".рф" -> DOMAIN_SUFFIX
     * - Domains: e.g. "youtube.com", "sub.domain.org" -> DOMAIN_SUFFIX (or DOMAIN)
     * - IP / CIDRs: e.g. "1.1.1.1", "192.168.0.0/16" -> IP_CIDR
     * - Packages: e.g. "com.google.android.youtube" -> PACKAGE_NAME
     */
    fun parse(
        text: String,
        defaultAction: RuleAction = RuleAction.DIRECT,
        startPriority: Int = 0
    ): SmartParseResult {
        val rules = mutableListOf<RoutingRule>()
        val invalidLines = mutableListOf<String>()
        var currentPriority = startPriority

        val lines = text.lines()
        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) {
                continue
            }

            // Strip URL scheme, path, query, and port
            var cleaned = line
            if (cleaned.startsWith("https://", ignoreCase = true)) {
                cleaned = cleaned.substring(8)
            } else if (cleaned.startsWith("http://", ignoreCase = true)) {
                cleaned = cleaned.substring(7)
            }
            if (cleaned.contains("/")) {
                // If it's not a CIDR like 192.168.0.0/16, strip path
                val parts = cleaned.split("/", limit = 2)
                if (!parts[0].matches(Regex("""^(\d{1,3}\.){3}\d{1,3}$"""))) {
                    cleaned = parts[0]
                }
            }
            if (cleaned.contains("?")) {
                cleaned = cleaned.substringBefore("?")
            }
            if (cleaned.contains(":") && !cleaned.contains("::")) {
                // Strip port if not IPv6
                cleaned = cleaned.substringBefore(":")
            }
            cleaned = cleaned.trim().trimEnd('.')

            if (cleaned.isEmpty()) continue

            when {
                // Explicit package prefix: package:com.example.app
                cleaned.startsWith("package:", ignoreCase = true) -> {
                    val pkg = cleaned.substring(8).trim()
                    rules.add(
                        RoutingRule(
                            id = UUID.randomUUID().toString(),
                            type = RuleType.PACKAGE_NAME,
                            value = pkg,
                            action = defaultAction,
                            priority = currentPriority++,
                            isEnabled = true
                        )
                    )
                }
                // Suffix starting with dot: .ru, .org
                cleaned.startsWith(".") && cleaned.length > 1 -> {
                    val suffix = cleaned.removePrefix(".")
                    rules.add(
                        RoutingRule(
                            id = UUID.randomUUID().toString(),
                            type = RuleType.DOMAIN_SUFFIX,
                            value = suffix.lowercase(),
                            action = defaultAction,
                            priority = currentPriority++,
                            isEnabled = true
                        )
                    )
                }
                // IP or IP CIDR
                IPV4_CIDR_REGEX.matches(cleaned) -> {
                    rules.add(
                        RoutingRule(
                            id = UUID.randomUUID().toString(),
                            type = RuleType.IP_CIDR,
                            value = cleaned,
                            action = defaultAction,
                            priority = currentPriority++,
                            isEnabled = true
                        )
                    )
                }
                // Android Package Name (e.g. com.google.android.youtube: >= 3 segments and last segment is not a standard domain TLD)
                PACKAGE_REGEX.matches(cleaned) && cleaned.split(".").size >= 3 && !COMMON_TLDS.contains(cleaned.substringAfterLast(".").lowercase()) -> {
                    rules.add(
                        RoutingRule(
                            id = UUID.randomUUID().toString(),
                            type = RuleType.PACKAGE_NAME,
                            value = cleaned,
                            action = defaultAction,
                            priority = currentPriority++,
                            isEnabled = true
                        )
                    )
                }
                // Valid standard domain
                DOMAIN_REGEX.matches(cleaned) -> {
                    rules.add(
                        RoutingRule(
                            id = UUID.randomUUID().toString(),
                            type = RuleType.DOMAIN_SUFFIX,
                            value = cleaned.lowercase(),
                            action = defaultAction,
                            priority = currentPriority++,
                            isEnabled = true
                        )
                    )
                }
                // Fallback Android Package Name
                PACKAGE_REGEX.matches(cleaned) -> {
                    rules.add(
                        RoutingRule(
                            id = UUID.randomUUID().toString(),
                            type = RuleType.PACKAGE_NAME,
                            value = cleaned,
                            action = defaultAction,
                            priority = currentPriority++,
                            isEnabled = true
                        )
                    )
                }
                // TLD / Suffix without dot: "ru", "su", "рф"
                cleaned.matches(Regex("""^[a-zA-Z]{2,10}$""")) -> {
                    rules.add(
                        RoutingRule(
                            id = UUID.randomUUID().toString(),
                            type = RuleType.DOMAIN_SUFFIX,
                            value = cleaned.lowercase(),
                            action = defaultAction,
                            priority = currentPriority++,
                            isEnabled = true
                        )
                    )
                }
                else -> {
                    invalidLines.add(line)
                }
            }
        }

        return SmartParseResult(rules, invalidLines)
    }
}
