package app.nebula.vpn.desktop.tray

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.rememberTrayState
import app.nebula.vpn.desktop.i18n.DesktopStrings
import app.nebula.vpn.desktop.state.DesktopAppState
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage

@Composable
fun ApplicationScope.DesktopTray(
    state: DesktopAppState,
    isVisible: Boolean,
    onToggleWindow: () -> Unit,
    onExit: () -> Unit
) {
    val trayState = rememberTrayState()
    val connState by state.connectionState.collectAsState()
    val lang = state.currentLanguage

    // Generate dynamic 16x16 icon based on connection state
    val iconImage = remember(connState) {
        val img = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        val dotColor = if (connState.isConnected) {
            Color(0, 208, 132) // Emerald green
        } else {
            Color(148, 163, 184) // Slate gray
        }
        g.color = dotColor
        g.fillOval(2, 2, 12, 12)
        g.dispose()
        img
    }

    Tray(
        icon = BitmapPainter(iconImage.toComposeImageBitmap()),
        state = trayState,
        tooltip = "Nebula VPN (${if (connState.isConnected) "Connected" else "Disconnected"})",
        onAction = onToggleWindow,
        menu = {
            Item(
                text = if (connState.isConnected) DesktopStrings.get("btn_disconnect", lang) else DesktopStrings.get("btn_connect", lang),
                onClick = { state.toggleConnect() }
            )
            Separator()
            Item(
                text = if (isVisible) DesktopStrings.get("tray_hide", lang) else DesktopStrings.get("tray_show", lang),
                onClick = onToggleWindow
            )
            Separator()
            Item(
                text = DesktopStrings.get("tray_quit", lang),
                onClick = onExit
            )
        }
    )
}
