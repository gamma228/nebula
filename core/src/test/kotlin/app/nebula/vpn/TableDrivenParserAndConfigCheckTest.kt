package app.nebula.vpn

import app.nebula.vpn.core.builder.SingBoxConfigBuilder
import app.nebula.vpn.core.model.ProtocolType
import app.nebula.vpn.core.model.Result
import app.nebula.vpn.core.model.TransportType
import app.nebula.vpn.core.parser.LinkParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Base64

class TableDrivenParserAndConfigCheckTest {

    private val json = Json { ignoreUnknownKeys = true }

    data class FixtureTestCase(
        val testName: String,
        val link: String,
        val expectedProtocol: ProtocolType,
        val expectedServer: String,
        val expectedPort: Int,
        val expectedTransport: TransportType = TransportType.TCP,
        val expectedTlsEnabled: Boolean = false,
        val expectedSni: String = "",
        val expectedAlpn: List<String> = emptyList(),
        val expectedFp: String = "chrome",
        val expectedRealityPublicKey: String = "",
        val expectedRealityShortId: String = "",
        val expectedRealitySpiderX: String = "",
        val expectedServiceName: String = "",
        val expectedFlow: String = "",
        val expectedPath: String = "",
        val expectedHost: String = "",
        val expectedMode: String = "gun",
        val expectedAuthority: String = "",
        val expectedPacketEncoding: String = "xudp",
        val expectedHeaderType: String = "",
        val expectedInsecure: Boolean = false,
        val expectedOutboundType: String
    )

