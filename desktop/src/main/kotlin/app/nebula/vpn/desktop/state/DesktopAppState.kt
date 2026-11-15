package app.nebula.vpn.desktop.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import app.nebula.vpn.core.builder.SingBoxConfigBuilder
import app.nebula.vpn.core.model.ConnectionState
import app.nebula.vpn.core.model.ProxyProfile
import app.nebula.vpn.core.model.Result
import app.nebula.vpn.core.model.RuleAction
import app.nebula.vpn.core.model.RuleType
import app.nebula.vpn.core.model.RoutingRule
import app.nebula.vpn.core.model.ThemeMode
import app.nebula.vpn.core.model.SurfaceStyle
import app.nebula.vpn.core.parser.LinkParser
import app.nebula.vpn.core.parser.SubscriptionFetcher
import app.nebula.vpn.core.proxy.DefaultLatencyProbe
import app.nebula.vpn.core.proxy.DefaultLatencyTester
import app.nebula.vpn.core.proxy.ServerTestState
import app.nebula.vpn.core.storage.DesktopSettings
import app.nebula.vpn.core.storage.DesktopStorageRepository
import app.nebula.vpn.core.windows.WindowsCoreController
import app.nebula.vpn.core.windows.WindowsSystemIntegration
import app.nebula.vpn.desktop.i18n.AppLanguage
import app.nebula.vpn.desktop.theme.DesktopThemePreset
import app.nebula.vpn.desktop.ui.components.SpeedSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.skia.Image
import java.io.File
import java.util.UUID

enum class NavScreen {
    HOME,
    SERVERS,
    ROUTING,
    TOOLS,
    SETTINGS
}

data class AppPreset(
    val titleKey: String,
    val exeNames: List<String>,
    val action: RuleAction,
    val iconDescription: String
)

