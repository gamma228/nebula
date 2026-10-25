package app.nebula.vpn.ui

import android.Manifest
import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import app.nebula.vpn.R
import app.nebula.vpn.core.model.ProxyProfile
import app.nebula.vpn.core.model.Result
import app.nebula.vpn.core.model.RoutingRule
import app.nebula.vpn.core.model.RuleAction
import app.nebula.vpn.core.model.RuleType
import app.nebula.vpn.core.parser.LinkParser
import app.nebula.vpn.data.repository.ProfileRepository
import app.nebula.vpn.data.repository.RoutingRepository
import app.nebula.vpn.data.repository.SettingsRepository
import app.nebula.vpn.data.repository.SubscriptionRepository
import app.nebula.vpn.data.repository.ThemeRepository
import app.nebula.vpn.data.repository.VpnRepository
import app.nebula.vpn.ui.appearance.AppearanceScreen
import app.nebula.vpn.ui.appearance.AppearanceViewModel
import app.nebula.vpn.ui.doctor.DoctorScreen
import app.nebula.vpn.ui.doctor.DoctorViewModel
import app.nebula.vpn.ui.home.HomeScreen
import app.nebula.vpn.ui.home.HomeViewModel
import app.nebula.vpn.ui.onboarding.OnboardingScreen
import app.nebula.vpn.ui.routing.RoutingScreen
import app.nebula.vpn.ui.routing.RoutingViewModel
import app.nebula.vpn.ui.scanner.QrScannerDialog
import app.nebula.vpn.ui.servers.ServersScreen
import app.nebula.vpn.ui.servers.ServersViewModel
import app.nebula.vpn.ui.settings.SettingsScreen
import app.nebula.vpn.ui.settings.SettingsViewModel
import app.nebula.vpn.ui.theme.EmeraldGreen
import app.nebula.vpn.ui.theme.NebulaTheme
import app.nebula.vpn.ui.tools.ToolsScreen
import app.nebula.vpn.ui.tools.ToolsViewModel
import app.nebula.vpn.vpn.BootReceiver
import app.nebula.vpn.vpn.ProxyVpnService
import app.nebula.vpn.vpn.VpnTileService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

