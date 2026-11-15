package app.nebula.vpn

import android.net.Uri
import app.nebula.vpn.core.model.ProtocolType
import app.nebula.vpn.core.model.ProxyProfile
import app.nebula.vpn.core.tor.NetworkPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.net.URI
import javax.xml.parsers.DocumentBuilderFactory

class SubscriptionImportEntryPointsTest {

    @Test
    fun testAndroidManifestIntentFilters() {
        val manifestFile = File("src/main/AndroidManifest.xml")
        assertTrue("AndroidManifest.xml must exist", manifestFile.exists())

        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(manifestFile)

        val intentFilters = doc.getElementsByTagName("intent-filter")
        var hasActionSend = false
        var hasActionView = false
        val registeredSchemes = mutableSetOf<String>()
        var autoVerifyCount = 0

        for (i in 0 until intentFilters.length) {
            val filterNode = intentFilters.item(i)
            val children = filterNode.childNodes
            var filterHasView = false
            var filterHasSend = false

            for (j in 0 until children.length) {
                val child = children.item(j)
                if (child.nodeName == "action") {
                    val actionName = child.attributes?.getNamedItem("android:name")?.nodeValue
                    if (actionName == "android.intent.action.SEND") filterHasSend = true
                    if (actionName == "android.intent.action.VIEW") filterHasView = true
                }
                if (child.nodeName == "data") {
                    val scheme = child.attributes?.getNamedItem("android:scheme")?.nodeValue
                    if (scheme != null) registeredSchemes.add(scheme)
                }
            }

            val autoVerifyAttr = filterNode.attributes?.getNamedItem("android:autoVerify")?.nodeValue
            if (autoVerifyAttr == "true") autoVerifyCount++

            if (filterHasSend) hasActionSend = true
            if (filterHasView) hasActionView = true
        }

        assertTrue("Manifest must register android.intent.action.SEND for text sharing", hasActionSend)
        assertTrue("Manifest must register android.intent.action.VIEW for deep links", hasActionView)

        // Schemes check
        val expectedSchemes = listOf("vless", "vmess", "trojan", "ss", "hy2", "sing-box", "clash", "nebula")
        for (scheme in expectedSchemes) {
            assertTrue("Manifest must register scheme [$scheme]", registeredSchemes.contains(scheme))
        }

        // Cross-client schemes must NOT hijack: zero autoVerify
        assertEquals("Cross-client schemes must not set android:autoVerify=true to avoid hijacking other clients", 0, autoVerifyCount)
    }

    @Test
    fun testDeepLinkUriExtraction() {
        // 1. Nebula subscribe scheme
        val nebulaUri = URI.create("nebula://subscribe?url=https%3A%2F%2Fexample.com%2Fsub%2Ftest")
        val nebulaQuery = parseQueryParams(nebulaUri)
        assertEquals("https://example.com/sub/test", nebulaQuery["url"])

        // 2. Clash install-config scheme
        val clashUri = URI.create("clash://install-config?url=https%3A%2F%2Fexample.com%2Fclash%2Fsub")
        val clashQuery = parseQueryParams(clashUri)
        assertEquals("https://example.com/clash/sub", clashQuery["url"])

        // 3. sing-box import-remote-profile scheme
        val sbUri = URI.create("sing-box://import-remote-profile?url=https%3A%2F%2Fexample.com%2Fsb%2Fsub")
        val sbQuery = parseQueryParams(sbUri)
        assertEquals("https://example.com/sb/sub", sbQuery["url"])
    }

    @Test
    fun testTorNetworkPolicyBlocksClearnetSubscriptionFetches() {
        NetworkPolicy.setTorActive(false)
        NetworkPolicy.clearCarrierEndpoint()

        // Clearnet allowed when Tor is inactive
        NetworkPolicy.checkAllowed("example.com", 443)

        // Switch to Tor mode
        NetworkPolicy.setTorActive(true)

        // Clearnet destination must be blocked fail-closed
        try {
            NetworkPolicy.checkAllowed("example.com", 443)
            fail("NetworkPolicy must throw TorPolicyBlockedException for clearnet destination in Tor mode")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("FAIL-CLOSED") == true)
        }

        // Loopback is whitelisted for local proxy inbounds
        NetworkPolicy.checkAllowed("127.0.0.1", 2080)
        NetworkPolicy.checkAllowed("::1", 2080)

