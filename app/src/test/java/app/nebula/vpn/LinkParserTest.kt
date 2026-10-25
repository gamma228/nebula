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
    fun testParseVlessRealityWithSpiderXAndAlpn() {
        val link = "vless://00000000-0000-0000-0000-000000000000@1.2.3.4:443?security=reality&sni=example.com&pbk=GDlASyCCKyp6r1oFWNITgVAZFHUzTQfdjbk49JHP9m8&sid=1234&spx=%2Flogin&fp=firefox&flow=xtls-rprx-vision&alpn=h2%2Chttp%2F1.1#VLESS-Reality"
        val result = LinkParser.parse(link)
        assertTrue(result is Result.Success)
        val profile = (result as Result.Success).data
        assertEquals("VLESS-Reality", profile.name)
        assertEquals(ProtocolType.VLESS, profile.protocol)
        assertTrue(profile.tls.enabled)
        assertEquals("example.com", profile.tls.serverName)
        assertEquals("GDlASyCCKyp6r1oFWNITgVAZFHUzTQfdjbk49JHP9m8", profile.tls.realityPublicKey)
        assertEquals("1234", profile.tls.realityShortId)
        assertEquals("/login", profile.tls.spiderX)
        assertEquals("firefox", profile.tls.utlsFingerprint)
        assertEquals("xtls-rprx-vision", profile.tls.flow)
        assertEquals(listOf("h2", "http/1.1"), profile.tls.alpn)
    }

    @Test
    fun testParseVlessWebSocketWithHostAndPath() {
        val link = "vless://00000000-0000-0000-0000-000000000000@104.16.1.1:443?security=tls&sni=worker.example.dev&type=ws&host=worker.example.dev&path=%2Fchat%3Fed%3D2048&alpn=http%2F1.1#VLESS-WS"
        val result = LinkParser.parse(link)
        assertTrue(result is Result.Success)
        val profile = (result as Result.Success).data
        assertEquals(TransportType.WS, profile.transport.type)
        assertEquals("worker.example.dev", profile.transport.host)
        assertEquals("/chat?ed=2048", profile.transport.path)
        assertEquals(listOf("http/1.1"), profile.tls.alpn)
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
    fun testParseTrojanWebSocket() {
        val link = "trojan://trojanpass@tr.example.com:443?security=tls&sni=tr.example.com&type=ws&host=tr.example.com&path=%2Ftrojan-ws&alpn=h2#Trojan-WS"
        val result = LinkParser.parse(link)
        assertTrue(result is Result.Success)
        val profile = (result as Result.Success).data
        assertEquals(ProtocolType.TROJAN, profile.protocol)
        assertEquals(TransportType.WS, profile.transport.type)
        assertEquals("tr.example.com", profile.transport.host)
        assertEquals("/trojan-ws", profile.transport.path)
        assertEquals(listOf("h2"), profile.tls.alpn)
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
        val link = "hy2://hypassword@hy2.example.com:8443?sni=hy2.example.com&insecure=1&alpn=h3#Hy2-Server"
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
        assertEquals(listOf("h3"), profile.tls.alpn)
    }
}