sealed class Screen(val index: Int, val titleRes: Int, val icon: ImageVector) {
    object Home : Screen(0, R.string.nav_home, Icons.Default.Shield)
    object Servers : Screen(1, R.string.nav_servers, Icons.Default.Dns)
    object Settings : Screen(2, R.string.nav_settings, Icons.Default.Settings)
    object Routing : Screen(3, R.string.nav_routing, Icons.Default.AltRoute)
    object Doctor : Screen(4, R.string.nav_doctor, Icons.Default.MedicalServices)
    object Tools : Screen(5, R.string.nav_tools, Icons.Default.Build)
    object Appearance : Screen(6, R.string.nav_appearance, Icons.Default.Palette)
}

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var themeRepository: ThemeRepository

    @Inject
    lateinit var profileRepository: ProfileRepository

    @Inject
    lateinit var subscriptionRepository: SubscriptionRepository

    @Inject
    lateinit var routingRepository: RoutingRepository

    @Inject
    lateinit var vpnRepository: VpnRepository

    private var onVpnPermissionGranted: (() -> Unit)? = null

    private val vpnPrepareLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            onVpnPermissionGranted?.invoke()
        }
        onVpnPermissionGranted = null
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* handled */ }

    private var pendingImportProfileState = mutableStateOf<ProxyProfile?>(null)
    private var pendingImportSubscriptionUrl = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Apply FLAG_SECURE dynamically
        lifecycleScope.launch {
            settingsRepository.flagSecureEnabled.collect { secure ->
                if (secure) {
                    window.setFlags(
                        WindowManager.LayoutParams.FLAG_SECURE,
                        WindowManager.LayoutParams.FLAG_SECURE
                    )
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }

        // Handle initial intent for deep links
        handleIntent(intent)

        setContent {
            val activeTheme by themeRepository.activeTheme.collectAsState(
                initial = ThemeRepository.PRESET_EMERALD_CYBER
            )
            val isOnboardingDone by settingsRepository.onboardingDone.collectAsState(
                initial = true
            )
            val currentLanguageCode by settingsRepository.languageCode.collectAsState(
                initial = "en"
            )

            val baseContext = LocalContext.current
            val (localizedContext, localizedConfig) = remember(currentLanguageCode, baseContext) {
                val targetLocale = when (currentLanguageCode) {
                    "ru" -> Locale("ru")
                    "uk" -> Locale("uk")
                    "zh-CN", "zh" -> Locale.SIMPLIFIED_CHINESE
                    "de" -> Locale.GERMAN
                    else -> Locale.ENGLISH
                }
                Locale.setDefault(targetLocale)
                val config = Configuration(baseContext.resources.configuration)
                config.setLocale(targetLocale)
                val wrapped = object : ContextWrapper(baseContext) {
                    private val localizedResources: Resources by lazy {
                        baseContext.createConfigurationContext(config).resources
                    }
                    override fun getResources(): Resources = localizedResources
                }
                Pair(wrapped, config)
            }

            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedConfig
            ) {
                NebulaTheme(config = activeTheme) {
                if (!isOnboardingDone) {
                    OnboardingScreen(
                        onComplete = { routingPresetDirectRu ->
                            lifecycleScope.launch {
                                settingsRepository.setOnboardingDone(true)
                                if (routingPresetDirectRu) {
                                    routingRepository.saveRules(
                                        listOf(
                                            RoutingRule(
                                                id = UUID.randomUUID().toString(),
                                                type = RuleType.DOMAIN_SUFFIX,
                                                value = "ru",
                                                action = RuleAction.DIRECT,
                                                priority = 0
                                            ),
                                            RoutingRule(
                                                id = UUID.randomUUID().toString(),
                                                type = RuleType.DOMAIN_SUFFIX,
                                                value = "su",
                                                action = RuleAction.DIRECT,
                                                priority = 1
                                            ),
                                            RoutingRule(
                                                id = UUID.randomUUID().toString(),
                                                type = RuleType.DOMAIN_SUFFIX,
                                                value = "xn--p1ai",
                                                action = RuleAction.DIRECT,
                                                priority = 2
                                            )
                                        )
                                    )
                                }
                            }
                        }
                    )
                } else {
                    MainNavigationContent()
                }

                // Import Single Server Confirmation Dialog
                val pending = pendingImportProfileState.value
                if (pending != null) {
                    AlertDialog(
                        onDismissRequest = { pendingImportProfileState.value = null },
                        title = { Text(stringResource(R.string.add_profile)) },
                        text = {
                            Column {
                                Text("Название: ${pending.name}")
                                Text("Протокол: ${pending.protocol.name}")
                                Text("Адрес: ${pending.server}:${pending.port}")
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                lifecycleScope.launch {
                                    profileRepository.saveProfile(pending)
                                    settingsRepository.setSelectedProfileId(pending.id)
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Импортирован: ${pending.name}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    pendingImportProfileState.value = null
                                }
                            }) {
                                Text("Импортировать")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { pendingImportProfileState.value = null }) {
                                Text("Отмена")
                            }
                        }
                    )
                }

                // Import Subscription Confirmation Dialog
                val pendingSub = pendingImportSubscriptionUrl.value
                if (pendingSub != null) {
                    AlertDialog(
                        onDismissRequest = { pendingImportSubscriptionUrl.value = null },
                        title = { Text("Импортировать подписку?") },
                        text = {
                            Text("Загрузить конфигурацию и серверы по ссылке:\n$pendingSub")
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                val url = pendingSub
                                pendingImportSubscriptionUrl.value = null
                                lifecycleScope.launch {
                                    val res = subscriptionRepository.addSubscription(url)
                                    if (res is Result.Success) {
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Подписка добавлена: ${res.data.title}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else if (res is Result.Error) {
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Ошибка загрузки: ${res.exception.message}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }) {
                                Text("Загрузить")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { pendingImportSubscriptionUrl.value = null }) {
                                Text("Отмена")
                            }
                        }
                    )
                }
            }
        }
    }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri: Uri? = intent?.data
        if (uri != null) {
            val link = uri.toString().trim()
            if (link.startsWith("http://", ignoreCase = true) || link.startsWith("https://", ignoreCase = true)) {
                pendingImportSubscriptionUrl.value = link
            } else {
                val parsed = LinkParser.parse(link)
                if (parsed is Result.Success) {
                    pendingImportProfileState.value = parsed.data
                }
            }
        }
    }

    fun requestVpnPermissionAndExecute(action: () -> Unit) {
        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) {
            onVpnPermissionGranted = action
            vpnPrepareLauncher.launch(prepareIntent)
        } else {
            action.invoke()
        }
    }

    @Composable
    private fun MainNavigationContent() {
        var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
        var showQrScanner by remember { mutableStateOf(false) }

        val homeViewModel: HomeViewModel = hiltViewModel()
        val serversViewModel: ServersViewModel = hiltViewModel()
        val routingViewModel: RoutingViewModel = hiltViewModel()
        val doctorViewModel: DoctorViewModel = hiltViewModel()
        val toolsViewModel: ToolsViewModel = hiltViewModel()
        val appearanceViewModel: AppearanceViewModel = hiltViewModel()
        val settingsViewModel: SettingsViewModel = hiltViewModel()

        // 3 primary bottom bar tabs (INCY-style: Home, Servers, Settings)
        val bottomBarScreens = listOf(
            Screen.Home,
            Screen.Servers,
            Screen.Settings
        )

        // Handle Back navigation for sub-screens
        val isSubScreen = currentScreen in listOf(
            Screen.Routing,
            Screen.Doctor,
            Screen.Tools,
            Screen.Appearance
        )

        if (isSubScreen) {
            BackHandler {
                currentScreen = Screen.Settings
            }
        }

        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    bottomBarScreens.forEach { screen ->
                        val isSelected = when (currentScreen) {
                            Screen.Home -> screen is Screen.Home
                            Screen.Servers -> screen is Screen.Servers
                            else -> screen is Screen.Settings
                        }
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = stringResource(screen.titleRes)) },
                            label = { Text(stringResource(screen.titleRes), fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                            selected = isSelected,
                            onClick = { currentScreen = screen },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = EmeraldGreen,
                                selectedTextColor = EmeraldGreen,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                indicatorColor = EmeraldGreen.copy(alpha = 0.15f)
                            )
                        )
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Sub-screen header with Back button
                    if (isSubScreen) {
                        val subTitle = when (currentScreen) {
                            Screen.Routing -> "Прокси по приложениям"
                            Screen.Appearance -> "Оформление"
                            Screen.Doctor -> "Connection Doctor"
                            Screen.Tools -> "Журнал и инструменты"
                            else -> ""
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            IconButton(onClick = { currentScreen = Screen.Settings }) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = subTitle,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Content
                    Box(modifier = Modifier.weight(1f)) {
                        when (currentScreen) {
                            is Screen.Home -> HomeScreen(
                                viewModel = homeViewModel,
                                onNavigateToServers = { currentScreen = Screen.Servers },
                                onRequestConnect = {
                                    requestVpnPermissionAndExecute {
                                        homeViewModel.toggleConnection()
                                    }
                                }
                            )
                            is Screen.Servers -> ServersScreen(
                                viewModel = serversViewModel,
                                onOpenQrScanner = { showQrScanner = true }
                            )
                            is Screen.Routing -> RoutingScreen(viewModel = routingViewModel)
                            is Screen.Doctor -> DoctorScreen(viewModel = doctorViewModel)
                            is Screen.Tools -> ToolsScreen(viewModel = toolsViewModel)
                            is Screen.Appearance -> AppearanceScreen(viewModel = appearanceViewModel)
                            is Screen.Settings -> SettingsScreen(
                                viewModel = settingsViewModel,
                                onNavigateToRouting = { currentScreen = Screen.Routing },
                                onNavigateToAppearance = { currentScreen = Screen.Appearance },
                                onNavigateToDoctor = { currentScreen = Screen.Doctor },
                                onNavigateToTools = { currentScreen = Screen.Tools }
                            )
                        }
                    }
                }
            }

            if (showQrScanner) {
                QrScannerDialog(
                    onDismissRequest = { showQrScanner = false },
                    onQrCodeScanned = { scannedContent ->
                        showQrScanner = false
                        val trimmed = scannedContent.trim()
                        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
                            pendingImportSubscriptionUrl.value = trimmed
                        } else {
                            val parsed = LinkParser.parse(trimmed)
                            if (parsed is Result.Success) {
                                lifecycleScope.launch {
                                    profileRepository.saveProfile(parsed.data)
                                    settingsRepository.setSelectedProfileId(parsed.data.id)
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Импортирован: ${parsed.data.name}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } else {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Неизвестный формат QR-кода",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                )
            }
        }
    }
}
