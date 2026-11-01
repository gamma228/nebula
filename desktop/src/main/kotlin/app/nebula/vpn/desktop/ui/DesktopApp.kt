package app.nebula.vpn.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    DesktopTheme(mode = state.currentThemeMode) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Sidebar(state)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
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
