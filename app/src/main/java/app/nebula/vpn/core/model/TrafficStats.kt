package app.nebula.vpn.core.model

data class TrafficStats(
    val uploadSpeedBps: Long = 0,
    val downloadSpeedBps: Long = 0,
    val totalUploadBytes: Long = 0,
    val totalDownloadBytes: Long = 0,
    val memoryUsageBytes: Long = 0,
    val activeConnections: Int = 0
)
