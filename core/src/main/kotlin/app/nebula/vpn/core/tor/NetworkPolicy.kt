package app.nebula.vpn.core.tor

import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class TorPolicyBlockedException(message: String) : IOException(message)

/**
 * Central Fail-Closed Network Policy Guard for Tor Mode (Milestone T6).
 *
 * In Tor mode, nothing may leave the device over the clearnet except through Tor.
 * While Tor mode is active, this guard interceptor blocks ALL outbound requests made by
 * the application itself (subscription refreshes, update checks, doctor checks, image loaders, etc.)
 * EXCEPT:
 * 1. Local loopback addresses (127.0.0.1, ::1) used for local engine IPC / SOCKS proxies.
 * 2. The single configured Carrier Server host & port (when Carrier Mode is enabled).
 *
 * Any violation throws [TorPolicyBlockedException], ensuring zero clearnet leaks.
 */
object NetworkPolicy {

    private val isTorActive = AtomicBoolean(false)
    private val allowedCarrierHost = AtomicReference<String?>(null)
    private val allowedCarrierPort = AtomicReference<Int?>(null)

    fun setTorActive(active: Boolean) {
        isTorActive.set(active)
    }

    fun isTorActive(): Boolean = isTorActive.get()

    fun setCarrierEndpoint(host: String?, port: Int?) {
        allowedCarrierHost.set(host?.trim())
        allowedCarrierPort.set(port)
    }

    fun clearCarrierEndpoint() {
        allowedCarrierHost.set(null)
        allowedCarrierPort.set(null)
    }

    /**
     * Checks if a target host and port are permitted under the active network policy.
     * Throws [TorPolicyBlockedException] if clearnet egress is forbidden.
     */
    fun checkAllowed(host: String, port: Int) {
        if (!isTorActive.get()) {
            return // In sing-box mode, standard policy applies
        }

        // 1. Loopback is always permitted for local IPC
        if (isLoopback(host)) {
            return
        }

        // 2. Configured Carrier endpoint is permitted if enabled
        val carrierHost = allowedCarrierHost.get()
        val carrierPort = allowedCarrierPort.get()
        if (carrierHost != null && carrierPort != null) {
            val hostMatches = host.equals(carrierHost, ignoreCase = true)
            val portMatches = (port == carrierPort)
            if (hostMatches && portMatches) {
                return
            }
        }

        // 3. All other clearnet requests are strictly BLOCKED fail-closed
        throw TorPolicyBlockedException(
            "FAIL-CLOSED POLICY VIOLATION: In Tor mode, outbound request to $host:$port is blocked to prevent clearnet leak."
        )
    }

    private fun isLoopback(host: String): Boolean {
        if (host.equals("localhost", ignoreCase = true) ||
            host.startsWith("127.") ||
            host == "::1" ||
            host == "0:0:0:0:0:0:0:1"
        ) {
            return true
        }
        return false
    }
}

