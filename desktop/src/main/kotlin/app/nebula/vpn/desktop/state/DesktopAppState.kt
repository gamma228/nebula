package app.nebula.vpn.desktop.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.nebula.vpn.core.builder.SingBoxConfigBuilder
import app.nebula.vpn.core.model.ConnectionState
import app.nebula.vpn.core.model.ProxyProfile
import app.nebula.vpn.core.model.Result
import app.nebula.vpn.core.model.RoutingRule
import app.nebula.vpn.core.model.ThemeMode
import app.nebula.vpn.core.model.TrafficStats
import app.nebula.vpn.core.parser.LinkParser
import app.nebula.vpn.core.parser.SubscriptionFetcher
import app.nebula.vpn.core.proxy.DefaultLatencyProbe
import app.nebula.vpn.core.proxy.DefaultLatencyTester
import app.nebula.vpn.core.proxy.ServerTestState
import app.nebula.vpn.core.storage.DesktopStorageRepository
import app.nebula.vpn.core.windows.WindowsCoreController
import app.nebula.vpn.core.windows.WindowsSystemIntegration
import app.nebula.vpn.desktop.i18n.AppLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

enum class NavScreen {
    HOME,
    SERVERS,
    ROUTING,
    TOOLS,
    SETTINGS
}

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
    var connectionMode by mutableStateOf("SYSTEM_PROXY") // "SYSTEM_PROXY" or "TUN"
    var killSwitchEnabled by mutableStateOf(false)
    var tunMtu by mutableStateOf(1400)
    var remoteDnsUrl by mutableStateOf("https://1.1.1.1/dns-query")
    var autostartEnabled by mutableStateOf(false)
    var autostartMinimized by mutableStateOf(false)
    var closeToTray by mutableStateOf(true)

    private val _profiles = MutableStateFlow<List<ProxyProfile>>(emptyList())
    val profiles = _profiles.asStateFlow()

    private val _selectedProfile = MutableStateFlow<ProxyProfile?>(null)
    val selectedProfile = _selectedProfile.asStateFlow()

    private val _rules = MutableStateFlow<List<RoutingRule>>(emptyList())
    val rules = _rules.asStateFlow()

    val connectionState = controller.connectionState
    val trafficStats = controller.trafficStats

    var isPingTestingAll by mutableStateOf(false)

    init {
        loadData()
    }

    fun loadData() {
        scope.launch {
            val loadedProfiles = storage.getAllProfiles()
            _profiles.value = loadedProfiles

            val selectedId = storage.getSelectedProfileId()
            _selectedProfile.value = loadedProfiles.firstOrNull { it.id == selectedId }
                ?: loadedProfiles.firstOrNull()

            _rules.value = storage.getAllRules()
            autostartEnabled = integration.isAutoStartEnabled()
        }
    }

    fun selectProfile(profile: ProxyProfile) {
        _selectedProfile.value = profile
        scope.launch {
            storage.setSelectedProfileId(profile.id)
            if (connectionState.value.isConnected) {
                // In-place switch
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
                    fakeDnsEnabled = true,
                    killSwitchEnabled = killSwitchEnabled && isTun,
                    mtu = tunMtu,
                    localPortMixedInbound = 2080,
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

    fun importLinkOrSubscription(input: String, onComplete: (Int, String?) -> Unit) {
        scope.launch {
            val trimmed = input.trim()
            if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
                // Subscription URL
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
                // Single share link
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
}
