package app.nebula.vpn.desktop.hotkey

import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import java.util.concurrent.atomic.AtomicBoolean

class GlobalHotkeyManager(
    private val onToggleConnect: () -> Unit,
    private val onToggleWindow: () -> Unit
) {
    private val isRunning = AtomicBoolean(false)
    private var thread: Thread? = null
    private var threadId: Int = 0

    companion object {
        const val HOTKEY_CONNECT_ID = 1001
        const val HOTKEY_WINDOW_ID = 1002
    }

    fun start() {
        if (!isRunning.compareAndSet(false, true)) return

        thread = Thread({
            try {
                threadId = Kernel32.INSTANCE.GetCurrentThreadId()

                // Register Ctrl+Shift+V for Connect / Disconnect
                val registeredV = User32.INSTANCE.RegisterHotKey(
                    null,
                    HOTKEY_CONNECT_ID,
                    WinUser.MOD_CONTROL or WinUser.MOD_SHIFT,
                    0x56 // VK_V
                )

                // Register Ctrl+Shift+N for Show / Hide Window
                val registeredN = User32.INSTANCE.RegisterHotKey(
                    null,
                    HOTKEY_WINDOW_ID,
                    WinUser.MOD_CONTROL or WinUser.MOD_SHIFT,
                    0x4E // VK_N
                )

                val msg = WinUser.MSG()
                while (isRunning.get()) {
                    val result = User32.INSTANCE.GetMessage(msg, null, 0, 0)
                    if (result == 0 || result == -1) break

                    if (msg.message == WinUser.WM_HOTKEY) {
                        when (msg.wParam.toInt()) {
                            HOTKEY_CONNECT_ID -> onToggleConnect()
                            HOTKEY_WINDOW_ID -> onToggleWindow()
                        }
                    }
                    User32.INSTANCE.TranslateMessage(msg)
                    User32.INSTANCE.DispatchMessage(msg)
                }

                if (registeredV) User32.INSTANCE.UnregisterHotKey(null, HOTKEY_CONNECT_ID)
                if (registeredN) User32.INSTANCE.UnregisterHotKey(null, HOTKEY_WINDOW_ID)
            } catch (_: Throwable) {
                // Fallback gracefully on non-windows or missing native symbols
            }
        }, "Nebula-GlobalHotkey-Thread").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        if (!isRunning.compareAndSet(true, false)) return
        try {
            if (threadId != 0) {
                User32.INSTANCE.PostThreadMessage(
                    threadId,
                    WinUser.WM_QUIT,
                    WinDef.WPARAM(0),
                    WinDef.LPARAM(0)
                )
            }
        } catch (_: Throwable) {}
        thread?.interrupt()
        thread = null
    }
}
