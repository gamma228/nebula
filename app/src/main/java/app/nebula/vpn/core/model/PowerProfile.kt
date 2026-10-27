package app.nebula.vpn.core.model

enum class PowerProfile(
    val id: String,
    val displayName: String,
    val interactiveIntervalMs: Long,
    val backgroundIntervalMs: Long,
    val coreLogLevel: String,
    val description: String
) {
    BALANCED(
        id = "BALANCED",
        displayName = "Balanced",
        interactiveIntervalMs = 3000L,
        backgroundIntervalMs = 60000L,
        coreLogLevel = "warn",
        description = "Optimal battery life with live 3s speed while screen is on; freezes notification updates when screen is off."
    ),
    BATTERY_SAVER(
        id = "BATTERY_SAVER",
        displayName = "Battery Saver",
        interactiveIntervalMs = 5000L,
        backgroundIntervalMs = 0L,
        coreLogLevel = "error",
        description = "Maximum battery endurance. Minimum core wakeups, error-only logging, notification speed disabled when screen is locked."
    ),
    HIGH_PERFORMANCE(
        id = "HIGH_PERFORMANCE",
        displayName = "High Performance",
        interactiveIntervalMs = 1000L,
        backgroundIntervalMs = 5000L,
        coreLogLevel = "info",
        description = "Continuous 1s telemetry and diagnostics. Higher CPU usage."
    );

    companion object {
        fun fromId(id: String?): PowerProfile {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: BALANCED
        }
    }
}
