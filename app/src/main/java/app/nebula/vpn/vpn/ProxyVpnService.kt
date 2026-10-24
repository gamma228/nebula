package app.nebula.vpn.vpn

import android.app.Notification
import android.app.PendingIntent
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ProxyVpnService : VpnService(), TunProvider {

    companion object {
        const val ACTION_START = "app.nebula.vpn.action.START"
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
    private var tunInterface: ParcelFileDescriptor? = null
    private var cachedRoutingMode: app.nebula.vpn.core.model.AppRoutingMode = app.nebula.vpn.core.model.AppRoutingMode.GLOBAL
    private var cachedTargetPackages: Set<String> = emptySet()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val profileId = intent.getStringExtra(EXTRA_PROFILE_ID)
                startVpnTunnel(profileId)
            }
            ACTION_STOP -> {
                stopVpnTunnel()
            }
        }
        return START_NOT_STICKY
    }

    private fun startVpnTunnel(profileId: String?) {
        serviceScope.launch {
            try {
                vpnRepository.updateState(ConnectionState.Connecting)
                startForegroundNotification("Connecting…", "Initializing tunnel")

                val activeProfile = if (profileId != null) {
                    profileRepository.getProfileById(profileId)
                } else {
                    val savedId = settingsRepository.selectedProfileId.first()
                    savedId?.let { profileRepository.getProfileById(it) }
                }

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
                android.util.Log.i("ProxyVpnService", "Starting sing-box with activeProfile=${activeProfile?.name}, config length=${configJson.length}")

                // Hand-off: start core with this as TunProvider
                proxyCore.start(configJson, this@ProxyVpnService)

                // Observe core state and speeds
                launch {
                    proxyCore.state.collect { state ->
                        vpnRepository.updateState(state)
                        when (state) {
                            is ConnectionState.Connected -> {
                                startForegroundNotification(
                                    title = "Connected: ${activeProfile?.name ?: "Nebula"}",
                                    content = "Protected via sing-box core"
                                )
                            }
                            is ConnectionState.Error -> {
                                stopVpnTunnel()
                            }
                            is ConnectionState.Disconnected -> {
                                stopSelf()
                            }
                            else -> {}
                        }
                    }
                }

                launch {
                    proxyCore.stats.collect { stats ->
                        vpnRepository.updateStats(stats)
                    }
                }

            } catch (e: Exception) {
                android.util.Log.e("ProxyVpnService", "startVpnTunnel error: ${e.message}", e)
                vpnRepository.updateState(ConnectionState.Error(e.message ?: "Failed to start VPN"))
                stopVpnTunnel()
            }
        }
    }

    private fun stopVpnTunnel() {
        serviceScope.launch {
            vpnRepository.updateState(ConnectionState.Disconnecting)
            try {
                proxyCore.stop()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                tunInterface?.close()
                tunInterface = null
            } catch (e: Exception) {
                e.printStackTrace()
            }
            vpnRepository.updateState(ConnectionState.Disconnected)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
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
        val inet4Addresses = options.inet4Address
        if (inet4Addresses != null) {
            while (inet4Addresses.hasNext()) {
                val prefix = inet4Addresses.next()
                builder.addAddress(prefix.address(), prefix.prefix())
            }
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
            if (inet4Routes != null) {
                while (inet4Routes.hasNext()) {
                    val prefix = inet4Routes.next()
                    builder.addRoute(prefix.address(), prefix.prefix())
                }
            } else {
                builder.addRoute("0.0.0.0", 0)
            }

            val inet6Routes = options.inet6RouteAddress
            if (inet6Routes != null) {
                while (inet6Routes.hasNext()) {
                    val prefix = inet6Routes.next()
                    builder.addRoute(prefix.address(), prefix.prefix())
                }
            } else {
                builder.addRoute("::", 0)
            }
        }

        // Add DNS Servers
        try {
            val dnsServers = options.dnsServerAddress
            if (dnsServers != null) {
                while (dnsServers.hasNext()) {
                    val dns = dnsServers.next()
                    builder.addDnsServer(dns)
                }
            } else {
                builder.addDnsServer("172.19.0.2")
            }
        } catch (e: Exception) {
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
        android.util.Log.i("ProxyVpnService", "TUN established successfully, native fd=${pfd.fd}")
        tunInterface = pfd
        return pfd.detachFd()
    }

    override fun protect(fd: Int): Boolean {
        val res = super.protect(fd)
        android.util.Log.d("ProxyVpnService", "Protected socket fd=$fd: $res")
        return res
    }

    override fun onRevoke() {
        stopVpnTunnel()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
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
