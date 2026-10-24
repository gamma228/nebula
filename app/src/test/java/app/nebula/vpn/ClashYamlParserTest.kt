package app.nebula.vpn

import app.nebula.vpn.core.model.GroupType
import app.nebula.vpn.core.model.ProtocolType
import app.nebula.vpn.core.model.Result
import app.nebula.vpn.core.model.RuleAction
import app.nebula.vpn.core.model.RuleType
import app.nebula.vpn.core.parser.ClashYamlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClashYamlParserTest {

    @Test
    fun testParseClashConfig() {
        val yaml = """
            proxies:
              - name: "SS-Node"
                type: ss
                server: 1.2.3.4
                port: 8388
                cipher: aes-128-gcm
                password: secretpassword
              - name: "Trojan-Node"
                type: trojan
                server: trojan.example.com
                port: 443
                password: trojanpassword
                sni: trojan.example.com

            proxy-groups:
              - name: "AUTO-SELECT"
                type: url-test
                proxies:
                  - "SS-Node"
                  - "Trojan-Node"
                url: "https://www.gstatic.com/generate_204"
                interval: 300

            rules:
              - DOMAIN-SUFFIX,google.com,AUTO-SELECT
              - DOMAIN-SUFFIX,ru,DIRECT
              - IP-CIDR,192.168.0.0/16,DIRECT
              - MATCH,AUTO-SELECT
        """.trimIndent()

        val result = ClashYamlParser.parse(yaml)
        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data

        assertEquals(2, data.profiles.size)
        assertEquals("SS-Node", data.profiles[0].name)
        assertEquals(ProtocolType.SHADOWSOCKS, data.profiles[0].protocol)
        assertEquals("Trojan-Node", data.profiles[1].name)
        assertEquals(ProtocolType.TROJAN, data.profiles[1].protocol)

        assertEquals(1, data.groups.size)
        assertEquals("AUTO-SELECT", data.groups[0].name)
        assertEquals(GroupType.URL_TEST, data.groups[0].type)
        assertEquals(2, data.groups[0].profileIds.size)

        assertEquals(3, data.rules.size)
        assertEquals(RuleType.DOMAIN_SUFFIX, data.rules[0].type)
        assertEquals("google.com", data.rules[0].value)
        assertEquals(RuleAction.PROXY, data.rules[0].action)

        assertEquals(RuleType.DOMAIN_SUFFIX, data.rules[1].type)
        assertEquals("ru", data.rules[1].value)
        assertEquals(RuleAction.DIRECT, data.rules[1].action)
    }
}
