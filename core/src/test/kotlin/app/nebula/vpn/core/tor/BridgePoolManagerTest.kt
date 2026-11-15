package app.nebula.vpn.core.tor

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class BridgePoolManagerTest {

    private lateinit var poolManager: BridgePoolManager

    @Before
    fun setup() {
        poolManager = BridgePoolManager()
    }

    @Test
    fun testImportAndRotation() {
        val sampleBridges = """
            192.0.2.1:443 1234567890ABCDEF1234567890ABCDEF12345678
            [2001:db8::1]:443 1234567890ABCDEF1234567890ABCDEF12345678
            192.0.2.2:443 1234567890ABCDEF1234567890ABCDEF12345678
            invalid garbage line
            obfs4 192.0.2.3:443 1234567890ABCDEF1234567890ABCDEF12345678 cert=xyz
        """.trimIndent()

        val added = poolManager.importBridges(sampleBridges)
        assertEquals("Only supported valid vanilla bridges should be added", 3, added)
        assertEquals(3, poolManager.getAllBridges().size)

        // Duplicate import should not duplicate
        val reAdded = poolManager.importBridges(sampleBridges)
        assertEquals(0, reAdded)
        assertEquals(3, poolManager.getAllBridges().size)

        // Filter by transport
        val vanillaBridges = poolManager.getValidBridges(BridgeTransport.VANILLA)
        assertEquals(3, vanillaBridges.size)
        assertEquals(BridgeTransport.VANILLA, vanillaBridges[0].transport)

        val obfs4Bridges = poolManager.getValidBridges(BridgeTransport.OBFS4)
        assertEquals(0, obfs4Bridges.size)

        // Rotation
        val firstPick = poolManager.getNextBridge()
        assertNotNull(firstPick)
        val secondPick = poolManager.getNextBridge()
        assertNotNull(secondPick)
    }

    @Test
    fun testHealthDeprioritizationOnConsecutiveFailures() {
        val line1 = "192.0.2.1:443 1234567890ABCDEF1234567890ABCDEF12345678"
        val line2 = "192.0.2.2:443 1234567890ABCDEF1234567890ABCDEF12345679"

        poolManager.importBridges("$line1\n$line2")

        val b1 = poolManager.getValidBridges().first { it.host.contains("192.0.2.1") }
        val b2 = poolManager.getValidBridges().first { it.host.contains("192.0.2.2") }

        // Record 3 failures on b1 -> marks it as blocked
        poolManager.recordFailure(b1)
        poolManager.recordFailure(b1)
        poolManager.recordFailure(b1)

        val healthList = poolManager.getAllBridges()
        val b1Health = healthList.first { it.bridge.raw.contains("192.0.2.1") }
        val b2Health = healthList.first { it.bridge.raw.contains("192.0.2.2") }

        assertTrue(b1Health.isBlocked)
        assertEquals(3, b1Health.failureCount)
        assertFalse(b2Health.isBlocked)

        // Deprioritization: b2 should be chosen over b1
        val picked = poolManager.getNextBridge(BridgeTransport.VANILLA)
        assertNotNull(picked)
        assertEquals(b2.raw.trim(), picked!!.raw.trim())

        // Success on b1 clears failure count
        poolManager.recordSuccess(b1)
        assertFalse(b1Health.isBlocked)
        assertEquals(0, b1Health.failureCount)
    }

    @Test
    fun testAssessPoolHealthWarnings() {
        // Empty pool
        val emptyHealth = poolManager.assessPoolHealth()
        assertEquals(0, emptyHealth.totalValidBridges)
        assertTrue(emptyHealth.warnings.any { it.contains("No custom bridges") })

        // Small pool (< 3 bridges)
        val line = "192.0.2.1:443 1234567890ABCDEF1234567890ABCDEF12345678"
        poolManager.importBridges(line)

        val smallHealth = poolManager.assessPoolHealth()
        assertEquals(1, smallHealth.totalValidBridges)
        assertTrue(smallHealth.isSmall)
        assertTrue(smallHealth.warnings.any { it.contains("small") })
    }
}
