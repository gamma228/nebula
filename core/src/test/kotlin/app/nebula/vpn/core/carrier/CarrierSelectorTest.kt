package app.nebula.vpn.core.carrier

import app.nebula.vpn.core.model.ProtocolType
import app.nebula.vpn.core.model.ProxyProfile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class CarrierSelectorTest {

    private lateinit var selector: CarrierSelector
    private lateinit var candidates: List<ProxyProfile>

    @Before
    fun setup() {
        selector = CarrierSelector()
        candidates = listOf(
            ProxyProfile(id = "node-1", name = "Fast Node", protocol = ProtocolType.VLESS, server = "1.1.1.1", port = 443, authSecret = "s1"),
            ProxyProfile(id = "node-2", name = "Medium Node", protocol = ProtocolType.TROJAN, server = "2.2.2.2", port = 443, authSecret = "s2"),
            ProxyProfile(id = "node-3", name = "Slow Node", protocol = ProtocolType.SHADOWSOCKS, server = "3.3.3.3", port = 8388, authSecret = "s3")
        )
    }

    @Test
    fun testRankCandidatesWithPinnedNode() {
        selector.pinProfile("node-2")
        val ranked = selector.rankCandidates(candidates, NetworkType.WIFI)

        assertEquals("node-2", ranked.first().id)
        assertEquals("node-2", selector.getPinnedProfileId())
    }

    @Test
    fun testRankCandidatesWithNetworkAffinity() {
        selector.recordCarrierSuccess("node-3", NetworkType.MOBILE)
        selector.recordCarrierSuccess("node-1", NetworkType.WIFI)

        val rankedWifi = selector.rankCandidates(candidates, NetworkType.WIFI)
        assertEquals("node-1", rankedWifi.first().id)

        val rankedMobile = selector.rankCandidates(candidates, NetworkType.MOBILE)
        assertEquals("node-3", rankedMobile.first().id)
    }

    @Test
    fun testProbeAndSelectBestLatency() = runBlocking {
        val latencies = mapOf(
            "node-1" to 250L,
            "node-2" to 95L,  // Fastest!
            "node-3" to 400L
        )

        val best = selector.probeAndSelectBest(candidates, NetworkType.WIFI) { profile ->
            latencies[profile.id] ?: -1L
        }

        assertNotNull(best)
        assertEquals("node-2", best!!.id)
        assertEquals("node-2", selector.getLastWorkingCarrier(NetworkType.WIFI))
    }

    @Test
    fun testFallbackWhenPreferredNodeFails() = runBlocking {
        selector.pinProfile("node-1")

        // node-1 fails (throws or returns -1), node-2 succeeds with 120ms
        val best = selector.probeAndSelectBest(candidates, NetworkType.WIFI) { profile ->
            if (profile.id == "node-1") {
                throw RuntimeException("Connection timeout")
            } else if (profile.id == "node-2") {
                120L
            } else {
                300L
            }
        }

        assertNotNull(best)
        assertEquals("node-2", best!!.id)
    }

    @Test
    fun testExclusionAfterRepeatedFailures() {
        for (i in 1..5) {
            selector.recordCarrierFailure("node-1")
        }

        val ranked = selector.rankCandidates(candidates, NetworkType.WIFI)
        // node-1 has 5 failures, should be excluded from ranked candidates
        assertEquals(2, ranked.size)
        assertEquals(listOf("node-2", "node-3"), ranked.map { it.id })
    }
}
