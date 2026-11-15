package app.nebula.vpn.core.tor

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class NetworkPolicyTest {

    @Before
    fun setup() {
        NetworkPolicy.setTorActive(false)
        NetworkPolicy.clearCarrierEndpoint()
    }

    @After
    fun tearDown() {
        NetworkPolicy.setTorActive(false)
        NetworkPolicy.clearCarrierEndpoint()
    }

    @Test
    fun testSingBoxModePermitsClearnetRequests() {
        NetworkPolicy.setTorActive(false)

        // Should not throw in sing-box mode
        NetworkPolicy.checkAllowed("example.com", 443)
        NetworkPolicy.checkAllowed("1.1.1.1", 53)
        NetworkPolicy.checkAllowed("api.github.com", 443)
    }

    @Test
    fun testTorModeBlocksExternalClearnetEndpoints() {
        NetworkPolicy.setTorActive(true)

        val forbiddenTargets = listOf(
            "example.com" to 443,
            "1.1.1.1" to 53,
            "8.8.8.8" to 443,
            "api.github.com" to 443,
            "sub.provider.ru" to 443
        )

        for ((host, port) in forbiddenTargets) {
            try {
                NetworkPolicy.checkAllowed(host, port)
                fail("NetworkPolicy must block clearnet target $host:$port in Tor mode")
            } catch (e: TorPolicyBlockedException) {
                // Expected exception
            }
        }
    }

    @Test
    fun testTorModeAllowsLoopback() {
        NetworkPolicy.setTorActive(true)

        // All loopback variations must pass for local SOCKS/DNS IPC
        NetworkPolicy.checkAllowed("127.0.0.1", 10808)
        NetworkPolicy.checkAllowed("127.0.0.1", 9050)
        NetworkPolicy.checkAllowed("localhost", 8080)
        NetworkPolicy.checkAllowed("::1", 53)
    }

    @Test
    fun testTorModeAllowsConfiguredCarrierOnly() {
        NetworkPolicy.setTorActive(true)
        NetworkPolicy.setCarrierEndpoint("vless.carrier.com", 443)

        // Exact carrier host and port must pass
        NetworkPolicy.checkAllowed("vless.carrier.com", 443)

        // Different port on carrier host must be blocked
        try {
            NetworkPolicy.checkAllowed("vless.carrier.com", 8080)
            fail("NetworkPolicy must block carrier host on unconfigured port")
        } catch (_: TorPolicyBlockedException) {}

        // Other domain on port 443 must be blocked
        try {
            NetworkPolicy.checkAllowed("attacker.com", 443)
            fail("NetworkPolicy must block non-carrier host")
        } catch (_: TorPolicyBlockedException) {}
    }
}
