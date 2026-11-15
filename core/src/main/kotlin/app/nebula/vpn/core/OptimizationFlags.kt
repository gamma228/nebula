package app.nebula.vpn.core

/**
 * Debug and safety flags for battery, performance, and networking optimizations.
 * Allows bisection and safe toggling of optimizations without regressing connectivity.
 */
object OptimizationFlags {
    /**
     * If true, limits Go runtime to GOMAXPROCS=1 instead of multi-core.
     * Prevents multi-core spinning while capping battery drain on mobile ARM big.LITTLE architectures.
     */
    var limitGoConcurrency: Boolean = true
    var goMaxProcsValue: Int = 1

    /**
     * If true, syncs screen interactive state to CommandServer.
     */
    var syncScreenInteractive: Boolean = true

    /**
     * If true, suspends notification speed updates when screen is locked to save battery.
     */
    var freezeNotificationOnScreenOff: Boolean = true

    /**
     * Default TUN MTU. 1500 is standard Ethernet/cellular; 1400 avoids cellular fragmentation.
     */
    var defaultMtu: Int = 1500

    /**
     * Force direct DNS to use dns-local (native platform resolver) outside tunnel.
     */
    var useLocalDnsForBootstrap: Boolean = true

    /**
     * Force dump of sanitized sing-box config to debug log/file on start.
     */
    var dumpConfigOnStart: Boolean = true
}
