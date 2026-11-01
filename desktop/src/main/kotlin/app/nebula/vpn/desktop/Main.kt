package app.nebula.vpn.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import app.nebula.vpn.desktop.state.DesktopAppState
import app.nebula.vpn.desktop.tray.DesktopTray
import app.nebula.vpn.desktop.ui.DesktopApp
import kotlinx.coroutines.runBlocking
import java.awt.Dimension
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    var isMinimizedStartup = args.contains("--minimized")

    val appState = DesktopAppState()

    // 1. Single Instance Guard
    var restoreWindowTrigger: (() -> Unit)? = null
    val singleInstanceGuard = SingleInstanceGuard {
        restoreWindowTrigger?.invoke()
    }

    if (!singleInstanceGuard.acquireOrSignal()) {
        println("Nebula is already running. Signaled existing instance to restore window.")
        exitProcess(0)
    }

    // 2. Register JVM shutdown hook for crash/exit safety
    Runtime.getRuntime().addShutdownHook(Thread({
        singleInstanceGuard.release()
        runBlocking {
            appState.controller.stop()
        }
    }, "Nebula-ShutdownHook"))

    // 3. Check for deep link argument
    val deepLinkArg = args.firstOrNull {
        it.startsWith("vless://", ignoreCase = true) ||
        it.startsWith("vmess://", ignoreCase = true) ||
        it.startsWith("trojan://", ignoreCase = true) ||
        it.startsWith("ss://", ignoreCase = true) ||
        it.startsWith("hysteria2://", ignoreCase = true) ||
        it.startsWith("tuic://", ignoreCase = true)
    }
    if (deepLinkArg != null) {
        appState.importLinkOrSubscription(deepLinkArg) { _, _ -> }
    }

    application {
        var isVisible by remember { mutableStateOf(!isMinimizedStartup) }
        val windowState = remember { WindowState(width = 980.dp, height = 640.dp) }

        restoreWindowTrigger = {
            isVisible = true
            windowState.isMinimized = false
        }

        // System Tray Integration
        DesktopTray(
            state = appState,
            isVisible = isVisible,
            onToggleWindow = {
                isVisible = !isVisible
                if (isVisible) windowState.isMinimized = false
            },
            onExit = {
                runBlocking {
                    appState.controller.stop()
                }
                singleInstanceGuard.release()
                exitApplication()
            }
        )

        // Main Desktop Window
        if (isVisible) {
            Window(
                onCloseRequest = {
                    if (appState.closeToTray) {
                        isVisible = false
                    } else {
                        runBlocking {
                            appState.controller.stop()
                        }
                        singleInstanceGuard.release()
                        exitApplication()
                    }
                },
                state = windowState,
                title = "Nebula VPN",
                resizable = true
            ) {
                window.minimumSize = Dimension(900, 600)
                DesktopApp(appState)
            }
        }
    }
}
