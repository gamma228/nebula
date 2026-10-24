package app.nebula.vpn

import app.nebula.vpn.core.model.ProtocolType
import app.nebula.vpn.core.model.Result
import app.nebula.vpn.core.model.TransportType
import app.nebula.vpn.core.parser.LinkParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkParserTest {

    @Test
    fun testParseVlessReality() {
        val link = "vless://00000000-0000-0000-0000-000000000000@1.2.3.4:443?security=reality&sni=example.com&pbk=GDlASyCCKyp6r1oFWNITgVAZFHUzTQfdjbk49JHP9m8&sid=1234&type=tcp#VLESS-Node"
        val result = LinkParser.parse(link)
        assertTrue(result is Result.Success)
        val profile = (result as Result.Success).data
        assertEquals("VLESS-Node", profile.name)
        assertEquals(ProtocolType.VLESS, profile.protocol)
        assertEquals("1.2.3.4", profile.server)
        assertEquals(443, profile.port)
        assertEquals("00000000-0000-0000-0000-000000000000", profile.authSecret)
        assertTrue(profile.tls.enabled)
        assertEquals("example.com", profile.tls.serverName)
        assertEquals("GDlASyCCKyp6r1oFWNITgVAZFHUzTQfdjbk49JHP9m8", profile.tls.realityPublicKey)
        assertEquals("1234", profile.tls.realityShortId)
    }

    @Test
    fun testParseTrojan() {
        val link = "trojan://secretpass@trojan.example.com:443?sni=trojan.example.com#Trojan-Main"
        val result = LinkParser.parse(link)
        assertTrue(result is Result.Success)
        val profile = (result as Result.Success).data
        assertEquals("Trojan-Main", profile.name)
        assertEquals(ProtocolType.TROJAN, profile.protocol)
        assertEquals("trojan.example.com", profile.server)
        assertEquals(443, profile.port)
        assertEquals("secretpass", profile.authSecret)
        assertTrue(profile.tls.enabled)
    }

    @Test
    fun testParseShadowsocks() {
        // ss://YWVzLTEyOC1nY206cGFzc3dvcmQ=@1.2.3.4:8388#SS-Server (aes-128-gcm:password)
        val link = "ss://YWVzLTEyOC1nY206cGFzc3dvcmQ=@1.2.3.4:8388#SS-Server"
        val result = LinkParser.parse(link)
        assertTrue(result is Result.Success)
        val profile = (result as Result.Success).data
        assertEquals("SS-Server", profile.name)
        assertEquals(ProtocolType.SHADOWSOCKS, profile.protocol)
        assertEquals("1.2.3.4", profile.server)
        assertEquals(8388, profile.port)
        assertEquals("password", profile.authSecret)
        assertEquals("aes-128-gcm", profile.encryption)
    }

    @Test
    fun testParseHysteria2() {
        val link = "hy2://hypassword@hy2.example.com:8443?sni=hy2.example.com&insecure=1#Hy2-Server"
        val result = LinkParser.parse(link)
        assertTrue(result is Result.Success)
        val profile = (result as Result.Success).data
        assertEquals("Hy2-Server", profile.name)
        assertEquals(ProtocolType.HYSTERIA2, profile.protocol)
        assertEquals("hy2.example.com", profile.server)
        assertEquals(8443, profile.port)
        assertEquals("hypassword", profile.authSecret)
        assertTrue(profile.tls.enabled)
        assertTrue(profile.tls.insecure)
    }
}