    private val testCases = listOf(
        // 1. VLESS Reality (pbk, sid, spx, fp, flow=xtls-rprx-vision, sni, type=tcp)
        FixtureTestCase(
            testName = "VLESS Reality TCP with Vision",
            link = "vless://11111111-2222-3333-4444-555555555555@95.217.1.1:443?security=reality&sni=yahoo.com&pbk=GDlASyCCKyp6r1oFWNITgVAZFHUzTQfdjbk49JHP9m8&sid=1a2b3c4d&spx=%2Fsearch&fp=chrome&flow=xtls-rprx-vision&type=tcp#Reality-TCP",
            expectedProtocol = ProtocolType.VLESS,
            expectedServer = "95.217.1.1",
            expectedPort = 443,
            expectedTransport = TransportType.TCP,
            expectedTlsEnabled = true,
            expectedSni = "yahoo.com",
            expectedAlpn = emptyList(),
            expectedFp = "chrome",
            expectedRealityPublicKey = "GDlASyCCKyp6r1oFWNITgVAZFHUzTQfdjbk49JHP9m8",
            expectedRealityShortId = "1a2b3c4d",
            expectedRealitySpiderX = "/search",
            expectedFlow = "xtls-rprx-vision",
            expectedOutboundType = "vless"
        ),

        // 2. VLESS TLS gRPC (serviceName, mode=gun, alpn=h2)
        FixtureTestCase(
            testName = "VLESS TLS gRPC with serviceName",
            link = "vless://dab7f290-02ba-0009-b514-2f743deae7eb@87.242.127.115:443?encryption=none&type=grpc&serviceName=x5.lk.v1.AccountService&mode=gun&security=tls&sni=lk.x5.ru&fp=qq&alpn=h2#VLESS-gRPC",
            expectedProtocol = ProtocolType.VLESS,
            expectedServer = "87.242.127.115",
            expectedPort = 443,
            expectedTransport = TransportType.GRPC,
            expectedTlsEnabled = true,
            expectedSni = "lk.x5.ru",
            expectedAlpn = listOf("h2"),
            expectedFp = "qq",
            expectedServiceName = "x5.lk.v1.AccountService",
            expectedMode = "gun",
            expectedOutboundType = "vless"
        ),

        // 3. VLESS WS with path and host
        FixtureTestCase(
            testName = "VLESS WebSocket with path and host",
            link = "vless://22222222-3333-4444-5555-666666666666@104.16.1.1:443?security=tls&sni=cloudflare.cdn.com&type=ws&host=cloudflare.cdn.com&path=%2Fws-proxy%3Fed%3D2048&alpn=http%2F1.1#VLESS-WS",
            expectedProtocol = ProtocolType.VLESS,
            expectedServer = "104.16.1.1",
            expectedPort = 443,
            expectedTransport = TransportType.WS,
            expectedTlsEnabled = true,
            expectedSni = "cloudflare.cdn.com",
            expectedAlpn = listOf("http/1.1"),
            expectedFp = "chrome",
            expectedPath = "/ws-proxy?ed=2048",
            expectedHost = "cloudflare.cdn.com",
            expectedOutboundType = "vless"
        ),

        // 4. VLESS httpupgrade & xhttp
        FixtureTestCase(
            testName = "VLESS HTTPUpgrade / XHTTP transport",
            link = "vless://33333333-4444-5555-6666-777777777777@198.51.100.1:443?security=tls&sni=fast.cdn.net&type=httpupgrade&host=fast.cdn.net&path=%2Fhttpupgrade-path#VLESS-HTTPUpgrade",
            expectedProtocol = ProtocolType.VLESS,
            expectedServer = "198.51.100.1",
            expectedPort = 443,
            expectedTransport = TransportType.HTTP_UPGRADE,
            expectedTlsEnabled = true,
            expectedSni = "fast.cdn.net",
            expectedAlpn = listOf("http/1.1"),
            expectedFp = "chrome",
            expectedPath = "/httpupgrade-path",
            expectedHost = "fast.cdn.net",
            expectedOutboundType = "vless"
        ),

        // 5. VLESS security=none
        FixtureTestCase(
            testName = "VLESS Plaintext security=none",
            link = "vless://44444444-5555-6666-7777-888888888888@192.0.2.1:80?security=none&type=tcp#VLESS-Plain",
            expectedProtocol = ProtocolType.VLESS,
            expectedServer = "192.0.2.1",
            expectedPort = 80,
            expectedTransport = TransportType.TCP,
            expectedTlsEnabled = false,
            expectedSni = "192.0.2.1",
            expectedAlpn = emptyList(),
            expectedFp = "chrome",
            expectedOutboundType = "vless"
        ),

        // 6. VLESS URL-encoded values
        FixtureTestCase(
            testName = "VLESS URL-encoded parameters",
            link = "vless://55555555-6666-7777-8888-999999999999@198.51.100.25:443?security=reality&sni=sample.org&pbk=GDlASyCCKyp6r1oFWNITgVAZFHUzTQfdjbk49JHP9m8&sid=1a2b3c4d&spx=%2Fapi%2Fv1%3Fquery%3D1&fp=chrome&type=tcp#VLESS%20Encoded%20Node",
            expectedProtocol = ProtocolType.VLESS,
            expectedServer = "198.51.100.25",
            expectedPort = 443,
            expectedTransport = TransportType.TCP,
            expectedTlsEnabled = true,
            expectedSni = "sample.org",
            expectedAlpn = emptyList(),
            expectedFp = "chrome",
            expectedRealityPublicKey = "GDlASyCCKyp6r1oFWNITgVAZFHUzTQfdjbk49JHP9m8",
            expectedRealityShortId = "1a2b3c4d",
            expectedRealitySpiderX = "/api/v1?query=1",
            expectedFlow = "xtls-rprx-vision",
            expectedOutboundType = "vless"
        ),

        // 7. VLESS IPv6 host
        FixtureTestCase(
            testName = "VLESS IPv6 host",
            link = "vless://66666666-7777-8888-9999-000000000000@[2001:db8::1]:443?security=tls&sni=ipv6.node.org&type=tcp#VLESS-IPv6",
            expectedProtocol = ProtocolType.VLESS,
            expectedServer = "2001:db8::1",
            expectedPort = 443,
            expectedTransport = TransportType.TCP,
            expectedTlsEnabled = true,
            expectedSni = "ipv6.node.org",
            expectedAlpn = emptyList(),
            expectedFp = "chrome",
            expectedOutboundType = "vless"
        ),

        // 8. VLESS missing optional params (pure default fallback)
        FixtureTestCase(
            testName = "VLESS missing optional params",
            link = "vless://77777777-8888-9999-aaaa-bbbbbbbbbbbb@192.0.2.100:443#Bare-VLESS",
            expectedProtocol = ProtocolType.VLESS,
            expectedServer = "192.0.2.100",
            expectedPort = 443,
            expectedTransport = TransportType.TCP,
            expectedTlsEnabled = false,
            expectedSni = "192.0.2.100",
            expectedAlpn = emptyList(),
            expectedFp = "chrome",
            expectedOutboundType = "vless"
        ),

        // 9. VLESS names with emoji or empty names
        FixtureTestCase(
            testName = "VLESS with emoji and empty name fallbacks",
            link = "vless://88888888-9999-aaaa-bbbb-cccccccccccc@198.51.100.50:443?security=tls&sni=fast.org#%F0%9F%87%B8%F0%9F%87%AA%20%F0%9F%8E%AE%20%E2%9A%A1%20%E2%AD%90%200.1X%20-%20LTE%20%E2%84%9689",
            expectedProtocol = ProtocolType.VLESS,
            expectedServer = "198.51.100.50",
            expectedPort = 443,
            expectedTransport = TransportType.TCP,
            expectedTlsEnabled = true,
            expectedSni = "fast.org",
            expectedAlpn = emptyList(),
            expectedFp = "chrome",
            expectedOutboundType = "vless"
        ),

        // 10. VMess base64 JSON
        FixtureTestCase(
            testName = "VMess base64 JSON format",
            link = "vmess://" + Base64.getEncoder().encodeToString(
                """{"add":"198.51.100.70","port":443,"id":"99999999-aaaa-bbbb-cccc-dddddddddddd","aid":0,"net":"ws","path":"/vmess-ws","host":"vmess.cdn.com","tls":"tls","ps":"VMess-Node"}""".toByteArray()
            ),
            expectedProtocol = ProtocolType.VMESS,
            expectedServer = "198.51.100.70",
            expectedPort = 443,
            expectedTransport = TransportType.WS,
            expectedTlsEnabled = true,
            expectedSni = "vmess.cdn.com",
            expectedAlpn = listOf("http/1.1"),
            expectedFp = "chrome",
            expectedPath = "/vmess-ws",
            expectedHost = "vmess.cdn.com",
            expectedOutboundType = "vmess"
        ),

        // 11. Trojan TLS WebSocket
        FixtureTestCase(
            testName = "Trojan TLS with WebSocket",
            link = "trojan://password123@trojan.cdn.org:443?security=tls&sni=trojan.cdn.org&type=ws&host=trojan.cdn.org&path=%2Ftrojan-path#Trojan-WS",
            expectedProtocol = ProtocolType.TROJAN,
            expectedServer = "trojan.cdn.org",
            expectedPort = 443,
            expectedTransport = TransportType.WS,
            expectedTlsEnabled = true,
            expectedSni = "trojan.cdn.org",
            expectedAlpn = listOf("http/1.1"),
            expectedFp = "chrome",
            expectedPath = "/trojan-path",
            expectedHost = "trojan.cdn.org",
            expectedOutboundType = "trojan"
        ),

        // 12. Shadowsocks standard format
        FixtureTestCase(
            testName = "Shadowsocks AEAD format",
            link = "ss://" + Base64.getEncoder().encodeToString("aes-128-gcm:pass123".toByteArray()) + "@198.51.100.80:8388#SS-AEAD",
            expectedProtocol = ProtocolType.SHADOWSOCKS,
            expectedServer = "198.51.100.80",
            expectedPort = 8388,
            expectedTransport = TransportType.TCP,
            expectedTlsEnabled = false,
            expectedSni = "",
            expectedAlpn = emptyList(),
            expectedFp = "chrome",
            expectedOutboundType = "shadowsocks"
        ),

        // 13. Hysteria 2 format
        FixtureTestCase(
            testName = "Hysteria 2 QUIC format",
            link = "hy2://hypass123@hy2.server.net:8443?sni=hy2.server.net&insecure=1&alpn=h3#Hy2-Node",
            expectedProtocol = ProtocolType.HYSTERIA2,
            expectedServer = "hy2.server.net",
            expectedPort = 8443,
            expectedTransport = TransportType.TCP,
            expectedTlsEnabled = true,
            expectedSni = "hy2.server.net",
            expectedAlpn = listOf("h3"),
            expectedFp = "chrome",
            expectedInsecure = true,
            expectedOutboundType = "hysteria2"
        ),

        // 14. TUIC v5 format
        FixtureTestCase(
            testName = "TUIC protocol format",
            link = "tuic://11111111-2222-3333-4444-555555555555:tuicpass@tuic.server.net:8443?sni=tuic.server.net&alpn=h3#TUIC-Node",
            expectedProtocol = ProtocolType.TUIC,
            expectedServer = "tuic.server.net",
            expectedPort = 8443,
            expectedTransport = TransportType.TCP,
            expectedTlsEnabled = true,
            expectedSni = "tuic.server.net",
            expectedAlpn = listOf("h3"),
            expectedFp = "chrome",
            expectedOutboundType = "tuic"
        )
    )

