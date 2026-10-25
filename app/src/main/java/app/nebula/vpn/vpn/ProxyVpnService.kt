package app.nebula.vpn.vpn

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import app.nebula.vpn.NebulaApp
import app.nebula.vpn.R
import app.nebula.vpn.core.builder.SingBoxConfigBuilder
import app.nebula.vpn.core.model.ConnectionState
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val profileId = intent?.getStringExtra(EXTRA_PROFILE_ID)
        android.util.Log.i("ProxyVpnService", "onStartCommand: action=$action, profileId=$profileId, currentState=${vpnRepository.connectionState.value}")

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

                    val allProfiles = profileRepository.allProfiles.first()
                    val customRules = routingRepository.allRules.first()
                    val routingMode = settingsRepository.routingMode.first()
                    val targetPackages = settingsRepository.targetPackages.first()
                    val remoteDns = settingsRepository.remoteDnsUrl.first()
                    val fakeDns = settingsRepository.fakeDnsEnabled.first()
                    val killSwitch = settingsRepository.killSwitchEnabled.first()

                    cachedRoutingMode = routingMode
                    cachedTargetPackages = targetPackages

                    val configJson = SingBoxConfigBuilder.build(
                        SingBoxConfigBuilder.BuildConfig(
                            activeProfile = activeProfile,
                            allProfiles = allProfiles,
                            customRules = customRules,
                            routingMode = routingMode,
                            targetPackages = targetPackages,
                            remoteDnsUrl = remoteDns,
                            fakeDnsEnabled = fakeDns,
                            killSwitchEnabled = killSwitch
                        )
                    )
                    android.util.Log.i("ProxyVpnService", "Starting sing-box with activeProfile=${activeProfile.name}, config length=${configJson.length}")

                    // Hand-off: start core with this as TunProvider
                    proxyCore.start(configJson, this@ProxyVpnService)

                    // Cancel previous collectors if any
                    stateCollectorJob?.cancel()
                    statsCollectorJob?.cancel()

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
                                        content = "Protected via sing-box core"
                                    )
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

                    statsCollectorJob = launch {
                        proxyCore.stats.collect { stats ->
                            vpnRepository.updateStats(stats)
                        }
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
                    settingsRepository.setSelectedProfileId(targetProfile.id)

                    val groupTag = "select"
                    val outboundTag = "proxy-${targetProfile.id}"
                    val switched = proxyCore.selectOutbound(groupTag, outboundTag)

                    if (!switched) {
                        android.util.Log.i("ProxyVpnService", "selectOutbound returned false, reloading full config in-place")
                        val allProfiles = profileRepository.allProfiles.first()
                        val customRules = routingRepository.allRules.first()
                        val routingMode = settingsRepository.routingMode.first()
                        val targetPackages = settingsRepository.targetPackages.first()
                        val remoteDns = settingsRepository.remoteDnsUrl.first()
                        val fakeDns = settingsRepository.fakeDnsEnabled.first()
                        val killSwitch = settingsRepository.killSwitchEnabled.first()

                        val configJson = SingBoxConfigBuilder.build(
                            SingBoxConfigBuilder.BuildConfig(
                                activeProfile = targetProfile,
                                allProfiles = allProfiles,
                                customRules = customRules,
                                routingMode = routingMode,
                                targetPackages = targetPackages,
                                remoteDnsUrl = remoteDns,
                                fakeDnsEnabled = fakeDns,
                                killSwitchEnabled = killSwitch
                            )
                        )
                        proxyCore.reloadConfig(configJson)
                    }

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
                        content = "Protected via sing-box core"
                    )
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
                kotlinx.coroutines.withTimeout(5000L) {
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
        stateCollectorJob?.cancel()
        stateCollectorJob = null
        statsCollectorJob?.cancel()
        statsCollectorJob = null

        vpnRepository.updateState(ConnectionState.Disconnecting)

        // 1. First stop proxyCore so libbox stops forwarding and unblocks native Go read loops
        try {
            kotlinx.coroutines.withTimeoutOrNull(2000L) {
                proxyCore.stop()
            }
        } catch (e: Exception) {
            android.util.Log.e("ProxyVpnService", "proxyCore.stop error: ${e.message}", e)
        }

        // 2. Close Java TUN file descriptor so Linux kernel immediately tears down tun0
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

        // 3. Tear down foreground service and notification immediately so user sees status bar icon clear
        stopForeground(STOP_FOREGROUND_REMOVE)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        nm?.cancel(NOTIFICATION_ID)

        currentProfileId = null
        currentProfileName = null
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

        // Add IPv6 addresses
        val inet6Addresses = options.inet6Address
        if (inet6Addresses != null) {
            while (inet6Addresses.hasNext()) {
                val prefix = inet6Addresses.next()
                builder.addAddress(prefix.address(), prefix.prefix())
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

        // Add DNS Servers
        var hasDns = false
        try {
            val dnsServers = options.dnsServerAddress
            if (dnsServers != null) {
                while (dnsServers.hasNext()) {
                    val dns = dnsServers.next()
                    if (dns.isNotBlank()) {
                        builder.addDnsServer(dns)
                        hasDns = true
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("ProxyVpnService", "Error reading dnsServerAddress: ${e.message}")
        }
        if (!hasDns) {
            builder.addDnsServer("172.19.0.2")
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
        super.onDestroy()
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

        kotlinx.coroutines.runBlocking(Dispatchers.IO) {
            try {
                kotlinx.coroutines.withTimeoutOrNull(1000L) {
                    proxyCore.stop()
                }
            } catch (ignored: Exception) {}
        }
        teardownScope.cancel()
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
            .setPriority(NotificationCompat.PRIORITY_LOW)
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
}