class DesktopAppState(
    val workingDir: File = WindowsCoreController.resolveDefaultWorkingDir()
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val storage = DesktopStorageRepository(workingDir)
    val controller = WindowsCoreController(workingDir = workingDir)
    val integration = WindowsSystemIntegration()

    var currentScreen by mutableStateOf(NavScreen.HOME)
    var currentLanguage by mutableStateOf(AppLanguage.RU)
    var currentThemeMode by mutableStateOf(ThemeMode.DARK)
    var currentThemePreset by mutableStateOf(DesktopThemePreset.EMERALD_CYBER)
    var surfaceStyle by mutableStateOf(SurfaceStyle.SOLID)
    var isAlwaysOnTop by mutableStateOf(false)
    var customAccentHex by mutableStateOf("#00D084")
    var backgroundImagePath by mutableStateOf<String?>(null)
    var backgroundDimAlpha by mutableStateOf(0.35f)
    var wallpaperBitmap by mutableStateOf<ImageBitmap?>(null)

    var connectionMode by mutableStateOf("SYSTEM_PROXY") // "SYSTEM_PROXY" or "TUN"
    var killSwitchEnabled by mutableStateOf(false)
    var tunMtu by mutableStateOf(1400)
    var remoteDnsUrl by mutableStateOf("https://1.1.1.1/dns-query")
    var autostartEnabled by mutableStateOf(false)
    var autostartMinimized by mutableStateOf(false)
    var closeToTray by mutableStateOf(true)

    // Anti-Censorship & Incy Anti-DPI Features
    var blockQuic by mutableStateOf(true)
    var fragmentEnabled by mutableStateOf(true)
    var sniSpoofPreset by mutableStateOf("rutube.ru")
    var fakeDnsEnabled by mutableStateOf(true)
    var disableIpv6 by mutableStateOf(true)

    private val _profiles = MutableStateFlow<List<ProxyProfile>>(emptyList())
    val profiles = _profiles.asStateFlow()

    private val _selectedProfile = MutableStateFlow<ProxyProfile?>(null)
    val selectedProfile = _selectedProfile.asStateFlow()

    private val _rules = MutableStateFlow<List<RoutingRule>>(emptyList())
    val rules = _rules.asStateFlow()

    val connectionState = controller.connectionState
    val trafficStats = controller.trafficStats

    // Real-time Traffic Graph History (last 30 seconds)
    val speedHistory = mutableStateListOf<SpeedSample>()

    // Live Active Connections Inspector
    val activeConnections = mutableStateListOf<ActiveConnection>()
    var isPollingConnections by mutableStateOf(false)

    var isPingTestingAll by mutableStateOf(false)

    // Available Application Presets
    val appPresets = listOf(
        AppPreset("preset_telegram", listOf("telegram.exe"), RuleAction.PROXY, "Telegram Messenger"),
        AppPreset("preset_discord", listOf("discord.exe", "update.exe"), RuleAction.PROXY, "Discord Voice & Chat"),
        AppPreset("preset_browser", listOf("chrome.exe", "msedge.exe", "firefox.exe", "brave.exe"), RuleAction.PROXY, "Web Browsers"),
        AppPreset("preset_steam", listOf("steam.exe", "steamwebhelper.exe"), RuleAction.PROXY, "Steam Gaming"),
        AppPreset("preset_spotify", listOf("spotify.exe"), RuleAction.PROXY, "Spotify Music"),
        AppPreset("preset_torrent_bypass", listOf("qbittorrent.exe", "utorrent.exe"), RuleAction.DIRECT, "Torrent Clients (Direct)")
    )

    init {
        loadData()
        startTrafficSampler()
        startConnectionsPoller()
    }

    fun loadData() {
        scope.launch {
            // Load settings
            val settings = storage.getSettings()
            currentLanguage = AppLanguage.entries.firstOrNull { it.code.equals(settings.languageCode, true) } ?: AppLanguage.RU
            currentThemePreset = DesktopThemePreset.fromId(settings.activeThemeId)
            customAccentHex = settings.customAccentHex
            backgroundImagePath = settings.backgroundImagePath
            backgroundDimAlpha = settings.backgroundDimAlpha
            wallpaperBitmap = loadWallpaper(settings.backgroundImagePath)
            connectionMode = settings.mode
            closeToTray = settings.closeToTray
            killSwitchEnabled = settings.killSwitchEnabled
            blockQuic = settings.blockQuic
            fragmentEnabled = settings.fragmentEnabled
            sniSpoofPreset = settings.sniSpoofPreset
            remoteDnsUrl = settings.remoteDnsUrl
            fakeDnsEnabled = settings.fakeDnsEnabled
            disableIpv6 = settings.disableIpv6
            tunMtu = settings.tunMtu

            // Load profiles & rules
            val loadedProfiles = storage.getAllProfiles()
            _profiles.value = loadedProfiles

            val selectedId = storage.getSelectedProfileId()
            _selectedProfile.value = loadedProfiles.firstOrNull { it.id == selectedId }
                ?: loadedProfiles.firstOrNull()

            _rules.value = storage.getAllRules()
            autostartEnabled = integration.isAutoStartEnabled()
        }
    }

    private fun startTrafficSampler() {
        scope.launch {
            trafficStats.collect { stat ->
                val sample = SpeedSample(
                    downBps = stat.downloadSpeedBps,
                    upBps = stat.uploadSpeedBps
                )
                speedHistory.add(sample)
                if (speedHistory.size > 35) {
                    speedHistory.removeAt(0)
                }
            }
        }
    }

    private fun startConnectionsPoller() {
        scope.launch {
            while (true) {
                if (connectionState.value.isConnected && currentScreen == NavScreen.TOOLS) {
                    try {
                        val snapshot = ClashApiClient.fetchConnections(port = 9090)
                        if (snapshot != null) {
                            activeConnections.clear()
                            activeConnections.addAll(snapshot.connections)
                        }
                    } catch (_: Exception) {}
                }
                delay(2000)
            }
        }
    }

    fun refreshConnections() {
        scope.launch {
            val snapshot = ClashApiClient.fetchConnections(port = 9090)
            if (snapshot != null) {
                activeConnections.clear()
                activeConnections.addAll(snapshot.connections)
            }
        }
    }

    fun closeConnection(id: String) {
        scope.launch {
            ClashApiClient.closeConnection(id, port = 9090)
            refreshConnections()
        }
    }

    fun closeAllConnections() {
        scope.launch {
            ClashApiClient.closeAllConnections(port = 9090)
            activeConnections.clear()
        }
    }

    fun selectProfile(profile: ProxyProfile) {
        _selectedProfile.value = profile
        scope.launch {
            storage.setSelectedProfileId(profile.id)
            if (connectionState.value.isConnected) {
                connect()
            }
        }
    }

    fun toggleConnect() {
        if (connectionState.value.isConnected) {
            disconnect()
        } else {
            connect()
        }
    }

    fun connect() {
        val profile = _selectedProfile.value ?: return
        scope.launch {
            val isTun = connectionMode == "TUN"
            val configJson = SingBoxConfigBuilder.build(
                SingBoxConfigBuilder.BuildConfig(
                    activeProfile = profile,
                    allProfiles = _profiles.value,
                    customRules = _rules.value,
                    remoteDnsUrl = remoteDnsUrl,
                    fakeDnsEnabled = fakeDnsEnabled,
                    killSwitchEnabled = killSwitchEnabled && isTun,
                    mtu = tunMtu,
                    localPortMixedInbound = 2080,
                    ipv6Enabled = !disableIpv6,
                    blockQuic = blockQuic,
                    isTunEnabled = isTun,
                    clashApiPort = 9090,
                    autoDetectInterface = isTun
                )
            )

            controller.start(profile, configJson)
        }
    }

    fun disconnect() {
        scope.launch {
            controller.stop()
        }
    }

    fun setWallpaper(path: String?) {
        backgroundImagePath = path
        wallpaperBitmap = loadWallpaper(path)
        scope.launch {
            storage.updateSettings { it.copy(backgroundImagePath = path) }
        }
    }

    fun setWallpaperDim(alpha: Float) {
        backgroundDimAlpha = alpha.coerceIn(0.05f, 0.90f)
        scope.launch {
            storage.updateSettings { it.copy(backgroundDimAlpha = backgroundDimAlpha) }
        }
    }

    fun setThemePreset(preset: DesktopThemePreset) {
        currentThemePreset = preset
        scope.launch {
            storage.updateSettings { it.copy(activeThemeId = preset.id) }
        }
    }

    fun setCustomAccent(hex: String) {
        customAccentHex = hex
        scope.launch {
            storage.updateSettings { it.copy(customAccentHex = hex) }
        }
    }

    fun addAppPreset(preset: AppPreset) {
        scope.launch {
            preset.exeNames.forEach { exe ->
                val rule = RoutingRule(
                    id = UUID.randomUUID().toString(),
                    type = RuleType.PROCESS_NAME,
                    value = exe,
                    action = preset.action
                )
                storage.saveRule(rule)
            }
            _rules.value = storage.getAllRules()
        }
    }

    fun importLinkOrSubscription(input: String, onComplete: (Int, String?) -> Unit) {
        scope.launch {
            val trimmed = input.trim()
            if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
                when (val fetchResult = SubscriptionFetcher.fetch(trimmed)) {
                    is Result.Success -> {
                        val newNodes = fetchResult.data.profiles
                        if (newNodes.isNotEmpty()) {
                            storage.saveProfiles(newNodes)
                            loadData()
                            onComplete(newNodes.size, null)
                        } else {
                            onComplete(0, "No valid proxy nodes found in subscription")
                        }
                    }
                    is Result.Error -> onComplete(0, fetchResult.exception.message)
                }
            } else {
                when (val parseResult = LinkParser.parse(trimmed)) {
                    is Result.Success -> {
                        storage.saveProfile(parseResult.data)
                        loadData()
                        onComplete(1, null)
                    }
                    is Result.Error -> onComplete(0, parseResult.exception.message)
                }
            }
        }
    }

    fun deleteProfile(profileId: String) {
        scope.launch {
            storage.deleteProfile(profileId)
            loadData()
        }
    }

    fun addRule(rule: RoutingRule) {
        scope.launch {
            storage.saveRule(rule)
            _rules.value = storage.getAllRules()
        }
    }

    fun deleteRule(ruleId: String) {
        scope.launch {
            storage.deleteRule(ruleId)
            _rules.value = storage.getAllRules()
        }
    }

    fun testAllPings() {
        if (isPingTestingAll) return
        isPingTestingAll = true
        scope.launch {
            try {
                val current = _profiles.value
                val tester = DefaultLatencyTester(DefaultLatencyProbe())
                val collectJob = launch {
                    tester.progress.collect { batchProgress ->
                        val states = batchProgress.currentServerStates
                        _profiles.value = _profiles.value.map { profile ->
                            val s = states[profile.id]
                            if (s is ServerTestState.Ok) {
                                profile.copy(lastPingMs = s.latencyMs)
                            } else profile
                        }
                    }
                }
                tester.testBatch(current)
                collectJob.cancel()
                storage.saveProfiles(_profiles.value)
            } catch (_: Exception) {
            } finally {
                isPingTestingAll = false
            }
        }
    }

    fun updateAutostart(enabled: Boolean, minimized: Boolean) {
        autostartEnabled = enabled
        autostartMinimized = minimized
        integration.setAutoStart(enabled, minimized)
    }

    fun persistSettings() {
        scope.launch {
            storage.updateSettings {
                it.copy(
                    languageCode = currentLanguage.code,
                    activeThemeId = currentThemePreset.id,
                    customAccentHex = customAccentHex,
                    backgroundImagePath = backgroundImagePath,
                    backgroundDimAlpha = backgroundDimAlpha,
                    mode = connectionMode,
                    closeToTray = closeToTray,
                    killSwitchEnabled = killSwitchEnabled,
                    blockQuic = blockQuic,
                    fragmentEnabled = fragmentEnabled,
                    sniSpoofPreset = sniSpoofPreset,
                    remoteDnsUrl = remoteDnsUrl,
                    fakeDnsEnabled = fakeDnsEnabled,
                    disableIpv6 = disableIpv6,
                    tunMtu = tunMtu
                )
            }
        }
    }

    private fun loadWallpaper(path: String?): ImageBitmap? {
        if (path.isNullOrBlank()) return null
        return try {
            val f = File(path)
            if (f.exists()) {
                Image.makeFromEncoded(f.readBytes()).toComposeImageBitmap()
            } else null
        } catch (_: Exception) {
            null
        }
    }
}
