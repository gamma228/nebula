package app.nebula.vpn.core.windows

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

interface WininetNative : StdCallLibrary {
    fun InternetSetOption(hInternet: Pointer?, dwOption: Int, lpBuffer: Pointer?, dwBufferLength: Int): Boolean

    companion object {
        val INSTANCE: WininetNative by lazy {
            Native.load("wininet", WininetNative::class.java, W32APIOptions.DEFAULT_OPTIONS)
        }
    }
}

@Serializable
data class SystemProxyBackup(
    val proxyEnable: Int,
    val proxyServer: String,
    val proxyOverride: String,
    val timestampMs: Long = System.currentTimeMillis()
)

class WindowsSystemProxy(
    private val stateDir: File = File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "Nebula")
) {
    companion object {
        private const val INTERNET_SETTINGS_KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings"
        private const val INTERNET_OPTION_SETTINGS_CHANGED = 39
        private const val INTERNET_OPTION_REFRESH = 37
        const val DEFAULT_BYPASS = "<local>;localhost;127.*;10.*;172.16.*;172.17.*;172.18.*;172.19.*;172.20.*;172.21.*;172.22.*;172.23.*;172.24.*;172.25.*;172.26.*;172.27.*;172.28.*;172.29.*;172.30.*;172.31.*;192.168.*"
    }

    private val json = Json { prettyPrint = true }
    private val backupFile = File(stateDir, "system_proxy_state.json")

    fun getCurrentSettings(): SystemProxyBackup {
        val enable = try {
            Advapi32Util.registryGetIntValue(WinReg.HKEY_CURRENT_USER, INTERNET_SETTINGS_KEY, "ProxyEnable")
        } catch (_: Exception) { 0 }

        val server = try {
            Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, INTERNET_SETTINGS_KEY, "ProxyServer")
        } catch (_: Exception) { "" }

        val override = try {
            Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, INTERNET_SETTINGS_KEY, "ProxyOverride")
        } catch (_: Exception) { "" }

        return SystemProxyBackup(enable, server, override)
    }

    @Synchronized
    fun enableProxy(host: String = "127.0.0.1", port: Int, bypass: String = DEFAULT_BYPASS): Boolean {
        return try {
            stateDir.mkdirs()
            // 1. Back up current settings only if not already saved
            if (!backupFile.exists()) {
                val current = getCurrentSettings()
                backupFile.writeText(json.encodeToString(current))
            }

            // 2. Set proxy settings in Windows Registry
            Advapi32Util.registrySetIntValue(WinReg.HKEY_CURRENT_USER, INTERNET_SETTINGS_KEY, "ProxyEnable", 1)
            Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, INTERNET_SETTINGS_KEY, "ProxyServer", "$host:$port")
            Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, INTERNET_SETTINGS_KEY, "ProxyOverride", bypass)

            // 3. Notify Windows WinINET subsystem
            notifySystem()
            true
        } catch (e: Exception) {
            System.err.println("Failed to enable Windows system proxy: ${e.message}")
            false
        }
    }

    @Synchronized
    fun disableProxy(): Boolean {
        return try {
            if (backupFile.exists()) {
                try {
                    val original = json.decodeFromString<SystemProxyBackup>(backupFile.readText())
                    Advapi32Util.registrySetIntValue(WinReg.HKEY_CURRENT_USER, INTERNET_SETTINGS_KEY, "ProxyEnable", original.proxyEnable)
                    Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, INTERNET_SETTINGS_KEY, "ProxyServer", original.proxyServer)
                    Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, INTERNET_SETTINGS_KEY, "ProxyOverride", original.proxyOverride)
                } catch (e: Exception) {
                    // Fallback to simply disabling
                    Advapi32Util.registrySetIntValue(WinReg.HKEY_CURRENT_USER, INTERNET_SETTINGS_KEY, "ProxyEnable", 0)
                } finally {
                    backupFile.delete()
                }
            } else {
                Advapi32Util.registrySetIntValue(WinReg.HKEY_CURRENT_USER, INTERNET_SETTINGS_KEY, "ProxyEnable", 0)
            }

            notifySystem()
            true
        } catch (e: Exception) {
            System.err.println("Failed to disable Windows system proxy: ${e.message}")
            false
        }
    }

    @Synchronized
    fun cleanupStaleState(): Boolean {
        if (backupFile.exists()) {
            System.err.println("Detected stale proxy backup file from unclean shutdown. Restoring original settings...")
            return disableProxy()
        }
        return true
    }

    fun isProxyEnabled(): Boolean {
        return try {
            val enable = Advapi32Util.registryGetIntValue(WinReg.HKEY_CURRENT_USER, INTERNET_SETTINGS_KEY, "ProxyEnable")
            enable == 1
        } catch (_: Exception) {
            false
        }
    }

    private fun notifySystem() {
        try {
            WininetNative.INSTANCE.InternetSetOption(null, INTERNET_OPTION_SETTINGS_CHANGED, null, 0)
            WininetNative.INSTANCE.InternetSetOption(null, INTERNET_OPTION_REFRESH, null, 0)
        } catch (e: Exception) {
            System.err.println("Warning: Failed to broadcast INTERNET_OPTION_SETTINGS_CHANGED: ${e.message}")
        }
    }
}
