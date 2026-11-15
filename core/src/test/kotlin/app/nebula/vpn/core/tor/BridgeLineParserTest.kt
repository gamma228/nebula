package app.nebula.vpn.core.tor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeLineParserTest {

    @Test
    fun `obfs4 bridge line is rejected with honest unsupported error`() {
        val line = "Bridge obfs4 192.0.2.1:443 1234567890ABCDEF1234567890ABCDEF12345678 cert=AbCdEf1234567890 iat-mode=0"
        val parsed = BridgeLineParser.parseLine(line)

        assertFalse("obfs4 must not be marked valid since PT is unsupported in-process", parsed.isValid)
        assertFalse("obfs4 must be marked unsupported", parsed.isSupported)
        assertEquals(BridgeTransport.OBFS4, parsed.transport)
        assertEquals("192.0.2.1", parsed.host)
        assertEquals(443, parsed.port)
        assertTrue("Error reason must state not supported yet", parsed.errorReason?.contains("not supported yet") == true)
    }

    @Test
    fun `webtunnel bridge line is rejected with honest unsupported error`() {
        val line = "webtunnel 198.51.100.2:443 FEDCBA0987654321FEDCBA0987654321FEDCBA09 url=https://tunnel.example.org/secret-path ver=0.0.1"
        val parsed = BridgeLineParser.parseLine(line)

        assertFalse("webtunnel must not be marked valid since PT is unsupported in-process", parsed.isValid)
        assertFalse("webtunnel must be marked unsupported", parsed.isSupported)
        assertEquals(BridgeTransport.WEBTUNNEL, parsed.transport)
        assertTrue("Error reason must state not supported yet", parsed.errorReason?.contains("not supported yet") == true)
    }

    @Test
    fun `vanilla bridge line is accepted as supported`() {
        val line = "192.0.2.1:443 1234567890ABCDEF1234567890ABCDEF12345678"
        val parsed = BridgeLineParser.parseLine(line)

        assertTrue("Vanilla bridge must be valid", parsed.isValid)
        assertTrue("Vanilla bridge must be supported", parsed.isSupported)
        assertEquals(BridgeTransport.VANILLA, parsed.transport)
        assertEquals("192.0.2.1", parsed.host)
        assertEquals(443, parsed.port)
        assertEquals("1234567890ABCDEF1234567890ABCDEF12345678", parsed.fingerprint)
    }

    @Test
    fun `vanilla IPv6 bridge is accepted as supported`() {
        val line = "[2001:db8::1234]:8443 AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        val parsed = BridgeLineParser.parseLine(line)

        assertTrue(parsed.isValid)
        assertTrue(parsed.isSupported)
        assertEquals(BridgeTransport.VANILLA, parsed.transport)
        assertEquals("2001:db8::1234", parsed.host)
        assertEquals(8443, parsed.port)
    }

    @Test
    fun `rejects invalid port or missing required params`() {
        // Port out of range
        val badPort = BridgeLineParser.parseLine("obfs4 1.2.3.4:70000 1234567890ABCDEF1234567890ABCDEF12345678 cert=xyz")
        assertFalse(badPort.isValid)
        assertTrue(badPort.errorReason?.contains("Port out of range") == true)

        // Missing cert on obfs4
        val missingCert = BridgeLineParser.parseLine("obfs4 1.2.3.4:443 1234567890ABCDEF1234567890ABCDEF12345678 iat-mode=0")
        assertFalse(missingCert.isValid)
        assertTrue(missingCert.errorReason?.contains("cert=") == true)

        // Insecure WebTunnel url
        val httpWebTunnel = BridgeLineParser.parseLine("webtunnel 1.2.3.4:443 1234567890ABCDEF1234567890ABCDEF12345678 url=http://insecure.org/path")
        assertFalse(httpWebTunnel.isValid)
        assertTrue(httpWebTunnel.errorReason?.contains("https://") == true)
    }

    @Test
    fun `masked string never leaks secret cert or full IP`() {
        val line = "obfs4 198.51.100.4:443 1234567890ABCDEF1234567890ABCDEF12345678 cert=SUPER_SECRET_CERT iat-mode=0"
        val parsed = BridgeLineParser.parseLine(line)
        val masked = parsed.masked()

        assertFalse("Masked output must not contain secret certificate", masked.contains("SUPER_SECRET_CERT"))
        assertFalse("Masked output must not contain full IP", masked.contains("198.51.100.4"))
        assertTrue("Masked output must show transport and port", masked.contains("obfs4") && masked.contains("443"))
    }

    @Test
    fun `bridge pool manager tracks rotation and health failure penalties`() {
        val pool = BridgePoolManager()
        val text = """
            # Comments should be ignored
            192.0.2.10:443 1111111111111111111111111111111111111111
            192.0.2.20:443 2222222222222222222222222222222222222222
            192.0.2.30:443 3333333333333333333333333333333333333333
        """.trimIndent()

        val imported = pool.importBridges(text)
        assertEquals(3, imported)

        val vanillaBridges = pool.getValidBridges(BridgeTransport.VANILLA)
        assertEquals(3, vanillaBridges.size)

        // Next bridge rotates
        val b1 = pool.getNextBridge(BridgeTransport.VANILLA)
        assertNotNull(b1)
        val b2 = pool.getNextBridge(BridgeTransport.VANILLA)
        assertNotNull(b2)

        // Mark b1 failing 3 times
        repeat(3) { pool.recordFailure(b1!!) }
        val health = pool.assessPoolHealth()
        assertEquals(3, health.totalValidBridges)

        // Next pick must favor b2 (healthy) over b1 (blocked)
        val nextPicked = pool.getNextBridge(BridgeTransport.VANILLA)
        assertEquals("Healthy bridge must be prioritized", b2!!.raw, nextPicked?.raw)
    }

    @Test
    fun `bridge pool manager rejects unsupported transports on import`() {
        val pool = BridgePoolManager()
        val text = """
            obfs4 192.0.2.10:443 1111111111111111111111111111111111111111 cert=cert1
            webtunnel 192.0.2.30:443 3333333333333333333333333333333333333333 url=https://example.com/wt
            snowflake 192.0.2.40:1 4444444444444444444444444444444444444444
        """.trimIndent()

        val imported = pool.importBridges(text)
        assertEquals("Unsupported PT transports must be rejected on import", 0, imported)
        assertEquals(0, pool.getAllBridges().size)
    }
}
