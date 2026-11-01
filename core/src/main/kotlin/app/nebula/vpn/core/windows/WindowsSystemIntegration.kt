package app.nebula.vpn.core.windows

import app.nebula.vpn.core.contract.SystemIntegration
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import java.io.File

class WindowsSystemIntegration(
    private val systemProxy: WindowsSystemProxy = WindowsSystemProxy(),
    private val appExePath: String = resolveCurrentExePath()
) : SystemIntegration {

    companion object {
        private const val RUN_REG_KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Run"
        private const val APP_REG_NAME = "Nebula"

        private fun resolveCurrentExePath(): String {
            return try {
                val command = ProcessHandle.current().info().command().orElse("")
                if (command.isNotBlank()) command else "Nebula.exe"
            } catch (_: Exception) {
                "Nebula.exe"
            }
        }
    }

    override fun setSystemProxy(host: String, port: Int, enabled: Boolean): Boolean {
        return if (enabled) {
            systemProxy.enableProxy(host, port)
        } else {
            systemProxy.disableProxy()
        }
    }

    override fun isSystemProxyEnabled(): Boolean {
        return systemProxy.isProxyEnabled()
    }

    override fun setAutoStart(enabled: Boolean, startMinimized: Boolean): Boolean {
        return try {
            if (enabled) {
                val exe = if (appExePath.contains(" ")) "\"$appExePath\"" else appExePath
                val cmd = if (startMinimized) "$exe --minimized" else exe
                Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, RUN_REG_KEY, APP_REG_NAME, cmd)
            } else {
                if (Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, RUN_REG_KEY, APP_REG_NAME)) {
                    Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, RUN_REG_KEY, APP_REG_NAME)
                }
            }
            true
        } catch (e: Exception) {
            System.err.println("Failed to update Windows autostart registry key: ${e.message}")
            false
        }
    }

    override fun isAutoStartEnabled(): Boolean {
        return try {
            Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, RUN_REG_KEY, APP_REG_NAME)
        } catch (_: Exception) {
            false
        }
    }

    override fun showNotification(title: String, message: String, isError: Boolean) {
        // System tray / Windows toast notification hook
        println("[NOTIFICATION] [${if (isError) "ERROR" else "INFO"}] $title - $message")
    }
}
