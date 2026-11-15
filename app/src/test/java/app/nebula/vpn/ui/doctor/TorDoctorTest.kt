package app.nebula.vpn.ui.doctor

import android.content.Context
import app.nebula.vpn.core.engine.EngineCapabilities
import app.nebula.vpn.core.engine.EngineStatus
import app.nebula.vpn.core.engine.EngineType
import app.nebula.vpn.core.engine.SingBoxEngine
import app.nebula.vpn.core.engine.TorEngine
import app.nebula.vpn.core.engine.TunnelEngineRegistry
import app.nebula.vpn.core.model.ConnectionState
import app.nebula.vpn.core.proxy.ProxyCore
import app.nebula.vpn.data.repository.ProfileRepository
import app.nebula.vpn.data.repository.SettingsRepository
import app.nebula.vpn.data.repository.VpnRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TorDoctorTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val context = mockk<Context>(relaxed = true)
    private val profileRepository = mockk<ProfileRepository>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val vpnRepository = mockk<VpnRepository>(relaxed = true)
    private val proxyCore = mockk<ProxyCore>(relaxed = true)
    private val singBoxEngine = mockk<SingBoxEngine>(relaxed = true)
    private val torEngine = mockk<TorEngine>(relaxed = true)

    private val connectionStateFlow = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    private val activeEngineTypeFlow = MutableStateFlow(EngineType.TOR)
    private val torStatusFlow = MutableStateFlow<EngineStatus>(EngineStatus.Idle)
    private val torStatsFlow = MutableStateFlow(app.nebula.vpn.core.model.TrafficStats())
    private val torTransportFlow = MutableStateFlow("webtunnel")
    private val torCustomBridgesFlow = MutableStateFlow(
        "webtunnel 192.0.2.1:443 1234567890abcdef1234567890abcdef12345678 url=https://example.com/secret\n" +
        "webtunnel 192.0.2.2:443 1234567890abcdef1234567890abcdef12345678 url=https://example.com/secret2\n" +
        "webtunnel 192.0.2.3:443 1234567890abcdef1234567890abcdef12345678 url=https://example.com/secret3"
    )
    private val torCarrierEnabledFlow = MutableStateFlow(true)
    private val torCarrierProfileIdFlow = MutableStateFlow("carrier-profile-1")

    private lateinit var engineRegistry: TunnelEngineRegistry
    private lateinit var viewModel: DoctorViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { vpnRepository.connectionState } returns connectionStateFlow
        every { settingsRepository.activeEngine } returns activeEngineTypeFlow
        every { settingsRepository.torBridgeType } returns torTransportFlow
        every { settingsRepository.torCustomBridges } returns torCustomBridgesFlow
        every { settingsRepository.torCarrierEnabled } returns torCarrierEnabledFlow
        every { settingsRepository.torCarrierProfileId } returns torCarrierProfileIdFlow
        every { settingsRepository.selectedProfileId } returns MutableStateFlow(null)
        every { settingsRepository.ipv6Enabled } returns MutableStateFlow(false)
        every { settingsRepository.blockQuic } returns MutableStateFlow(true)

        every { torEngine.status } returns torStatusFlow
        every { torEngine.stats } returns torStatsFlow
        every { torEngine.capabilities } returns EngineCapabilities.TOR_ANDROID
        every { torEngine.getRecentLogs(any()) } returns listOf(
            "[TorEngine] Initializing Onionmasq tunnel on fd 124...",
            "[TorEngine] Connecting to bridge at 192.0.2.1:443 with fingerprint 1234567890abcdef1234567890abcdef12345678 cert=xyz123 iat-mode=1",
            "[TorEngine] Multi-hop circuits ready."
        )

        engineRegistry = mockk(relaxed = true)
        every { engineRegistry.activeEngineType } returns activeEngineTypeFlow
        every { engineRegistry.getEngine(EngineType.TOR) } returns torEngine
        every { engineRegistry.getEngine(EngineType.SING_BOX) } returns singBoxEngine

        viewModel = DoctorViewModel(
            context = context,
            profileRepository = profileRepository,
            settingsRepository = settingsRepository,
            vpnRepository = vpnRepository,
            proxyCore = proxyCore,
            engineRegistry = engineRegistry
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testTorDoctorDisconnectedPreChecks() = runTest(testDispatcher) {
        viewModel.runDiagnostics(testDispatcher)
        advanceUntilIdle()

        val checks = viewModel.checks.value
        assertTrue("Checks should not be empty", checks.isNotEmpty())

        val titles = checks.map { it.title }
        assertTrue("Must include VPN Permission check", titles.any { it.contains("VPN") })
        assertTrue("Must include Always-on / Lockdown check", titles.any { it.contains("Lockdown") })
        assertTrue("Must include Battery check", titles.any { it.contains("Фоновый режим") || it.contains("батарея") })
        assertTrue("Must include Tor Core status", titles.any { it.contains("Onionmasq") })
        assertTrue("Must include Bridge Pool check", titles.any { it.contains("Пул мостов") })
        assertTrue("Must include Carrier Mode check", titles.any { it.contains("Carrier") })
        assertTrue("Must include Per-App Circuit Isolation", titles.any { it.contains("Изоляция цепочек") })
        assertTrue("Must include DNS through Tor check", titles.any { it.contains("DNS через Tor") })
        assertTrue("Must include Fail-Closed Policy check", titles.any { it.contains("Fail-Closed") })
        assertTrue("Must include UDP & QUIC Drop check", titles.any { it.contains("QUIC") })
        assertTrue("Must include IPv6 protection check", titles.any { it.contains("IPv6") })
        assertTrue("Must include Clock Skew check", titles.any { it.contains("системного времени") })

        val verdict = viewModel.verdict.value
        assertNotNull(verdict)
        assertEquals("Tor не подключен", verdict?.title)
    }

    @Test
    fun testTorDoctorConnectedChecks() = runTest(testDispatcher) {
        connectionStateFlow.value = ConnectionState.Connected(profileName = "Tor Network", connectedAt = System.currentTimeMillis())
        torStatusFlow.value = EngineStatus.Connected(summary = "Tor Connected (Multi-hop isolated)")
        torStatsFlow.value = app.nebula.vpn.core.model.TrafficStats(totalDownloadBytes = 4096, totalUploadBytes = 2048)

        viewModel.runDiagnostics(testDispatcher)
        advanceUntilIdle()

        val checks = viewModel.checks.value
        val titles = checks.map { it.title }
        println("TOR CONNECTED TITLES: $titles")
        assertTrue("Must include End-to-end data transfer check when connected", titles.any { it.contains("Передача данных в сети Tor") })

        val verdict = viewModel.verdict.value
        assertNotNull(verdict)
        assertTrue("Verdict must be non-empty", verdict?.title?.isNotEmpty() == true)
    }

    @Test
    fun testTorDoctorSanitizedBundleRedaction() = runTest(testDispatcher) {
        viewModel.runDiagnostics(testDispatcher)
        advanceUntilIdle()

        val bundle = viewModel.generateRedactedDiagnosticsBundle()
        assertNotNull(bundle)

        // Sensitive credentials and bridge parameters MUST NOT leak in the bundle (Hard Rule 5)
        assertFalse("Raw URL secret must not appear in report", bundle.contains("url=https://example.com/secret"))
        assertTrue("Bridge lines must be masked with wildcards", bundle.contains("webtunnel") && bundle.contains("***"))

        // Raw IPs in Tor engine logs must be masked to x.x.x.x
        assertFalse("Engine logs must not contain raw IP 192.0.2.1", bundle.contains("192.0.2.1"))
        assertTrue("Engine logs must replace IPs with x.x.x.x", bundle.contains("x.x.x.x"))
        assertFalse("Engine logs must not leak fingerprint", bundle.contains("1234567890abcdef1234567890abcdef12345678"))
        assertTrue("Engine logs must replace fingerprint with [FINGERPRINT]", bundle.contains("[FINGERPRINT]"))
        assertFalse("Engine logs must not leak cert", bundle.contains("cert=xyz123"))
    }
}
