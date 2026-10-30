package app.nebula.vpn.core.model

enum class PowerProfile(
    val id: String,
    val displayName: String,
    val interactiveIntervalMs: Long,
    val backgroundIntervalMs: Long,
    val coreLogLevel: String,
    val tunStack: String = "gvisor",
    val endpointIndependentNat: Boolean = true,
    val description: String
) {
    BALANCED(
        id = "BALANCED",
        displayName = "Balanced",
        interactiveIntervalMs = 1000L,
        backgroundIntervalMs = 0L,
        coreLogLevel = "warn",
        tunStack = "gvisor",
        endpointIndependentNat = true,
        description = "Smooth 1s live speed while screen is on; completely freezes updates when screen is off to conserve 100% battery."
    ),
    BATTERY_SAVER(
        id = "BATTERY_SAVER",
        displayName = "Battery Saver",
        interactiveIntervalMs = 2000L,
        backgroundIntervalMs = 0L,
        coreLogLevel = "error",
        tunStack = "gvisor",
        endpointIndependentNat = true,
        description = "Maximum battery endurance. 2s sampling when active, error-only logging, notification speed disabled when screen is locked."
    ),
    HIGH_PERFORMANCE(
        id = "HIGH_PERFORMANCE",
        displayName = "High Performance",
        interactiveIntervalMs = 1000L,
        backgroundIntervalMs = 5000L,
        coreLogLevel = "info",
        tunStack = "gvisor",
        endpointIndependentNat = true,
        description = "Continuous 1s telemetry and diagnostics. Higher CPU usage."
    );

    companion object {
        fun fromId(id: String?): PowerProfile {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: BALANCED
        }
    }
}
