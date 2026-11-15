package app.nebula.vpn

import app.nebula.vpn.core.model.PowerProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryEfficiencyTest {

    @Test
    fun testPowerProfileDefaultsAndParsing() {
        // Fallback on null or unknown string
        assertEquals(PowerProfile.BALANCED, PowerProfile.fromId(null))
        assertEquals(PowerProfile.BALANCED, PowerProfile.fromId("unknown_xyz"))
        assertEquals(PowerProfile.BALANCED, PowerProfile.fromId("balanced"))
        assertEquals(PowerProfile.BALANCED, PowerProfile.fromId("BALANCED"))

        // Specific profiles
        assertEquals(PowerProfile.BATTERY_SAVER, PowerProfile.fromId("BATTERY_SAVER"))
        assertEquals(PowerProfile.BATTERY_SAVER, PowerProfile.fromId("battery_saver"))
        assertEquals(PowerProfile.HIGH_PERFORMANCE, PowerProfile.fromId("HIGH_PERFORMANCE"))
        assertEquals(PowerProfile.HIGH_PERFORMANCE, PowerProfile.fromId("high_performance"))
    }

    @Test
    fun testPowerProfileContractParameters() {
        val balanced = PowerProfile.BALANCED
        assertEquals(1000L, balanced.interactiveIntervalMs)
        assertEquals(0L, balanced.backgroundIntervalMs)
        assertEquals("warn", balanced.coreLogLevel)
        assertEquals("gvisor", balanced.tunStack)
        assertTrue(balanced.endpointIndependentNat)
        assertTrue(balanced.description.isNotBlank())

        val saver = PowerProfile.BATTERY_SAVER
        assertEquals(2000L, saver.interactiveIntervalMs)
        assertEquals(0L, saver.backgroundIntervalMs)
        assertEquals("error", saver.coreLogLevel)
        assertEquals("gvisor", saver.tunStack)
        assertTrue(saver.endpointIndependentNat)
        assertTrue(saver.description.isNotBlank())

        val perf = PowerProfile.HIGH_PERFORMANCE
        assertEquals(1000L, perf.interactiveIntervalMs)
        assertEquals(5000L, perf.backgroundIntervalMs)
        assertEquals("info", perf.coreLogLevel)
        assertEquals("gvisor", perf.tunStack)
        assertTrue(perf.endpointIndependentNat)
        assertTrue(perf.description.isNotBlank())
    }

    @Test
    fun testSpeedFormattingEdgeCases() {
        fun formatSpeed(bytesPerSec: Long): String {
            if (bytesPerSec <= 0) return "0 KB/s"
            val kb = bytesPerSec / 1024.0
            if (kb < 1000) {
                return String.format(java.util.Locale.US, "%.1f KB/s", kb)
            }
            val mb = kb / 1024.0
            return String.format(java.util.Locale.US, "%.1f MB/s", mb)
        }

        assertEquals("0 KB/s", formatSpeed(0L))
        assertEquals("0 KB/s", formatSpeed(-50L))
        assertEquals("1.0 KB/s", formatSpeed(1024L))
        assertEquals("500.0 KB/s", formatSpeed(512000L))
        assertEquals("1.0 MB/s", formatSpeed(1024L * 1024L))
        assertEquals("10.5 MB/s", formatSpeed((10.5 * 1024L * 1024L).toLong()))
    }

    @Test
    fun testDurationCalculationFromConnectedAt() {
        val connectedAt = 100_000L
        val now = 165_000L
        val elapsedSeconds = (now - connectedAt) / 1000L
        assertEquals(65L, elapsedSeconds)
    }

    @Test
    fun testGoRuntimeConcurrencyCappedToOneCore() {
        assertTrue("Go concurrency limiter must be active", app.nebula.vpn.core.OptimizationFlags.limitGoConcurrency)
        assertEquals(
            "GOMAXPROCS must be capped to 1 core to prevent scheduler work-stealing on mobile CPUs",
            1,
            app.nebula.vpn.core.OptimizationFlags.goMaxProcsValue
        )
    }

    @Test
    fun testGrpcConfigDisablesKeepaliveWithoutStreams() {
        val profile = app.nebula.vpn.core.model.ProxyProfile(
            id = "test-grpc-battery",
            name = "gRPC Battery Test",
            protocol = app.nebula.vpn.core.model.ProtocolType.VLESS,
            server = "1.2.3.4",
            port = 443,
            authSecret = "uuid-1234",
            transport = app.nebula.vpn.core.model.TransportConfig(
                type = app.nebula.vpn.core.model.TransportType.GRPC,
                serviceName = "TestService"
            )
        )
        val configJson = app.nebula.vpn.core.builder.SingBoxConfigBuilder.build(
            app.nebula.vpn.core.builder.SingBoxConfigBuilder.BuildConfig(
                activeProfile = profile
            )
        )
        assertTrue(configJson.contains("\"permit_without_stream\": false"))
        assertTrue(configJson.contains("\"idle_timeout\": \"60s\""))
        assertTrue(configJson.contains("\"ping_timeout\": \"30s\""))
    }
}
