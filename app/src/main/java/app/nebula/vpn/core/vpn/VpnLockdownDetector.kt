package app.nebula.vpn.core.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.provider.Settings
import app.nebula.vpn.vpn.ProxyVpnService

data class VpnLockdownStatus(
    val isAlwaysOn: Boolean,
    val isLockdownEnabled: Boolean
)

object VpnLockdownDetector {

    /**
     * Queries whether Always-on VPN and Lockdown mode are active using official public
     * Android APIs (VpnService.isAlwaysOn and VpnService.isLockdownEnabled) on API 29+.
     */
    fun getStatus(context: Context): VpnLockdownStatus {
        val vpnService = (context as? VpnService) ?: ProxyVpnService.activeInstance
        if (vpnService != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val alwaysOn = try { vpnService.isAlwaysOn } catch (_: Throwable) { false }
            val lockdown = try { vpnService.isLockdownEnabled } catch (_: Throwable) { false }
            return VpnLockdownStatus(
                isAlwaysOn = alwaysOn,
                isLockdownEnabled = lockdown
            )
        }
        return VpnLockdownStatus(isAlwaysOn = false, isLockdownEnabled = false)
    }

    fun createVpnSettingsIntent(): Intent {
        return Intent(Settings.ACTION_VPN_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }
}
