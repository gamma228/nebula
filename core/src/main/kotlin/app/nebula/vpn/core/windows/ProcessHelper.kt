package app.nebula.vpn.core.windows

import java.io.File

data class WindowsProcessInfo(
    val pid: Long,
    val name: String,
    val path: String
)

object ProcessHelper {

    /**
     * Lists currently running GUI and desktop application processes that have accessible executable paths.
     * De-duplicated by process name.
     */
    fun getRunningProcesses(): List<WindowsProcessInfo> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<WindowsProcessInfo>()

        ProcessHandle.allProcesses().forEach { handle ->
            try {
                val info = handle.info()
                val cmdOpt = info.command()
                if (cmdOpt.isPresent) {
                    val cmd = cmdOpt.get()
                    if (cmd.isNotBlank()) {
                        val file = File(cmd)
                        val name = file.name
                        if (name.endsWith(".exe", ignoreCase = true) && !seen.contains(name.lowercase())) {
                            seen.add(name.lowercase())
                            result.add(
                                WindowsProcessInfo(
                                    pid = handle.pid(),
                                    name = name,
                                    path = file.absolutePath
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                // Ignore processes whose command line is inaccessible due to OS permissions
            }
        }

        return result.sortedBy { it.name.lowercase() }
    }
}
