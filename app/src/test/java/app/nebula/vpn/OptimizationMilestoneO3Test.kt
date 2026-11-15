package app.nebula.vpn

import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import app.nebula.vpn.core.engine.EngineCapabilities
import app.nebula.vpn.core.engine.EngineStatus
import app.nebula.vpn.core.engine.EngineType
import app.nebula.vpn.core.engine.SingBoxEngine
import app.nebula.vpn.core.engine.TorEngine
import app.nebula.vpn.core.engine.TunnelEngineRegistry
import app.nebula.vpn.core.engine.TunnelStartRequest
import app.nebula.vpn.core.model.AppRoutingMode
import app.nebula.vpn.core.model.ConnectionState
import app.nebula.vpn.core.model.TrafficStats
import app.nebula.vpn.core.parser.SplitTunnelApplier
import app.nebula.vpn.core.proxy.LibboxCore
import app.nebula.vpn.core.proxy.ProxyCore
import app.nebula.vpn.core.proxy.TunProvider
import app.nebula.vpn.data.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress

@OptIn(ExperimentalCoroutinesApi::class)
class OptimizationMilestoneO3Test {

    @Test
    fun `testSplitTunnelApplierBypassesBinderWhenKnownPackagesProvided`() {
        val builder = mockk<VpnService.Builder>(relaxed = true)
        val pm = mockk<PackageManager>()
        // If packageManager.getPackageInfo is called, it will throw an AssertionError to prove it is bypassed
        every { pm.getPackageInfo(any<String>(), any<Int>()) } throws AssertionError("getPackageInfo should not be called when knownInstalledPackages is provided")

        val knownPackages = setOf("org.telegram.messenger", "com.google.android.youtube", "app.nebula.vpn")
        val targetList = listOf("org.telegram.messenger", "com.google.android.youtube", "com.not.installed")

        val result = SplitTunnelApplier.apply(
            builder = builder,
            packageManager = pm,
            ownPackageName = "app.nebula.vpn",
            mode = AppRoutingMode.FORWARD_APPS,
            targetPackages = targetList,
            knownInstalledPackages = knownPackages
        )

        verify(exactly = 1) { builder.addAllowedApplication("org.telegram.messenger") }
        verify(exactly = 1) { builder.addAllowedApplication("com.google.android.youtube") }
        verify(exactly = 0) { builder.addAllowedApplication("com.not.installed") }

        assertEquals(listOf("org.telegram.messenger", "com.google.android.youtube"), result.appliedPackages)
        assertEquals(listOf("com.not.installed"), result.missingPackages)
    }

    @Test
    fun `testRapidEngineSwitch200CyclesWithoutDeadlock`() = runTest(UnconfinedTestDispatcher()) {
        val settingsRepository: SettingsRepository = mockk(relaxed = true)
        val proxyCore: ProxyCore = mockk(relaxed = true)
        val tunProvider: TunProvider = mockk(relaxed = true)
        val torEngine: TorEngine = mockk(relaxed = true)

        val coreStateFlow = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
        val coreStatsFlow = MutableStateFlow(TrafficStats())
        every { proxyCore.state } returns coreStateFlow
        every { proxyCore.stats } returns coreStatsFlow

        val activeEngineFlow = MutableStateFlow(EngineType.SING_BOX)
        every { settingsRepository.activeEngine } returns activeEngineFlow
        coEvery { settingsRepository.setActiveEngine(any()) } answers {
            activeEngineFlow.value = firstArg()
        }

        val singBoxEngine = SingBoxEngine(proxyCore, backgroundScope).apply { this.tunProvider = tunProvider }
        val registry = TunnelEngineRegistry(
            singBoxEngine = singBoxEngine,
            torEngine = torEngine,
            settingsRepository = settingsRepository,
            registryScope = backgroundScope
        )

        // Stress test: 200 rapid cycles of alternating between SING_BOX and TOR
        for (i in 1..200) {
            val targetType = if (i % 2 == 0) EngineType.SING_BOX else EngineType.TOR
            activeEngineFlow.value = targetType
            advanceUntilIdle()
            assertEquals("Engine at cycle $i must match target", targetType, registry.activeEngineType.value)
        }
    }
}
