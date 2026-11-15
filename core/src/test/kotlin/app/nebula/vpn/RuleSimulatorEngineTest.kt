package app.nebula.vpn

import app.nebula.vpn.core.model.RoutingRule
import app.nebula.vpn.core.model.RuleAction
import app.nebula.vpn.core.model.RuleType
import app.nebula.vpn.core.routing.RuleSimulatorEngine
import org.junit.Assert.*
import org.junit.Test

class RuleSimulatorEngineTest {

    @Test
    fun testDomainAndSuffixMatching() {
        val rules = listOf(
            RoutingRule(id = "1", type = RuleType.DOMAIN, value = "exact.com", action = RuleAction.DIRECT),
            RoutingRule(id = "2", type = RuleType.DOMAIN_SUFFIX, value = "github.com", action = RuleAction.PROXY),
            RoutingRule(id = "3", type = RuleType.DOMAIN_KEYWORD, value = "blocked", action = RuleAction.BLOCK)
        )

        // Exact domain match
        val res1 = RuleSimulatorEngine.simulate("exact.com", rules)
        assertEquals(RuleAction.DIRECT, res1.action)
        assertEquals(1, res1.ruleIndex)

        // Subdomain of exact should not match exact domain
        val res1Sub = RuleSimulatorEngine.simulate("sub.exact.com", rules)
        assertEquals(RuleAction.PROXY, res1Sub.action) // fallback

        // Suffix match on root domain and subdomains
        val res2Root = RuleSimulatorEngine.simulate("github.com", rules)
        assertEquals(RuleAction.PROXY, res2Root.action)
        assertEquals(2, res2Root.ruleIndex)

        val res2Sub = RuleSimulatorEngine.simulate("api.github.com", rules)
        assertEquals(RuleAction.PROXY, res2Sub.action)
        assertEquals(2, res2Sub.ruleIndex)

        // Keyword match
        val res3 = RuleSimulatorEngine.simulate("some-blocked-site.org", rules)
        assertEquals(RuleAction.BLOCK, res3.action)
        assertEquals(3, res3.ruleIndex)
    }

    @Test
    fun testProcessAndIpCidrMatching() {
        val rules = listOf(
            RoutingRule(id = "1", type = RuleType.PROCESS_NAME, value = "curl.exe", action = RuleAction.DIRECT),
            RoutingRule(id = "2", type = RuleType.IP_CIDR, value = "10.0.0.0/8", action = RuleAction.DIRECT),
            RoutingRule(id = "3", type = RuleType.DOMAIN_REGEX, value = """.*\.internal\.net""", action = RuleAction.DIRECT)
        )

        // Process name standalone
        val p1 = RuleSimulatorEngine.simulate("curl.exe", rules)
        assertEquals(RuleAction.DIRECT, p1.action)
        assertEquals(1, p1.ruleIndex)

        // Process name with Windows path
        val p2 = RuleSimulatorEngine.simulate("""C:\Windows\System32\curl.exe""", rules)
        assertEquals(RuleAction.DIRECT, p2.action)
        assertEquals(1, p2.ruleIndex)

        // IP CIDR
        val ip1 = RuleSimulatorEngine.simulate("10.50.1.1", rules)
        assertEquals(RuleAction.DIRECT, ip1.action)
        assertEquals(2, ip1.ruleIndex)

        // Regex
        val reg1 = RuleSimulatorEngine.simulate("srv-01.internal.net", rules)
        assertEquals(RuleAction.DIRECT, reg1.action)
        assertEquals(3, reg1.ruleIndex)
    }

    @Test
    fun testPriorityOrderingFirstRuleWins() {
        val rules = listOf(
            RoutingRule(id = "1", type = RuleType.DOMAIN_SUFFIX, value = "google.com", action = RuleAction.DIRECT),
            RoutingRule(id = "2", type = RuleType.DOMAIN_KEYWORD, value = "google", action = RuleAction.BLOCK)
        )

        val res = RuleSimulatorEngine.simulate("maps.google.com", rules)
        assertEquals(RuleAction.DIRECT, res.action)
        assertEquals(1, res.ruleIndex)
    }

    @Test
    fun testFallbackToDefaultProxyWhenNoMatch() {
        val rules = listOf(
            RoutingRule(id = "1", type = RuleType.DOMAIN, value = "onlythis.com", action = RuleAction.DIRECT)
        )

        val res = RuleSimulatorEngine.simulate("unrelated.org", rules)
        assertNull(res.matchedRule)
        assertNull(res.ruleIndex)
        assertEquals(RuleAction.PROXY, res.action)
        assertTrue(res.reason.contains("Falling back to default"))
    }
}
