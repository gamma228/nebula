package app.nebula.vpn

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.VpnService
import app.nebula.vpn.core.model.AppRoutingMode
import app.nebula.vpn.core.parser.SplitTunnelApplier
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitTunnelApplierTest {

    @Test
    fun testForwardAppsOnlyCallsAddAllowedApplication() {
        val builder = mockk<VpnService.Builder>(relaxed = true)
        val pm = mockk<PackageManager>()
        every { pm.getPackageInfo("org.telegram.messenger", 0) } returns PackageInfo()
        every { pm.getPackageInfo("com.unknown.app", 0) } throws PackageManager.NameNotFoundException()

        val result = SplitTunnelApplier.apply(
            builder = builder,
            packageManager = pm,
            ownPackageName = "app.nebula.vpn",
            mode = AppRoutingMode.FORWARD_APPS,
            targetPackages = listOf("org.telegram.messenger", "com.unknown.app")
        )

        verify(exactly = 1) { builder.addAllowedApplication("org.telegram.messenger") }
        verify(exactly = 0) { builder.addDisallowedApplication(any()) }

        assertEquals(listOf("org.telegram.messenger"), result.appliedPackages)
        assertEquals(listOf("com.unknown.app"), result.missingPackages)
    }

    @Test
    fun testBypassAppsOnlyCallsAddDisallowedApplication() {
        val builder = mockk<VpnService.Builder>(relaxed = true)
        val pm = mockk<PackageManager>()
        every { pm.getPackageInfo("com.android.chrome", 0) } returns PackageInfo()

        val result = SplitTunnelApplier.apply(
            builder = builder,
            packageManager = pm,
            ownPackageName = "app.nebula.vpn",
            mode = AppRoutingMode.BYPASS_APPS,
            targetPackages = listOf("com.android.chrome")
        )

        verify(exactly = 1) { builder.addDisallowedApplication("com.android.chrome") }
        verify(exactly = 0) { builder.addAllowedApplication(any()) }

        assertEquals(listOf("com.android.chrome"), result.appliedPackages)
        assertTrue(result.missingPackages.isEmpty())
    }

    @Test
    fun testEmptyWhitelistFallsBackToOwnPackage() {
        val builder = mockk<VpnService.Builder>(relaxed = true)
        val pm = mockk<PackageManager>()
        every { pm.getPackageInfo("app.nebula.vpn", 0) } returns PackageInfo()

        val result = SplitTunnelApplier.apply(
            builder = builder,
            packageManager = pm,
            ownPackageName = "app.nebula.vpn",
            mode = AppRoutingMode.FORWARD_APPS,
            targetPackages = emptyList()
        )

        verify(exactly = 1) { builder.addAllowedApplication("app.nebula.vpn") }
        verify(exactly = 0) { builder.addDisallowedApplication(any()) }

        assertEquals(listOf("app.nebula.vpn"), result.appliedPackages)
    }
}