    @Test
    fun testTableDrivenShareLinkParsing() {
        for (tc in testCases) {
            val parseResult = LinkParser.parse(tc.link)
            assertTrue("Parsing failed for test case '${tc.testName}': ${(parseResult as? Result.Error)?.message}", parseResult is Result.Success)
            val profile = (parseResult as Result.Success).data

            assertEquals("${tc.testName}: Protocol mismatch", tc.expectedProtocol, profile.protocol)
            assertEquals("${tc.testName}: Server mismatch", tc.expectedServer, profile.server)
            assertEquals("${tc.testName}: Port mismatch", tc.expectedPort, profile.port)
            assertEquals("${tc.testName}: Transport mismatch", tc.expectedTransport, profile.transport.type)
            assertEquals("${tc.testName}: TLS Enabled mismatch", tc.expectedTlsEnabled, profile.tls.enabled)
            assertEquals("${tc.testName}: SNI mismatch", tc.expectedSni, profile.tls.serverName)
            assertEquals("${tc.testName}: ALPN mismatch", tc.expectedAlpn, profile.tls.alpn)
            if (tc.expectedRealityPublicKey.isNotEmpty()) {
                assertEquals("${tc.testName}: Reality Public Key mismatch", tc.expectedRealityPublicKey, profile.tls.realityPublicKey)
                assertEquals("${tc.testName}: Reality Short ID mismatch", tc.expectedRealityShortId, profile.tls.realityShortId)
            }
            if (tc.expectedServiceName.isNotEmpty()) {
                assertEquals("${tc.testName}: ServiceName mismatch", tc.expectedServiceName, profile.transport.serviceName)
            }
            if (tc.expectedFlow.isNotEmpty()) {
                assertEquals("${tc.testName}: Flow mismatch", tc.expectedFlow, profile.tls.flow)
            }
            if (tc.expectedPath.isNotEmpty()) {
                assertEquals("${tc.testName}: Path mismatch", tc.expectedPath, profile.transport.path)
            }
            if (tc.expectedHost.isNotEmpty()) {
                assertEquals("${tc.testName}: Host mismatch", tc.expectedHost, profile.transport.host)
            }
            if (tc.expectedInsecure) {
                assertTrue("${tc.testName}: Insecure flag must be true", profile.tls.insecure)
            }
        }
    }

