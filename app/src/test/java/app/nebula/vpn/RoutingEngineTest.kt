package app.nebula.vpn

import app.nebula.vpn.core.model.RoutingRule
import app.nebula.vpn.core.model.RuleAction
import app.nebula.vpn.core.model.RuleType
import app.nebula.vpn.core.parser.SmartRuleParser
import app.nebula.vpn.core.routing.RuleConflictDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingEngineTest {

    @Test
    fun testSmartRuleParserDetectsDomainsSuffixesIpsAndPackages() {
        val input = """
            # This is a comment
            https://www.youtube.com/watch?v=123
            .ru
            192.168.1.0/24
            1.1.1.1
            com.google.android.youtube
            instagram.com:443
            // Another comment
            invalid!!token??
        """.trimIndent()

        val result = SmartRuleParser.parse(input, defaultAction = RuleAction.PROXY)

        assertEquals(6, result.rules.size)
        assertEquals(1, result.invalidLines.size)
        assertEquals("invalid!!token??", result.invalidLines[0])

        // 1. youtube.com
        val youtubeRule = result.rules[0]
        assertEquals(RuleType.DOMAIN_SUFFIX, youtubeRule.type)
        assertEquals("www.youtube.com", youtubeRule.value)
        assertEquals(RuleAction.PROXY, youtubeRule.action)

        // 2. .ru suffix
        val ruRule = result.rules[1]
        assertEquals(RuleType.DOMAIN_SUFFIX, ruRule.type)
        assertEquals("ru", ruRule.value)

        // 3. 192.168.1.0/24
        val cidrRule = result.rules[2]
        assertEquals(RuleType.IP_CIDR, cidrRule.type)
        assertEquals("192.168.1.0/24", cidrRule.value)

        // 4. 1.1.1.1
        val ipRule = result.rules[3]
        assertEquals(RuleType.IP_CIDR, ipRule.type)
        assertEquals("1.1.1.1", ipRule.value)

        // 5. Package name
        val pkgRule = result.rules[4]
        assertEquals(RuleType.PACKAGE_NAME, pkgRule.type)
        assertEquals("com.google.android.youtube", pkgRule.value)

        // 6. instagram.com:443 stripped of port
        val instaRule = result.rules[5]
        assertEquals(RuleType.DOMAIN_SUFFIX, instaRule.type)
        assertEquals("instagram.com", instaRule.value)
    }

    @Test
    fun testRuleConflictDetectorIdentifiesShadowedRules() {
        val rules = listOf(
            RoutingRule("1", RuleType.DOMAIN_SUFFIX, "google.com", RuleAction.PROXY, priority = 0),
            RoutingRule("2", RuleType.DOMAIN, "mail.google.com", RuleAction.DIRECT, priority = 1), // Shadowed by 1
            RoutingRule("3", RuleType.DOMAIN_KEYWORD, "youtube", RuleAction.PROXY, priority = 2),
            RoutingRule("4", RuleType.DOMAIN_SUFFIX, "myoutube.com", RuleAction.BLOCK, priority = 3), // Shadowed by 3
            RoutingRule("5", RuleType.IP_CIDR, "10.0.0.0/8", RuleAction.DIRECT, priority = 4),
            RoutingRule("6", RuleType.IP_CIDR, "10.1.2.3/32", RuleAction.BLOCK, priority = 5), // Shadowed by 5
            RoutingRule("7", RuleType.DOMAIN_SUFFIX, "github.com", RuleAction.PROXY, priority = 6) // Independent
        )

        val shadowedIds = RuleConflictDetector.findShadowedRules(rules)

        assertTrue("mail.google.com should be shadowed", shadowedIds.contains("2"))
        assertTrue("myoutube.com should be shadowed", shadowedIds.contains("4"))
        assertTrue("10.1.2.3/32 should be shadowed", shadowedIds.contains("6"))
        assertFalse("github.com should NOT be shadowed", shadowedIds.contains("7"))
        assertFalse("google.com root should NOT be shadowed", shadowedIds.contains("1"))
    }
}
