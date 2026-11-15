package app.nebula.vpn

import app.nebula.vpn.core.diagnostics.StabilityDiagnosticsCollector
import app.nebula.vpn.core.diagnostics.StabilityDiagnosticsCollector.DeviceSnapshot
import app.nebula.vpn.core.diagnostics.StabilityDiagnosticsCollector.ProcessExitRecord
import app.nebula.vpn.core.diagnostics.StabilityDiagnosticsCollector.StabilityState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StabilityDiagnosticsTest {

    @Test
    fun testExitReasonMappingCompleteness() {
        assertEquals("REASON_EXIT_SELF", StabilityDiagnosticsCollector.mapReasonCode(1))
        assertEquals("REASON_SIGNALED", StabilityDiagnosticsCollector.mapReasonCode(2))
        assertEquals("REASON_LOW_MEMORY", StabilityDiagnosticsCollector.mapReasonCode(3))
        assertEquals("REASON_CRASH", StabilityDiagnosticsCollector.mapReasonCode(4))
        assertEquals("REASON_CRASH_NATIVE", StabilityDiagnosticsCollector.mapReasonCode(5))
        assertEquals("REASON_ANR", StabilityDiagnosticsCollector.mapReasonCode(6))
        assertEquals("REASON_INITIALIZATION_FAILURE", StabilityDiagnosticsCollector.mapReasonCode(7))
        assertEquals("REASON_PERMISSION_CHANGE", StabilityDiagnosticsCollector.mapReasonCode(8))
        assertEquals("REASON_EXCESSIVE_RESOURCE_USAGE", StabilityDiagnosticsCollector.mapReasonCode(9))
        assertEquals("REASON_USER_REQUESTED", StabilityDiagnosticsCollector.mapReasonCode(10))
        assertEquals("REASON_USER_STOPPED", StabilityDiagnosticsCollector.mapReasonCode(11))
        assertEquals("REASON_DEPENDENCY_DIED", StabilityDiagnosticsCollector.mapReasonCode(12))
        assertEquals("REASON_OTHER", StabilityDiagnosticsCollector.mapReasonCode(13))
        assertEquals("REASON_FREEZER", StabilityDiagnosticsCollector.mapReasonCode(14))
        assertEquals("REASON_PACKAGE_STATE_CHANGE", StabilityDiagnosticsCollector.mapReasonCode(15))
        assertEquals("REASON_PACKAGE_UPDATED", StabilityDiagnosticsCollector.mapReasonCode(16))
    }

    @Test
    fun testSubReasonMapping() {
        assertEquals("SUBREASON_EXCESSIVE_CPU", StabilityDiagnosticsCollector.mapSubReason(9, 1))
        assertEquals("SUBREASON_EXCESSIVE_CPU", StabilityDiagnosticsCollector.mapSubReason(9, 7))
        assertEquals("SUBREASON_EXCESSIVE_MEMORY", StabilityDiagnosticsCollector.mapSubReason(9, 4))
        assertEquals("SUBREASON_FORCE_STOP", StabilityDiagnosticsCollector.mapSubReason(10, 21))
        assertEquals("SUBREASON_REMOVE_TASK", StabilityDiagnosticsCollector.mapSubReason(10, 22))
    }

    @Test
    fun testStrictSanitizationNoLeaks() {
        val rawInput = """
            Connecting to https://my-private-server.org:8443/subscribe?token=secret_abc123
            Resolved target to 198.51.100.45:443
            User UUID: 550e8400-e29b-41d4-a716-446655440000
            Carrier bridge 127.0.0.1:9050 connected, TUN DNS at 172.19.0.2
            Outbound parameters: password=superSecretPassword&pbk=AbCdEf1234567890
            Bridge node fingerprint: 1234567890abcdef1234567890abcdef12345678
        """.trimIndent()

        val sanitized = StabilityDiagnosticsCollector.sanitizeText(rawInput)

        // Verifications
        assertFalse("Raw URL must not be present", sanitized.contains("https://my-private-server.org"))
        assertTrue("Redacted URL marker must be present", sanitized.contains("[URL_REDACTED]"))

        assertFalse("Public server IP must be masked", sanitized.contains("198.51.100.45"))
        assertTrue("x.x.x.x mask must be present", sanitized.contains("x.x.x.x:443"))

        assertTrue("127.0.0.1 must be preserved for loopback diagnostics", sanitized.contains("127.0.0.1:9050"))
        assertTrue("172.19.0.2 must be preserved for FakeIP DNS diagnostics", sanitized.contains("172.19.0.2"))

        assertFalse("UUID must be redacted", sanitized.contains("550e8400-e29b-41d4-a716-446655440000"))
        assertTrue("UUID marker must be present", sanitized.contains("[UUID_REDACTED]"))

        assertFalse("Password must be redacted", sanitized.contains("superSecretPassword"))
        assertTrue("password= must be redacted", sanitized.contains("password=[REDACTED]"))

        assertFalse("Public key pbk must be redacted", sanitized.contains("AbCdEf1234567890"))
        assertTrue("pbk= must be redacted", sanitized.contains("pbk=[REDACTED]"))

        assertFalse("Tor fingerprint must be redacted", sanitized.contains("1234567890abcdef1234567890abcdef12345678"))
        assertTrue("Fingerprint marker must be present", sanitized.contains("[FINGERPRINT]"))
    }

    @Test
    fun testStabilitySummaryDecisionMatrix() {
        val dummyDevice = DeviceSnapshot(
            manufacturer = "Google",
            model = "Pixel 8",
            brand = "google",
            androidVersion = "14",
            sdkInt = 34,
            primaryAbi = "arm64-v8a",
            totalRamMb = 8192,
            availRamMb = 4096,
            isLowRam = false,
            isIgnoringBatteryOptimizations = true,
            standbyBucket = "ACTIVE (10)",
            isBackgroundRestricted = false,
            detectedOemSkin = "Google Pixel"
        )

        // Case 1: Healthy device with no recent exits
        val summaryHealthy = StabilityDiagnosticsCollector.getStabilitySummary(
            device = dummyDevice,
            exits = emptyList(),
            watchdogStalls = emptyList()
        )
        assertEquals(StabilityState.HEALTHY, summaryHealthy.state)

        // Case 2: System recorded an ANR (reason 6) within 48h
        val anrRecord = ProcessExitRecord(
            timestamp = System.currentTimeMillis() - 10000L,
            formattedTimestamp = "2026-11-10 07:00:00",
            pid = 1234,
            reasonCode = 6,
            reasonName = "REASON_ANR",
            subReasonCode = 0,
            subReasonName = "NONE",
            status = 0,
            pssKb = 150000,
            rssKb = 220000,
            description = "Input dispatching timed out",
            anrTrace = "main thread at android.os.Looper.loop()"
        )
        val summaryAnr = StabilityDiagnosticsCollector.getStabilitySummary(
            device = dummyDevice,
            exits = listOf(anrRecord),
            watchdogStalls = emptyList()
        )
        assertEquals(StabilityState.RECENT_ANR, summaryAnr.state)

        // Case 3: System killed process due to excessive resource usage (reason 9)
        val resourceRecord = ProcessExitRecord(
            timestamp = System.currentTimeMillis() - 20000L,
            formattedTimestamp = "2026-11-10 06:50:00",
            pid = 2345,
            reasonCode = 9,
            reasonName = "REASON_EXCESSIVE_RESOURCE_USAGE",
            subReasonCode = 7,
            subReasonName = "SUBREASON_EXCESSIVE_CPU",
            status = 0,
            pssKb = 200000,
            rssKb = 280000,
            description = "Excessive CPU usage in background",
            anrTrace = null
        )
        val summaryResource = StabilityDiagnosticsCollector.getStabilitySummary(
            device = dummyDevice,
            exits = listOf(resourceRecord),
            watchdogStalls = emptyList()
        )
        assertEquals(StabilityState.RESOURCE_KILL, summaryResource.state)

        // Case 4: Background restricted on Xiaomi OEM
        val xiaomiDevice = dummyDevice.copy(
            manufacturer = "Xiaomi",
            detectedOemSkin = "Xiaomi HyperOS / MIUI",
            isIgnoringBatteryOptimizations = false
        )
        val summaryRestricted = StabilityDiagnosticsCollector.getStabilitySummary(
            device = xiaomiDevice,
            exits = emptyList(),
            watchdogStalls = emptyList()
        )
        assertEquals(StabilityState.BACKGROUND_RESTRICTED, summaryRestricted.state)
    }
}
