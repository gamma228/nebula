package app.nebula.vpn.vpn

import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import app.nebula.vpn.NebulaApp
import app.nebula.vpn.R
import app.nebula.vpn.core.builder.SingBoxConfigBuilder
import app.nebula.vpn.core.model.ConnectionState
import app.nebula.vpn.core.model.PowerProfile
import app.nebula.vpn.core.model.TrafficStats
import app.nebula.vpn.core.parser.SplitTunnelApplier
import app.nebula.vpn.core.proxy.ProxyCore
import app.nebula.vpn.core.proxy.TunProvider
import app.nebula.vpn.data.repository.ProfileRepository
import app.nebula.vpn.data.repository.RoutingRepository
import app.nebula.vpn.data.repository.SettingsRepository
import app.nebula.vpn.data.repository.VpnRepository
import app.nebula.vpn.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import io.nekohasekai.libbox.TunOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@AndroidEntryPoint
class ProxyVpnService : VpnService(), TunProvider {

    companion object {
        const val ACTION_START = "app.nebula.vpn.action.START"
        const val ACTION_SWITCH = "app.nebula.vpn.action.SWITCH"
        const val ACTION_STOP = "app.nebula.vpn.action.STOP"
        const val EXTRA_PROFILE_ID = "profile_id"
        private const val NOTIFICATION_ID = 1001

        @Volatile var isRunning: Boolean = false
            internal set
        @Volatile var activeProfileName: String? = null
            internal set
        @Volatile var activeInstance: ProxyVpnService? = null
            internal set
    }

    @Inject lateinit var proxyCore: ProxyCore
    @Inject lateinit var profileRepository: ProfileRepository
    @Inject lateinit var routingRepository: RoutingRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var vpnRepository: VpnRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val teardownScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val vpnMutex = Mutex()

    private var tunNativeFd: Int = -1
    private var tunInterface: ParcelFileDescriptor? = null
    private var tunnelJob: Job? = null
    private var cachedRoutingMode: app.nebula.vpn.core.model.AppRoutingMode = app.nebula.vpn.core.model.AppRoutingMode.GLOBAL
    private var cachedTargetPackages: Set<String> = emptySet()

    private var currentProfileId: String? = null
    private var currentProfileName: String? = null
    private var connectionStartTime: Long = 0L

    private var stateCollectorJob: Job? = null
    private var statsCollectorJob: Job? = null
    private var trafficTickerJob: Job? = null
    private var lastUidRxBytes: Long = -1L
    private var lastUidTxBytes: Long = -1L
    private var lastTrafficSampleTimeMs: Long = 0L
    private var accumulatedRxBytes: Long = 0L
    private var accumulatedTxBytes: Long = 0L
    private var probeJob: Job? = null
    private var screenReceiver: BroadcastReceiver? = null
    @Volatile private var isScreenInteractive: Boolean = true
    @Volatile private var cachedPowerProfile: PowerProfile = PowerProfile.BALANCED
    @Volatile private var cachedShowSpeed: Boolean = true

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        try {
            val procs = if (app.nebula.vpn.core.OptimizationFlags.limitGoConcurrency) {
                app.nebula.vpn.core.OptimizationFlags.goMaxProcsValue.toString()
            } else {
                Runtime.getRuntime().availableProcessors().toString()
            }
            android.system.Os.setenv("GOMAXPROCS", procs, true)
            android.system.Os.setenv("GODEBUG", "madvdontneed=1", true)
        } catch (_: Exception) {}
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        isScreenInteractive = powerManager?.isInteractive ?: true

