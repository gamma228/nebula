package app.nebula.vpn.core.proxy

import io.nekohasekai.libbox.TunOptions

/**
 * TunProvider bridges the platform Android VpnService to libbox's TUN establishment.
 */
interface TunProvider {
    /**
     * Called by PlatformInterface inside libbox when opening the tunnel.
     * The implementation must build [android.net.VpnService.Builder], apply split-tunneling,
     * establish the ParcelFileDescriptor, and return the detached native file descriptor.
     */
    fun openTun(options: TunOptions): Int

    /**
     * Protects an outbound socket from VPN looping.
     */
    fun protect(fd: Int): Boolean
}