    @Test
    fun testOutboundJsonExactFieldsAndSingBoxCheck() {
        val singBoxBin = File("C:/Users/User/tools/sing-box.exe")
        for (tc in testCases) {
            val parsed = (LinkParser.parse(tc.link) as Result.Success).data
            val configJson = SingBoxConfigBuilder.build(
                SingBoxConfigBuilder.BuildConfig(
                    activeProfile = parsed,
                    localPortMixedInbound = 2080
                )
            )

            assertNotNull(configJson)
            val rootObj = json.parseToJsonElement(configJson).jsonObject
            val outbounds = rootObj["outbounds"]?.jsonArray
            assertNotNull(outbounds)
            val proxyOutbound = outbounds!!.firstOrNull { it.jsonObject["tag"]?.jsonPrimitive?.content == "proxy" }?.jsonObject
            assertNotNull("${tc.testName}: Outbound 'proxy' must be present in config", proxyOutbound)

            assertEquals("${tc.testName}: Outbound type mismatch", tc.expectedOutboundType, proxyOutbound!!["type"]?.jsonPrimitive?.content)
            assertEquals("${tc.testName}: Outbound server mismatch", tc.expectedServer, proxyOutbound["server"]?.jsonPrimitive?.content)
            assertEquals("${tc.testName}: Outbound port mismatch", tc.expectedPort, proxyOutbound["server_port"]?.jsonPrimitive?.content?.toInt())

            if (tc.expectedTlsEnabled) {
                val tlsObj = proxyOutbound["tls"]?.jsonObject
                assertNotNull("${tc.testName}: TLS object must be present", tlsObj)
                assertTrue("${tc.testName}: TLS enabled must be true", tlsObj!!["enabled"]?.jsonPrimitive?.content == "true")

                if (tc.expectedRealityPublicKey.isNotEmpty()) {
                    val realityObj = tlsObj["reality"]?.jsonObject
                    assertNotNull("${tc.testName}: Reality object must be present", realityObj)
                    assertEquals("${tc.testName}: Reality public_key mismatch", tc.expectedRealityPublicKey, realityObj!!["public_key"]?.jsonPrimitive?.content)
                    if (tc.expectedRealityShortId.isNotEmpty()) {
                        assertEquals("${tc.testName}: Reality short_id mismatch", tc.expectedRealityShortId, realityObj["short_id"]?.jsonPrimitive?.content)
                    }
                }

                if (tc.expectedAlpn.isNotEmpty()) {
                    val alpnArray = tlsObj["alpn"]?.jsonArray?.map { it.jsonPrimitive.content }
                    assertEquals("${tc.testName}: ALPN array mismatch", tc.expectedAlpn, alpnArray)
                }
            }

            if (tc.expectedTransport == TransportType.GRPC) {
                val transportObj = proxyOutbound["transport"]?.jsonObject
                assertNotNull("${tc.testName}: Transport object must be present for gRPC", transportObj)
                assertEquals("${tc.testName}: Transport type must be grpc", "grpc", transportObj!!["type"]?.jsonPrimitive?.content)
                if (tc.expectedServiceName.isNotEmpty()) {
                    assertEquals("${tc.testName}: Transport service_name mismatch", tc.expectedServiceName, transportObj["service_name"]?.jsonPrimitive?.content)
                }
            }

            if (singBoxBin.exists()) {
                val tempFile = File.createTempFile("sing-box-fixture-", ".json")
                try {
                    tempFile.writeText(configJson)
                    val process = ProcessBuilder(singBoxBin.absolutePath, "check", "-c", tempFile.absolutePath)
                        .redirectErrorStream(true)
                        .start()
                    val output = process.inputStream.bufferedReader().readText()
                    val exitCode = process.waitFor()
                    assertTrue("sing-box check failed on ${tc.testName}:\n$output", exitCode == 0)
                } finally {
                    tempFile.delete()
                }
            }
        }
    }
}
