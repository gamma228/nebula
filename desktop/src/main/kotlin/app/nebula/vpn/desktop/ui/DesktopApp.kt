package app.nebula.vpn.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import app.nebula.vpn.desktop.state.DesktopAppState
import app.nebula.vpn.desktop.state.NavScreen
import app.nebula.vpn.desktop.theme.DesktopTheme
import app.nebula.vpn.desktop.ui.screens.HomeScreen
import app.nebula.vpn.desktop.ui.screens.RoutingScreen
import app.nebula.vpn.desktop.ui.screens.ServersScreen
import app.nebula.vpn.desktop.ui.screens.SettingsScreen
import app.nebula.vpn.desktop.ui.screens.ToolsScreen

@Composable
fun DesktopApp(state: DesktopAppState) {
    DesktopTheme(
        mode = state.currentThemeMode,
        preset = state.currentThemePreset,
        customAccentHex = state.customAccentHex
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val wallpaper = state.wallpaperBitmap
            if (wallpaper != null) {
                // Background Wallpaper Image
                Image(
                    bitmap = wallpaper,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Dimming & Tint Overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = state.backgroundDimAlpha))
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                )
            }

            // Foreground Application Layout
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (wallpaper != null) Color.Transparent
                        else MaterialTheme.colorScheme.background
                    )
            ) {
                Sidebar(state)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (wallpaper != null) MaterialTheme.colorScheme.background.copy(alpha = 0.45f)
                            else MaterialTheme.colorScheme.background
                        )
                ) {
                    when (state.currentScreen) {
                        NavScreen.HOME -> HomeScreen(state)
                        NavScreen.SERVERS -> ServersScreen(state)
                        NavScreen.ROUTING -> RoutingScreen(state)
                        NavScreen.TOOLS -> ToolsScreen(state)
                        NavScreen.SETTINGS -> SettingsScreen(state)
                    }
                }
            }
        }
    }
}