        serviceScope.launch {
            settingsRepository.powerProfile.collect { id ->
                cachedPowerProfile = PowerProfile.fromId(id)
            }
        }
        serviceScope.launch {
            settingsRepository.showSpeedInNotification.collect { show ->
                cachedShowSpeed = show
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> {
                        isScreenInteractive = true
                        onScreenStateChanged(true)
                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        isScreenInteractive = false
                        onScreenStateChanged(false)
                    }
                }
            }
        }
        screenReceiver = receiver
        try {
            androidx.core.content.ContextCompat.registerReceiver(
                this,
                receiver,
                filter,
                androidx.core.content.ContextCompat.RECEIVER_EXPORTED
            )
        } catch (e: Exception) {
            android.util.Log.w("ProxyVpnService", "Failed to register screen receiver: ${e.message}")
        }
    }

    private fun onScreenStateChanged(screenOn: Boolean) {
        proxyCore.setScreenInteractive(screenOn)
        if (!screenOn) {
            // Screen is OFF: cancel ticker immediately and freeze notification speed updates to let CPU sleep
            trafficTickerJob?.cancel()
            trafficTickerJob = null
            val name = currentProfileName
            if (name != null && vpnRepository.connectionState.value is ConnectionState.Connected) {
                updateForegroundNotification("Connected: $name", "Protected via Nebula")
            }
        } else {
            // Screen is ON: restart ticker immediately so fresh speeds appear without lag
            if (vpnRepository.connectionState.value is ConnectionState.Connected) {
                startTrafficTicker()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val profileId = intent?.getStringExtra(EXTRA_PROFILE_ID)
        android.util.Log.i("ProxyVpnService", "onStartCommand: action=$action, profileId=$profileId, currentState=${vpnRepository.connectionState.value}")

        if (action == ACTION_START || action == ACTION_SWITCH) {
            val title = currentProfileName?.let { "Connected: $it" } ?: "Connecting…"
            val content = if (currentProfileName != null) "Protected via Nebula" else "Initializing tunnel"
            startForegroundNotification(title, content)
        }

        when (action) {
            ACTION_START -> {
                if (vpnRepository.connectionState.value is ConnectionState.Connected && tunInterface != null) {
                    android.util.Log.i("ProxyVpnService", "Already connected, switching profile instead")
                    switchVpnProfile(profileId)
                } else {
                    startVpnTunnel(profileId)
                }
            }
            ACTION_SWITCH -> {
                if (vpnRepository.connectionState.value is ConnectionState.Connected && tunInterface != null) {
                    switchVpnProfile(profileId)
                } else {
                    startVpnTunnel(profileId)
                }
            }
            ACTION_STOP -> {
                stopVpnTunnel()
            }
        }
        return START_NOT_STICKY
    }

    private fun startVpnTunnel(profileId: String?) {
        tunnelJob?.cancel()
        tunnelJob = serviceScope.launch {
            vpnMutex.withLock {
                if (vpnRepository.connectionState.value is ConnectionState.Connected && tunInterface != null) {
                    android.util.Log.i("ProxyVpnService", "startVpnTunnel: already connected with active TUN, skipping")
                    return@withLock
                }

                try {
                    vpnRepository.updateState(ConnectionState.Connecting)
                    startForegroundNotification("Connecting…", "Initializing tunnel")

                    val activeProfile = if (profileId != null) {
                        profileRepository.getProfileById(profileId)
                    } else {
                        val savedId = settingsRepository.selectedProfileId.first()
                        savedId?.let { profileRepository.getProfileById(it) } ?: profileRepository.allProfiles.first().firstOrNull()
                    }

                    if (activeProfile == null) {
                        android.util.Log.w("ProxyVpnService", "No proxy profiles available to start tunnel")
                        vpnRepository.updateState(ConnectionState.Error("No server selected. Please add or select a server first."))
                        stopSelf()
                        return@withLock
                    }

                    currentProfileId = activeProfile.id
                    currentProfileName = activeProfile.name
                    connectionStartTime = System.currentTimeMillis()

                    val customRules = routingRepository.allRules.first()
                    val routingMode = settingsRepository.routingMode.first()
                    val targetPackages = settingsRepository.targetPackages.first()
                    val remoteDns = settingsRepository.remoteDnsUrl.first()
                    val fakeDns = settingsRepository.fakeDnsEnabled.first()
                    val killSwitch = settingsRepository.killSwitchEnabled.first()
                    val ipv6 = settingsRepository.ipv6Enabled.first()
                    val blockQuic = settingsRepository.blockQuic.first()

                    cachedRoutingMode = routingMode
                    cachedTargetPackages = targetPackages

                    val powerProfileId = settingsRepository.powerProfile.first()
                    val profileEnum = PowerProfile.fromId(powerProfileId)

                    val configJson = SingBoxConfigBuilder.build(
                        SingBoxConfigBuilder.BuildConfig(
                            activeProfile = activeProfile,
                            customRules = customRules,
                            routingMode = routingMode,
                            targetPackages = targetPackages,
                            remoteDnsUrl = remoteDns,
                            fakeDnsEnabled = fakeDns,
                            killSwitchEnabled = killSwitch,
                            localPortMixedInbound = 2080,
                            ipv6Enabled = ipv6,
                            blockQuic = blockQuic,
                            logLevel = profileEnum.coreLogLevel,
                            tunStack = profileEnum.tunStack,
                            endpointIndependentNat = profileEnum.endpointIndependentNat
                        )
                    )
                    // Dump sanitized config to debug file
                    dumpSanitizedConfig(configJson)
                    android.util.Log.i("ProxyVpnService", "Starting sing-box with activeProfile=${activeProfile.name}, logLevel=${profileEnum.coreLogLevel}, config length=${configJson.length}")

                    // Hand-off: start core with this as TunProvider
                    proxyCore.start(configJson, this@ProxyVpnService)
                    proxyCore.setScreenInteractive(isScreenInteractive)

                    // Cancel previous collectors if any
                    stateCollectorJob?.cancel()
                    statsCollectorJob?.cancel()
                    statsCollectorJob = null

                    stateCollectorJob = launch {
                        proxyCore.state.collect { state ->
                            when (state) {
                                is ConnectionState.Connected -> {
                                    val name = currentProfileName ?: activeProfile.name
                                    val startT = if (connectionStartTime > 0L) connectionStartTime else System.currentTimeMillis()
                                    vpnRepository.updateState(
                                        ConnectionState.Connected(
                                            profileName = name,
                                            connectedAt = startT
                                        )
                                    )
                                    startForegroundNotification(
                                        title = "Connected: $name",
                                        content = "Protected via Nebula"
                                    )
                                    launchConnectivityProbe(name, startT)
                                }
                                is ConnectionState.Error -> {
                                    vpnRepository.updateState(state)
                                    stopVpnTunnel()
                                }
                                is ConnectionState.Disconnected -> {
                                    vpnRepository.updateState(state)
                                    stopVpnTunnel()
                                }
                                else -> {
                                    vpnRepository.updateState(state)
                                }
                            }
                        }
                    }

                    if (isScreenInteractive) {
                        startTrafficTicker()
                    }

                } catch (e: Exception) {
                    android.util.Log.e("ProxyVpnService", "startVpnTunnel error: ${e.message}", e)
                    vpnRepository.updateState(ConnectionState.Error(e.message ?: "Failed to start VPN"))
                    stopVpnTunnelInternal()
                }
            }
        }
    }

    private fun switchVpnProfile(profileId: String?) {
        serviceScope.launch {
            vpnMutex.withLock {
                try {
                    val targetProfile = if (profileId != null) {
                        profileRepository.getProfileById(profileId)
                    } else {
                        val savedId = settingsRepository.selectedProfileId.first()
                        savedId?.let { profileRepository.getProfileById(it) }
                    } ?: return@withLock

                    if (targetProfile.id == currentProfileId && vpnRepository.connectionState.value is ConnectionState.Connected) {
                        android.util.Log.d("ProxyVpnService", "Profile is already active: ${targetProfile.name}")
                        return@withLock
                    }

                    android.util.Log.i("ProxyVpnService", "Hot-switching to profile: ${targetProfile.name} (${targetProfile.id})")
                    currentProfileId = targetProfile.id
                    currentProfileName = targetProfile.name
                    activeProfileName = targetProfile.name
                    settingsRepository.setSelectedProfileId(targetProfile.id)

                    val customRules = routingRepository.allRules.first()
                    val routingMode = settingsRepository.routingMode.first()
                    val targetPackages = settingsRepository.targetPackages.first()
                    val remoteDns = settingsRepository.remoteDnsUrl.first()
                    val fakeDns = settingsRepository.fakeDnsEnabled.first()
                    val killSwitch = settingsRepository.killSwitchEnabled.first()
                    val ipv6 = settingsRepository.ipv6Enabled.first()
                    val blockQuic = settingsRepository.blockQuic.first()
                    val powerProfileId = settingsRepository.powerProfile.first()
                    val profileEnum = PowerProfile.fromId(powerProfileId)

                    val configJson = SingBoxConfigBuilder.build(
                        SingBoxConfigBuilder.BuildConfig(
                            activeProfile = targetProfile,
                            customRules = customRules,
                            routingMode = routingMode,
                            targetPackages = targetPackages,
                            remoteDnsUrl = remoteDns,
                            fakeDnsEnabled = fakeDns,
                            killSwitchEnabled = killSwitch,
                            localPortMixedInbound = 2080,
                            ipv6Enabled = ipv6,
                            blockQuic = blockQuic,
                            logLevel = profileEnum.coreLogLevel,
                            tunStack = profileEnum.tunStack,
                            endpointIndependentNat = profileEnum.endpointIndependentNat
                        )
                    )
                    dumpSanitizedConfig(configJson)
                    proxyCore.reloadConfig(configJson)

                    // Update UI and notification quietly with no disconnect flicker
                    val startT = if (connectionStartTime > 0L) connectionStartTime else System.currentTimeMillis()
                    vpnRepository.updateState(
                        ConnectionState.Connected(
                            profileName = targetProfile.name,
                            connectedAt = startT
                        )
                    )
                    startForegroundNotification(
                        title = "Connected: ${targetProfile.name}",
                        content = "Protected via Nebula"
                    )
                    launchConnectivityProbe(targetProfile.name, startT)
                    android.util.Log.i("ProxyVpnService", "Hot-switch completed successfully to ${targetProfile.name}")

                } catch (e: Exception) {
                    android.util.Log.e("ProxyVpnService", "switchVpnProfile error: ${e.message}", e)
                }
            }
        }
    }

    private fun stopVpnTunnel() {
        tunnelJob?.cancel()
        tunnelJob = null
        teardownScope.launch {
            try {
                kotlinx.coroutines.withTimeout(1500L) {
                    vpnMutex.withLock {
                        stopVpnTunnelInternal()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("ProxyVpnService", "vpnMutex acquisition timed out during stop, forcing teardown: ${e.message}")
                stopVpnTunnelInternal()
            }
        }
    }

    private suspend fun stopVpnTunnelInternal() {
        android.util.Log.i("ProxyVpnService", "Stopping VPN tunnel internally...")
        probeJob?.cancel()
        probeJob = null
        stateCollectorJob?.cancel()
        stateCollectorJob = null
        statsCollectorJob?.cancel()
        statsCollectorJob = null
        trafficTickerJob?.cancel()
        trafficTickerJob = null

        vpnRepository.updateState(ConnectionState.Disconnecting)

        // 1. Immediately close TUN file descriptors so Linux kernel instantly removes tun0 and releases OS socket binds
        try {
            tunInterface?.close()
            android.util.Log.i("ProxyVpnService", "tunInterface closed successfully")
        } catch (e: Exception) {
            android.util.Log.e("ProxyVpnService", "Error closing tunInterface: ${e.message}", e)
        }
        tunInterface = null

        if (tunNativeFd > 0) {
            try {
                ParcelFileDescriptor.adoptFd(tunNativeFd).close()
            } catch (ignored: Exception) {}
            tunNativeFd = -1
        }

        // 2. Tear down foreground service and notification immediately so system status bar key icon clears in <200ms
        stopForeground(STOP_FOREGROUND_REMOVE)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        nm?.cancel(NOTIFICATION_ID)

        // 3. Stop proxyCore so libbox stops forwarding and frees Go runtime memory
        try {
            kotlinx.coroutines.withTimeoutOrNull(1000L) {
                proxyCore.stop()
            }
        } catch (e: Exception) {
            android.util.Log.e("ProxyVpnService", "proxyCore.stop error: ${e.message}", e)
        }

        currentProfileId = null
        currentProfileName = null
        isRunning = false
        activeProfileName = null
        connectionStartTime = 0L

        vpnRepository.updateState(ConnectionState.Disconnected)
        stopSelf()
        android.util.Log.i("ProxyVpnService", "VPN tunnel stopped cleanly, key icon cleared")
    }

    /**
     * TunProvider implementation called by libbox's PlatformInterface.
     * The VpnService.Builder is created here using libbox's TunOptions plus user split-tunneling.
     */
    override fun openTun(options: TunOptions): Int {
        val builder = Builder()
        builder.setSession("Nebula")

        val mtu = options.mtu
        if (mtu > 0) {
            builder.setMtu(mtu)
        }

        // Add IPv4 addresses
        var hasIpv4Address = false
        val inet4Addresses = options.inet4Address
        if (inet4Addresses != null) {
            while (inet4Addresses.hasNext()) {
                val prefix = inet4Addresses.next()
                builder.addAddress(prefix.address(), prefix.prefix())
                hasIpv4Address = true
            }
        }
        if (!hasIpv4Address) {
            builder.addAddress("172.19.0.1", 30)
        }

        // Add IPv6 addresses if configured
        var hasIpv6Address = false
        val inet6Addresses = options.inet6Address
        if (inet6Addresses != null) {
            while (inet6Addresses.hasNext()) {
                val prefix = inet6Addresses.next()
                builder.addAddress(prefix.address(), prefix.prefix())
                hasIpv6Address = true
            }
        }

        // Add Routes if autoRoute is enabled
        if (options.autoRoute) {
            val inet4Routes = options.inet4RouteAddress
            var hasIpv4Route = false
            if (inet4Routes != null) {
                while (inet4Routes.hasNext()) {
                    val prefix = inet4Routes.next()
                    builder.addRoute(prefix.address(), prefix.prefix())
                    hasIpv4Route = true
                }
            }
            if (!hasIpv4Route) {
                builder.addRoute("0.0.0.0", 0)
            }

            // Only add IPv6 routes if an IPv6 address was configured (default OFF for v2rayNG parity)
            if (hasIpv6Address) {
                val inet6Routes = options.inet6RouteAddress
                var hasIpv6Route = false
                if (inet6Routes != null) {
                    while (inet6Routes.hasNext()) {
                        val prefix = inet6Routes.next()
                        builder.addRoute(prefix.address(), prefix.prefix())
                        hasIpv6Route = true
                    }
                }
                if (!hasIpv6Route) {
                    builder.addRoute("::", 0)
                }
            }
        }

        // Add DNS Servers - public reliable DNS servers with DoH detour through proxy (INCY parity: never add Russian DNS to avoid poisoning)
        builder.addDnsServer("1.1.1.1")
        builder.addDnsServer("8.8.8.8")
        try {
            val dnsServers = options.dnsServerAddress
            if (dnsServers != null) {
                while (dnsServers.hasNext()) {
                    val dns = dnsServers.next()
                    if (dns.isNotBlank() && dns != "1.1.1.1" && dns != "8.8.8.8" && dns != "77.88.8.8") {
                        builder.addDnsServer(dns)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("ProxyVpnService", "Error reading dnsServerAddress: ${e.message}")
        }

        // Apply Split Tunneling synchronously before establish()
        SplitTunnelApplier.apply(
            builder = builder,
            packageManager = packageManager,
            ownPackageName = packageName,
            mode = cachedRoutingMode,
            targetPackages = cachedTargetPackages
        )

        val pfd = builder.establish()
        if (pfd == null) {
            android.util.Log.e("ProxyVpnService", "VpnService.Builder.establish() returned null! (Is VPN prepared?)")
            throw IllegalStateException("VpnService.Builder.establish() returned null")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            setUnderlyingNetworks(null)
        }
        tunInterface = pfd
        val nativeFd = pfd.dup().detachFd()
        android.util.Log.i("ProxyVpnService", "TUN established successfully, native fd=$nativeFd, pfd=$pfd")
        tunNativeFd = nativeFd
        isRunning = true
        activeProfileName = currentProfileName
        return nativeFd
    }

    override fun protect(fd: Int): Boolean {
        val res = super.protect(fd)
        android.util.Log.d("ProxyVpnService", "Protected socket fd=$fd: $res")
        return res
    }

    override fun onRevoke() {
        vpnRepository.onVpnRevoked()
        stopVpnTunnel()
    }

    override fun onDestroy() {
        if (activeInstance === this) {
            activeInstance = null
        }
        super.onDestroy()
        screenReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {}
            screenReceiver = null
        }
        serviceScope.cancel()
        tunnelJob?.cancel()
        tunnelJob = null
        try {
            tunInterface?.close()
        } catch (ignored: Exception) {}
        tunInterface = null

        if (tunNativeFd > 0) {
            try {
                ParcelFileDescriptor.adoptFd(tunNativeFd).close()
            } catch (ignored: Exception) {}
            tunNativeFd = -1
        }

        teardownScope.launch {
            try {
                kotlinx.coroutines.withTimeoutOrNull(1000L) {
                    proxyCore.stop()
                }
            } catch (ignored: Exception) {}
        }
        isRunning = false
        activeProfileName = null
    }

    private fun startForegroundNotification(title: String, content: String) {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val disconnectIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ProxyVpnService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NebulaApp.VPN_NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .addAction(0, "Disconnect", disconnectIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setUsesChronometer(true)
            .setShowWhen(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun launchConnectivityProbe(profileName: String, startT: Long) {
        probeJob?.cancel()
        probeJob = serviceScope.launch(Dispatchers.IO) {
            // Initial warm-up for TLS / WebSocket handshake to settle
            kotlinx.coroutines.delay(1800)
            if (vpnRepository.connectionState.value !is ConnectionState.Connected) return@launch

            var ok = probeProxyConnectivity()
            if (!ok) {
                // Retry once after brief pause to tolerate initial mobile packet jitter
                kotlinx.coroutines.delay(1500)
                if (vpnRepository.connectionState.value !is ConnectionState.Connected) return@launch
                ok = probeProxyConnectivity()
            }
            if (vpnRepository.connectionState.value !is ConnectionState.Connected) return@launch

            if (!ok) {
                android.util.Log.w("ProxyVpnService", "Active probe failed: node $profileName dropped test packets")
                vpnRepository.updateState(
                    ConnectionState.Connected(
                        profileName = profileName,
                        connectedAt = startT,
                        isDegraded = true,
                        warning = "No internet access through proxy"
                    )
                )
                startForegroundNotification(
                    title = "Connected: $profileName (No Internet)",
                    content = "Proxy is not routing data. Open Doctor to diagnose."
                )
            } else {
                android.util.Log.i("ProxyVpnService", "Active probe succeeded: node $profileName verified healthy")
                vpnRepository.updateState(
                    ConnectionState.Connected(
                        profileName = profileName,
                        connectedAt = startT,
                        isDegraded = false,
                        warning = null
                    )
                )
                startForegroundNotification(
                    title = "Connected: $profileName",
                    content = "Protected via Nebula"
                )
            }
        }
    }

    private fun probeProxyConnectivity(): Boolean {
        val proxy = java.net.Proxy(java.net.Proxy.Type.HTTP, java.net.InetSocketAddress("127.0.0.1", 2080))
        val testUrls = listOf(
            "http://cp.cloudflare.com/generate_204",
            "http://connectivitycheck.gstatic.com/generate_204",
            "http://1.1.1.1/generate_204",
            "http://www.google.com/generate_204"
        )
        for (urlStr in testUrls) {
            try {
                val url = java.net.URL(urlStr)
                val conn = url.openConnection(proxy) as java.net.HttpURLConnection
                conn.connectTimeout = 3500
                conn.readTimeout = 3500
                conn.instanceFollowRedirects = false
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile; Nebula-VPN)")
                try {
                    val code = conn.responseCode
                    if (code in 200..399) return true
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                android.util.Log.d("ProxyVpnService", "Probe url $urlStr failed via proxy: ${e.message}")
            }
        }
        return false
    }

    private var lastNotifTitle: String? = null
    private var lastNotifContent: String? = null
    private var cachedPendingIntent: PendingIntent? = null
    private var cachedDisconnectIntent: PendingIntent? = null

    private fun startTrafficTicker() {
        trafficTickerJob?.cancel()
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val interactive = powerManager?.isInteractive ?: isScreenInteractive
        if (!interactive) return // Screen locked: zero background tickers

        lastUidRxBytes = android.net.TrafficStats.getUidRxBytes(android.os.Process.myUid())
        lastUidTxBytes = android.net.TrafficStats.getUidTxBytes(android.os.Process.myUid())
        lastTrafficSampleTimeMs = System.currentTimeMillis()
        accumulatedRxBytes = 0L
        accumulatedTxBytes = 0L

        trafficTickerJob = serviceScope.launch(Dispatchers.IO) {
            var lastNotifUpdateTime = 0L

            while (isActive) {
                val profileEnum = cachedPowerProfile
                val showSpeed = cachedShowSpeed
                val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
                val isCurrentInteractive = pm?.isInteractive ?: isScreenInteractive

                if (!isCurrentInteractive) {
                    // Screen locked: exit ticker loop cleanly to conserve 100% CPU
                    break
                }

                kotlinx.coroutines.delay(profileEnum.interactiveIntervalMs)
                if (!isActive) break

                val now = System.currentTimeMillis()
                val deltaSec = ((now - lastTrafficSampleTimeMs).coerceAtLeast(500L)) / 1000.0
                lastTrafficSampleTimeMs = now

                val currentUidRx = android.net.TrafficStats.getUidRxBytes(android.os.Process.myUid())
                val currentUidTx = android.net.TrafficStats.getUidTxBytes(android.os.Process.myUid())

                var deltaRx = 0L
                var deltaTx = 0L

                if (lastUidRxBytes >= 0 && currentUidRx >= lastUidRxBytes) {
                    deltaRx = currentUidRx - lastUidRxBytes
                }
                if (lastUidTxBytes >= 0 && currentUidTx >= lastUidTxBytes) {
                    deltaTx = currentUidTx - lastUidTxBytes
                }

                lastUidRxBytes = currentUidRx
                lastUidTxBytes = currentUidTx

                accumulatedRxBytes += deltaRx
                accumulatedTxBytes += deltaTx

                val rxSpeedBps = if (deltaSec > 0) (deltaRx / deltaSec).toLong() else 0L
                val txSpeedBps = if (deltaSec > 0) (deltaTx / deltaSec).toLong() else 0L

                val coreStats = proxyCore.stats.value
                val finalRxSpeed = maxOf(rxSpeedBps, coreStats.downloadSpeedBps)
                val finalTxSpeed = maxOf(txSpeedBps, coreStats.uploadSpeedBps)
                val finalTotalRx = maxOf(accumulatedRxBytes, coreStats.totalDownloadBytes)
                val finalTotalTx = maxOf(accumulatedTxBytes, coreStats.totalUploadBytes)

                val stats = TrafficStats(
                    uploadSpeedBps = if (isCurrentInteractive) finalTxSpeed else 0L,
                    downloadSpeedBps = if (isCurrentInteractive) finalRxSpeed else 0L,
                    totalUploadBytes = finalTotalTx,
                    totalDownloadBytes = finalTotalRx,
                    memoryUsageBytes = coreStats.memoryUsageBytes,
                    activeConnections = coreStats.activeConnections
                )

                vpnRepository.updateStats(stats)

                // Update notification ONLY when screen is interactive AND user enabled speed in notification.
                // When screen is off, zero notification IPC occurs. Throttled to >= 2500ms to eliminate Binder storms.
                if (isCurrentInteractive && showSpeed && currentProfileName != null && vpnRepository.connectionState.value is ConnectionState.Connected) {
                    val minInterval = maxOf(2500L, profileEnum.interactiveIntervalMs)
                    if (now - lastNotifUpdateTime >= minInterval) {
                        lastNotifUpdateTime = now
                        val notifTitle = "Connected: $currentProfileName"
                        val notifContent = "⬇ ${formatSpeed(finalRxSpeed)}   ⬆ ${formatSpeed(finalTxSpeed)}"
                        updateForegroundNotification(notifTitle, notifContent)
                    }
                }
            }
        }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0) return "0 KB/s"
        val kb = bytesPerSec / 1024.0
        if (kb < 1000) {
            return String.format(java.util.Locale.US, "%.1f KB/s", kb)
        }
        val mb = kb / 1024.0
        return String.format(java.util.Locale.US, "%.1f MB/s", mb)
    }

    private fun updateForegroundNotification(title: String, content: String) {
        if (title == lastNotifTitle && content == lastNotifContent) {
            return // Skip IPC if content has not changed
        }
        lastNotifTitle = title
        lastNotifContent = content

        try {
            val pendingIntent = cachedPendingIntent ?: PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            ).also { cachedPendingIntent = it }

            val disconnectIntent = cachedDisconnectIntent ?: PendingIntent.getService(
                this,
                1,
                Intent(this, ProxyVpnService::class.java).apply { action = ACTION_STOP },
                PendingIntent.FLAG_IMMUTABLE
            ).also { cachedDisconnectIntent = it }

            val notification = NotificationCompat.Builder(this, NebulaApp.VPN_NOTIFICATION_CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .addAction(0, "Disconnect", disconnectIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setUsesChronometer(true)
                .setShowWhen(true)
                .build()

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            android.util.Log.w("ProxyVpnService", "Failed to update notification: ${e.message}")
        }
    }

    private fun dumpSanitizedConfig(configJson: String) {
        if (!app.nebula.vpn.core.OptimizationFlags.dumpConfigOnStart) return
        try {
            val sanitized = configJson
                .replace(Regex("\"uuid\"\\s*:\\s*\"[^\"]+\""), "\"uuid\": \"[REDACTED]\"")
                .replace(Regex("\"password\"\\s*:\\s*\"[^\"]+\""), "\"password\": \"[REDACTED]\"")
                .replace(Regex("\"public_key\"\\s*:\\s*\"[^\"]+\""), "\"public_key\": \"[REDACTED]\"")
            val targetFile = java.io.File(filesDir, "last_generated_config.json")
            targetFile.writeText(sanitized)
            android.util.Log.d("ProxyVpnService", "Dumped sanitized sing-box config (${sanitized.length} chars) to ${targetFile.name}")
        } catch (e: Exception) {
            android.util.Log.w("ProxyVpnService", "Failed to dump sanitized config: ${e.message}")
        }
    }
}
