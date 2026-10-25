package app.nebula.vpn

import app.nebula.vpn.core.builder.SingBoxConfigBuilder
import app.nebula.vpn.core.model.AppRoutingMode
import app.nebula.vpn.core.model.ProtocolType
import app.nebula.vpn.core.model.ProxyProfile
import app.nebula.vpn.core.model.RoutingRule
import app.nebula.vpn.core.model.RuleAction
import app.nebula.vpn.core.model.RuleType
import app.nebula.vpn.core.model.TlsConfig
import app.nebula.vpn.core.model.TransportConfig
import app.nebula.vpn.core.model.TransportType
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SingBoxConfigBuilderTest {

    @Test
    fun testBuildConfigForShadowsocks() {
        val profile = ProxyProfile(
            id = "test-ss",
            name = "SS-Node",
            protocol = ProtocolType.SHADOWSOCKS,
            server = "1.2.3.4",
            port = 8388,
            authSecret = "secret123",
            encryption = "aes-128-gcm"
        )
        val config = SingBoxConfigBuilder.build(
            SingBoxConfigBuilder.BuildConfig(
                activeProfile = profile
            )
        )
        assertNotNull(config)
        assertTrue(config.contains("\"type\": \"shadowsocks\""))
        assertTrue(config.contains("\"method\": \"aes-128-gcm\""))
        assertTrue(config.contains("\"password\": \"secret123\""))
        assertTrue(config.contains("\"action\": \"hijack-dns\""))
        assertTrue(config.contains("\"action\": \"sniff\""))

        validateWithDesktopSingBox(config, "test-ss.json")
    }

    @Test
    fun testBuildConfigForVlessReality() {
        val profile = ProxyProfile(
            id = "test-vless",
            name = "VLESS-Node",
            protocol = ProtocolType.VLESS,
            server = "example.com",
            port = 443,
            authSecret = "00000000-0000-0000-0000-000000000000",
            tls = TlsConfig(
                enabled = true,
                serverName = "example.com",
                realityPublicKey = "GDlASyCCKyp6r1oFWNITgVAZFHUzTQfdjbk49JHP9m8",
                realityShortId = "1234"
            )
        )
        val config = SingBoxConfigBuilder.build(
            SingBoxConfigBuilder.BuildConfig(
                activeProfile = profile
            )
        )
        assertTrue(config.contains("\"type\": \"vless\""))
        assertTrue(config.contains("\"flow\": \"xtls-rprx-vision\""))
        assertTrue(config.contains("\"reality\""))

        validateWithDesktopSingBox(config, "test-vless.json")
    }

    @Test
    fun testBuildConfigWithSplitTunnelAndRules() {
        val profile = ProxyProfile(
            id = "test-hy2",
            name = "Hy2-Node",
            protocol = ProtocolType.HYSTERIA2,
            server = "hy2.example.com",
            port = 443,
            authSecret = "hy2password",
            tls = TlsConfig(enabled = true, serverName = "hy2.example.com")
        )
        val rules = listOf(
            RoutingRule(
                id = "r1",
                type = RuleType.DOMAIN_SUFFIX,
                value = "ru",
                action = RuleAction.DIRECT,
                priority = 0
            ),
            RoutingRule(
                id = "r2",
                type = RuleType.IP_CIDR,
                value = "10.0.0.0/8",
                action = RuleAction.DIRECT,
                priority = 1
            )
        )
        val config = SingBoxConfigBuilder.build(
            SingBoxConfigBuilder.BuildConfig(
                activeProfile = profile,
                routingMode = AppRoutingMode.BYPASS_APPS,
                targetPackages = setOf("com.android.chrome"),
                customRules = rules,
                killSwitchEnabled = true
            )
        )
        assertTrue(config.contains("com.android.chrome"))
        assertTrue(config.contains("\"domain_suffix\""))
        assertTrue(config.contains("\"strict_route\": true"))

        validateWithDesktopSingBox(config, "test-rules.json")
    }

    @Test
    fun testBuildConfigForVlessRealityWithSpiderXAndAlpn() {
        val profile = ProxyProfile(
            id = "test-reality",
            name = "VLESS-Reality",
            protocol = ProtocolType.VLESS,
            server = "1.2.3.4",
            port = 443,
            authSecret = "00000000-0000-0000-0000-000000000000",
            tls = TlsConfig(
                enabled = true,
                serverName = "example.com",
                realityPublicKey = "GDlASyCCKyp6r1oFWNITgVAZFHUzTQfdjbk49JHP9m8",
                realityShortId = "1234",
                spiderX = "/login",
                utlsFingerprint = "firefox",
                flow = "xtls-rprx-vision",
                alpn = listOf("h2", "http/1.1")
            )
        )
        val config = SingBoxConfigBuilder.build(
            SingBoxConfigBuilder.BuildConfig(
                activeProfile = profile,
                remoteDnsUrl = "https://1.1.1.1/dns-query"
            )
        )
        assertTrue(config.contains("\"public_key\": \"GDlASyCCKyp6r1oFWNITgVAZFHUzTQfdjbk49JHP9m8\""))
        assertTrue(config.contains("\"fingerprint\": \"firefox\""))
        assertTrue(config.contains("\"alpn\""))
        assertTrue(config.contains("\"path\": \"/dns-query\""))

        validateWithDesktopSingBox(config, "test-reality-full.json")
    }

    @Test
    fun testBuildConfigForWebSocketWithHostHeader() {
        val profile = ProxyProfile(
            id = "test-ws",
            name = "VLESS-WS",
            protocol = ProtocolType.VLESS,
            server = "104.16.1.1",
            port = 443,
            authSecret = "00000000-0000-0000-0000-000000000000",
            tls = TlsConfig(
                enabled = true,
                serverName = "worker.example.dev"
            ),
            transport = TransportConfig(
                type = TransportType.WS,
                path = "/chat?ed=2048",
                host = "worker.example.dev"
            )
        )
        val config = SingBoxConfigBuilder.build(
            SingBoxConfigBuilder.BuildConfig(
                activeProfile = profile
            )
        )
        assertTrue(config.contains("\"type\": \"ws\""))
        assertTrue(config.contains("\"path\": \"/chat?ed=2048\""))
        assertTrue(config.contains("\"Host\": \"worker.example.dev\""))

        validateWithDesktopSingBox(config, "test-ws.json")
    }

    @Test
    fun testBuildConfigForTrojanWebSocket() {
        val profile = ProxyProfile(
            id = "test-trojan-ws",
            name = "Trojan-WS",
            protocol = ProtocolType.TROJAN,
            server = "tr.example.com",
            port = 443,
            authSecret = "trojanpass",
            tls = TlsConfig(
                enabled = true,
                serverName = "tr.example.com",
                alpn = listOf("h2")
            ),
            transport = TransportConfig(
                type = TransportType.WS,
                path = "/trojan-ws",
                host = "tr.example.com"
            )
        )
        val config = SingBoxConfigBuilder.build(
            SingBoxConfigBuilder.BuildConfig(
                activeProfile = profile
            )
        )
        assertTrue(config.contains("\"type\": \"trojan\""))
        assertTrue(config.contains("\"type\": \"ws\""))
        assertTrue(config.contains("\"Host\": \"tr.example.com\""))

        validateWithDesktopSingBox(config, "test-trojan-ws.json")
    }

    private fun validateWithDesktopSingBox(configJson: String, tempFileName: String) {
        val singBoxBin = File("C:/Users/User/tools/sing-box.exe")
        if (singBoxBin.exists()) {
            val tempFile = File.createTempFile("sing-box-test-", tempFileName)
            try {
                tempFile.writeText(configJson)
                val process = ProcessBuilder(singBoxBin.absolutePath, "check", "-c", tempFile.absolutePath)
                    .redirectErrorStream(true)
                    .start()
                val output = process.inputStream.bufferedReader().readText()
                val exitCode = process.waitFor()
                assertTrue("sing-box check failed on $tempFileName:\n$output", exitCode == 0)
            } finally {
                tempFile.delete()
            }
        }
    }
}
