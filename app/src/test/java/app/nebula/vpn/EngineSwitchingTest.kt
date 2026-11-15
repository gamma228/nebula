package app.nebula.vpn

import app.nebula.vpn.core.engine.CarrierConfig
import app.nebula.vpn.core.engine.EngineCapabilities
import app.nebula.vpn.core.engine.EngineStatus
import app.nebula.vpn.core.engine.EngineType
import app.nebula.vpn.core.engine.SingBoxEngine
import app.nebula.vpn.core.engine.TorEngine
import app.nebula.vpn.core.engine.TunnelEngineRegistry
import app.nebula.vpn.core.engine.TunnelStartRequest
import app.nebula.vpn.core.model.ConnectionState
import app.nebula.vpn.core.model.TrafficStats
import app.nebula.vpn.core.proxy.ProxyCore
import app.nebula.vpn.core.proxy.TunProvider
import app.nebula.vpn.data.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EngineSwitchingTest {

    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private val proxyCore: ProxyCore = mockk(relaxed = true)
    private val tunProvider: TunProvider = mockk(relaxed = true)
    private val coreStateFlow = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    private val coreStatsFlow = MutableStateFlow(TrafficStats())
    private val activeEngineFlow = MutableStateFlow(EngineType.SING_BOX)

    private lateinit var torEngine: TorEngine

    @Before
    fun setup() {
        every { proxyCore.state } returns coreStateFlow
        every { proxyCore.stats } returns coreStatsFlow
        every { settingsRepository.activeEngine } returns activeEngineFlow

        torEngine = mockk(relaxed = true) {
            every { id } returns EngineType.TOR.id
            every { displayName } returns "Tor (Arti + Onionmasq)"
            every { capabilities } returns EngineCapabilities.TOR_ANDROID
            every { status } returns MutableStateFlow(EngineStatus.Idle)
        }
    }

    private fun createFixtures(scope: kotlinx.coroutines.CoroutineScope): Pair<SingBoxEngine, TunnelEngineRegistry> {
        val engine = SingBoxEngine(proxyCore, scope).apply { this.tunProvider = this@EngineSwitchingTest.tunProvider }
        val reg = TunnelEngineRegistry(
            singBoxEngine = engine,
            torEngine = torEngine,
            settingsRepository = settingsRepository,
            registryScope = scope
        )
        return engine to reg
    }

    @Test
    fun `sing-box capabilities reflect full proxy feature set`() {
        val caps = EngineCapabilities.SING_BOX
        assertTrue("sing-box must support server list", caps.supportsServerList)
        assertTrue("sing-box must support subscriptions", caps.supportsSubscriptions)
        assertTrue("sing-box must support custom routing rules", caps.supportsCustomRoutingRules)
        assertTrue("sing-box must support per-app selection", caps.supportsPerAppSelection)
        assertTrue("sing-box must support UDP", caps.supportsUdp)
        assertTrue("sing-box must support local stats", caps.supportsLocalStats)
        assertTrue("sing-box must support LAN access", caps.supportsLanAccess)
        assertFalse("sing-box must NOT show bridges", caps.supportsBridges)
        assertFalse("sing-box must NOT show new identity", caps.supportsNewIdentity)
        assertFalse("sing-box must NOT show carrier mode", caps.supportsCarrier)
        assertTrue("sing-box must be supported on this platform", caps.supportedOnThisPlatform)
    }

    @Test
    fun `tor capabilities enforce onion-routing boundaries`() {
        val caps = EngineCapabilities.TOR_ANDROID
        assertFalse("Tor mode must hide individual server list", caps.supportsServerList)
        assertFalse("Tor mode must hide subscriptions as direct servers", caps.supportsSubscriptions)
        assertFalse("Tor mode must hide custom domain routing to avoid leaks", caps.supportsCustomRoutingRules)
        assertTrue("Tor mode must allow per-app circuit isolation", caps.supportsPerAppSelection)
        assertFalse("Tor mode must declare UDP unsupported to enforce fail-closed policy", caps.supportsUdp)
        assertTrue("Tor mode must support local stats", caps.supportsLocalStats)
        assertTrue("Tor mode must support bridges (obfs4, Snowflake, WebTunnel)", caps.supportsBridges)
        assertTrue("Tor mode must support new identity circuit refreshing", caps.supportsNewIdentity)
        assertTrue("Tor mode must support LAN access toggle", caps.supportsLanAccess)
        assertTrue("Tor mode must support Carrier circumvention mode", caps.supportsCarrier)
        assertTrue("Tor mode must be supported on Android platform", caps.supportedOnThisPlatform)
    }

    @Test
    fun `desktop tor capability flag is unsupported`() {
        val caps = EngineCapabilities.TOR_UNSUPPORTED
        assertFalse("Desktop must flag Tor as unsupported", caps.supportedOnThisPlatform)
    }

    @Test
    fun `registry adapts active engine and capabilities dynamically`() = runTest(UnconfinedTestDispatcher()) {
        val (engine, reg) = createFixtures(backgroundScope)
        assertEquals(EngineType.SING_BOX, reg.activeEngineType.value)
        assertEquals(engine, reg.activeEngine.value)
        assertEquals(EngineCapabilities.SING_BOX, reg.activeCapabilities.value)

        // Switch to TOR
        activeEngineFlow.value = EngineType.TOR

        assertEquals(EngineType.TOR, reg.activeEngineType.value)
        assertEquals(torEngine, reg.activeEngine.value)
        assertEquals(EngineCapabilities.TOR_ANDROID, reg.activeCapabilities.value)

        // Switch back to SING_BOX
        activeEngineFlow.value = EngineType.SING_BOX

        assertEquals(EngineType.SING_BOX, reg.activeEngineType.value)
        assertEquals(engine, reg.activeEngine.value)
        assertEquals(EngineCapabilities.SING_BOX, reg.activeCapabilities.value)
    }

    @Test
    fun `sing-box engine maps core states to engine status accurately`() = runTest(UnconfinedTestDispatcher()) {
        val (engine, _) = createFixtures(backgroundScope)
        assertEquals(EngineStatus.Idle, engine.status.value)

        coreStateFlow.value = ConnectionState.Connecting
        assertEquals(EngineStatus.Preparing, engine.status.value)

        coreStateFlow.value = ConnectionState.Connected(profileName = "US West VLESS", connectedAt = 1000L)
        assertTrue(engine.status.value is EngineStatus.Connected)
        assertEquals("US West VLESS", (engine.status.value as EngineStatus.Connected).summary)

        coreStateFlow.value = ConnectionState.Connected(profileName = "US West VLESS", isDegraded = true, warning = "No probe")
        assertTrue(engine.status.value is EngineStatus.Connected)
        assertTrue((engine.status.value as EngineStatus.Connected).summary?.contains("Degraded") == true)

        coreStateFlow.value = ConnectionState.Error("Network unreachable")
        assertTrue(engine.status.value is EngineStatus.Failed)
        assertEquals("Network unreachable", (engine.status.value as EngineStatus.Failed).reason)

        coreStateFlow.value = ConnectionState.Disconnected
        assertEquals(EngineStatus.Idle, engine.status.value)
    }

    @Test
    fun `rapid engine switching stress test maintains state integrity`() = runTest(UnconfinedTestDispatcher()) {
        val (engine, reg) = createFixtures(backgroundScope)
        for (i in 0 until 50) {
            val target = if (i % 2 == 0) EngineType.TOR else EngineType.SING_BOX
            activeEngineFlow.value = target
            assertEquals(target, reg.activeEngineType.value)
            if (target == EngineType.TOR) {
                assertEquals(torEngine, reg.activeEngine.value)
                assertEquals(EngineCapabilities.TOR_ANDROID, reg.activeCapabilities.value)
            } else {
                assertEquals(engine, reg.activeEngine.value)
                assertEquals(EngineCapabilities.SING_BOX, reg.activeCapabilities.value)
            }
        }
    }

    @Test
    fun `carrier configuration integrates seamlessly with tunnel start request`() {
        val carrier = CarrierConfig(
            enabled = true,
            carrierProfileId = "vless-reality-node",
            socksPort = 10808,
            socksHost = "127.0.0.1",
            authUsername = "nebula",
            authPassword = "password"
        )
        val request = TunnelStartRequest(
            tunFd = 42,
            bridgeLines = "obfs4 192.0.2.1:443 ...",
            carrierConfig = carrier
        )

        assertTrue(request.carrierConfig?.enabled == true)
        assertEquals("vless-reality-node", request.carrierConfig?.carrierProfileId)
        assertEquals(10808, request.carrierConfig?.socksPort)
        assertEquals(42, request.tunFd)
    }
}