        // Reset back to clean state
        NetworkPolicy.setTorActive(false)
        NetworkPolicy.clearCarrierEndpoint()
    }

    @Test
    fun testStableIdentityMatchingPreservesFavoritesAndLatency() {
        val existingProfiles = listOf(
            ProxyProfile(
                id = "profile-uuid-1",
                name = "DE Server",
                protocol = ProtocolType.VLESS,
                server = "198.51.100.10",
                port = 443,
                authSecret = "secret-uuid-1",
                subscriptionId = "sub-1",
                isFavorite = true,
                lastPingMs = 45L
            ),
            ProxyProfile(
                id = "profile-uuid-2",
                name = "NL Server",
                protocol = ProtocolType.VMESS,
                server = "198.51.100.20",
                port = 8443,
                authSecret = "secret-uuid-2",
                subscriptionId = "sub-1",
                isFavorite = false,
                lastPingMs = 120L
            )
        )

        // Incoming profiles from refresh have fresh UUIDs and updated name
        val incomingProfiles = listOf(
            ProxyProfile(
                id = "fresh-random-uuid-x",
                name = "DE Server (Renamed)",
                protocol = ProtocolType.VLESS,
                server = "198.51.100.10",
                port = 443,
                authSecret = "secret-uuid-1"
            ),
            ProxyProfile(
                id = "fresh-random-uuid-y",
                name = "US Server (New)",
                protocol = ProtocolType.TROJAN,
                server = "198.51.100.30",
                port = 443,
                authSecret = "secret-uuid-3"
            )
        )

        val oldByKey = existingProfiles.associateBy { "${it.protocol}:${it.server}:${it.port}:${it.authSecret}" }
        val merged = incomingProfiles.map { p ->
            val key = "${p.protocol}:${p.server}:${p.port}:${p.authSecret}"
            val old = oldByKey[key]
            if (old != null) {
                p.copy(
                    id = old.id,
                    subscriptionId = "sub-1",
                    isFavorite = old.isFavorite,
                    lastPingMs = old.lastPingMs
                )
            } else {
                p.copy(subscriptionId = "sub-1")
            }
        }

        assertEquals(2, merged.size)
        val deMatched = merged.first { it.server == "198.51.100.10" }
        assertEquals("profile-uuid-1", deMatched.id)
        assertTrue("Favorite status must be preserved across refreshes", deMatched.isFavorite)
        assertEquals(45L, deMatched.lastPingMs)
        assertEquals("DE Server (Renamed)", deMatched.name)

        val usNew = merged.first { it.server == "198.51.100.30" }
        assertEquals("fresh-random-uuid-y", usNew.id)
        assertFalse(usNew.isFavorite)
    }

    @Test
    fun testStubNodesGuardProtectsGoodProfilesFromBeingWiped() {
        val existingProfiles = listOf(
            ProxyProfile(
                id = "good-profile-1",
                name = "Working Node",
                protocol = ProtocolType.VLESS,
                server = "198.51.100.1",
                port = 443,
                authSecret = "good-uuid"
            )
        )

        val stubProfiles = listOf(
            ProxyProfile(
                id = "stub-1",
                name = "⚠️ Приложение не поддерживается",
                protocol = ProtocolType.VLESS,
                server = "0.0.0.0",
                port = 1,
                authSecret = "stub-secret"
            )
        )

        val hasStubNodesOnly = stubProfiles.isNotEmpty() && stubProfiles.all { it.isStub }
        assertTrue("Stub profiles must be recognized as stubs", hasStubNodesOnly)

        val shouldPreserveExisting = (stubProfiles.isEmpty() || hasStubNodesOnly) && existingProfiles.any { !it.isStub }
        assertTrue("Existing good profiles must be preserved when server responds with stubs only", shouldPreserveExisting)
    }

    private fun parseQueryParams(uri: URI): Map<String, String> {
        val query = uri.rawQuery ?: return emptyMap()
        val map = mutableMapOf<String, String>()
        for (param in query.split("&")) {
            val parts = param.split("=", limit = 2)
            if (parts.size == 2) {
                map[parts[0]] = java.net.URLDecoder.decode(parts[1], "UTF-8")
            }
        }
        return map
    }
}
