package app.nebula.vpn.core.proxy

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import app.nebula.vpn.core.model.ConnectionState
import app.nebula.vpn.core.model.TrafficStats
import io.nekohasekai.libbox.BridgeOptions
import io.nekohasekai.libbox.BridgeSession
import io.nekohasekai.libbox.CommandClient
import io.nekohasekai.libbox.CommandClientHandler
import io.nekohasekai.libbox.CommandClientOptions
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.ConnectionEvents
import io.nekohasekai.libbox.ConnectionOwner
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.LocalDNSTransport
import io.nekohasekai.libbox.LogIterator
import io.nekohasekai.libbox.NeighborUpdateListener
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.Notification
import io.nekohasekai.libbox.OutboundGroupItemIterator
import io.nekohasekai.libbox.OutboundGroupIterator
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.PlatformUser
import io.nekohasekai.libbox.SetupOptions
import io.nekohasekai.libbox.ShellSession
import io.nekohasekai.libbox.StatusMessage
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.SystemProxyStatus
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.libbox.WIFIState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetSocketAddress

class LibboxCore(
    private val context: Context
) : ProxyCore {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _stats = MutableStateFlow(TrafficStats())
    override val stats: StateFlow<TrafficStats> = _stats.asStateFlow()

    private var commandServer: CommandServer? = null
    private var commandClient: CommandClient? = null
    private var activeTunProvider: TunProvider? = null

    private val logBuffer = ArrayDeque<String>(2048)

    init {
        try {
            // Delete stale cache.db so Go runtime never restores stale selector or outbound state
            val filesDir = context.filesDir
            File(filesDir, "cache.db").delete()
            File(filesDir, "cache.db-shm").delete()
            File(filesDir, "cache.db-wal").delete()

            val setupOptions = SetupOptions().apply {
                basePath = context.filesDir.absolutePath
                workingPath = context.filesDir.absolutePath
                tempPath = context.cacheDir.absolutePath
                fixAndroidStack = true
                debug = false
            }
            Libbox.setup(setupOptions)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun start(configJson: String, tunProvider: TunProvider): Unit = withContext(Dispatchers.IO) {
        try {
            val existingServer = commandServer
            if (existingServer != null) {
                android.util.Log.i("LibboxCore", "CommandServer already running, hot reloading service...")
                reloadConfig(configJson)
                _state.value = ConnectionState.Connected(profileName = "Active Proxy")
                return@withContext
            }

            android.util.Log.i("LibboxCore", "Starting LibboxCommandServer...")
            _state.value = ConnectionState.Connecting
            activeTunProvider = tunProvider

            val platformInterface = createPlatformInterface(tunProvider)
            val serverHandler = createCommandServerHandler()

            val server = Libbox.newCommandServer(serverHandler, platformInterface)
            commandServer = server
            server.start()

            android.util.Log.i("LibboxCore", "CommandServer started, calling startOrReloadService with config length: ${configJson.length}")
            val overrideOptions = OverrideOptions()
            server.startOrReloadService(configJson, overrideOptions)

            initCommandClient()

            _state.value = ConnectionState.Connected(profileName = "Active Proxy")
            android.util.Log.i("LibboxCore", "LibboxCore connected successfully!")
        } catch (e: Exception) {
            android.util.Log.e("LibboxCore", "Failed to start LibboxCore: ${e.message}", e)
            _state.value = ConnectionState.Error(e.message ?: "Failed to start sing-box core")
            stop()
            throw e
        }
        Unit
    }

    override suspend fun reloadConfig(configJson: String): Unit = withContext(Dispatchers.IO) {
        val server = commandServer
        if (server != null) {
            try {
                android.util.Log.i("LibboxCore", "reloadConfig: length=${configJson.length}")
                val overrideOptions = OverrideOptions()
                server.startOrReloadService(configJson, overrideOptions)
                android.util.Log.i("LibboxCore", "reloadConfig completed successfully!")
            } catch (e: Exception) {
                android.util.Log.e("LibboxCore", "reloadConfig error: ${e.message}", e)
                throw e
            }
        } else {
            val tun = activeTunProvider ?: return@withContext
            start(configJson, tun)
        }
    }

    override suspend fun stop(): Unit = withContext(Dispatchers.IO) {
        try {
            _state.value = ConnectionState.Disconnecting
            try {
                commandClient?.disconnect()
            } catch (e: Exception) {
                android.util.Log.w("LibboxCore", "commandClient.disconnect error: ${e.message}")
            }
            commandClient = null

            try {
                commandServer?.closeService()
            } catch (e: Exception) {
                android.util.Log.w("LibboxCore", "commandServer.closeService error: ${e.message}")
            }

            try {
                commandServer?.close()
            } catch (e: Exception) {
                android.util.Log.w("LibboxCore", "commandServer.close error: ${e.message}")
            }
            commandServer = null
            activeTunProvider = null

            _state.value = ConnectionState.Disconnected
            _stats.value = TrafficStats()
            android.util.Log.i("LibboxCore", "LibboxCore stopped cleanly")
        } catch (e: Exception) {
            android.util.Log.e("LibboxCore", "LibboxCore.stop error: ${e.message}", e)
            _state.value = ConnectionState.Disconnected
        }
        Unit
    }

    override suspend fun urlTest(groupTag: String): Unit = withContext(Dispatchers.IO) {
        try {
            commandClient?.urlTest(groupTag)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        Unit
    }

    override fun setScreenInteractive(isInteractive: Boolean) {
        try {
            commandServer?.recordScreenState(isInteractive)
            android.util.Log.d("LibboxCore", "Notified commandServer of screen interactive: $isInteractive")
        } catch (e: Exception) {
            android.util.Log.w("LibboxCore", "recordScreenState failed: ${e.message}")
        }
    }

    override suspend fun selectOutbound(groupTag: String, outboundTag: String): Boolean = withContext(Dispatchers.IO) {
        val client = commandClient ?: return@withContext false
        try {
            android.util.Log.i("LibboxCore", "selectOutbound: group=$groupTag, outbound=$outboundTag")
            client.selectOutbound(groupTag, outboundTag)
            try {
                client.closeConnections()
                android.util.Log.i("LibboxCore", "closeConnections succeeded for clean switch")
            } catch (e: Exception) {
                android.util.Log.w("LibboxCore", "closeConnections warning: ${e.message}")
            }
            android.util.Log.i("LibboxCore", "selectOutbound succeeded!")
            true
        } catch (e: Exception) {
            android.util.Log.w("LibboxCore", "selectOutbound failed: ${e.message}")
            false
        }
    }

    private suspend fun initCommandClient() {
        try {
            val clientOptions = CommandClientOptions().apply {
                statusInterval = 0 // Disable background JNI status polling ticker in Go runtime
                addCommand(Libbox.CommandGroup) // Required for selector outbound management and hot-switching
            }

            val clientHandler = object : CommandClientHandler {
                override fun clearLogs() {
                    synchronized(logBuffer) { logBuffer.clear() }
                }

                override fun connected() {}

                override fun disconnected(message: String?) {}

                override fun initializeClashMode(modes: StringIterator?, currentMode: String?) {}

                override fun setDefaultLogLevel(level: Int) {}

                override fun updateClashMode(newMode: String?) {}

                override fun writeConnectionEvents(events: ConnectionEvents?) {}

                override fun writeGroups(groups: OutboundGroupIterator?) {}

                override fun writeLogs(logs: LogIterator?) {}

                override fun writeOutbounds(outbounds: OutboundGroupItemIterator?) {}

                override fun writeStatus(status: StatusMessage?) {
                    if (status != null) {
                        _stats.value = TrafficStats(
                            uploadSpeedBps = status.uplink,
                            downloadSpeedBps = status.downlink,
                            totalUploadBytes = status.uplinkTotal,
                            totalDownloadBytes = status.downlinkTotal,
                            memoryUsageBytes = status.memory,
                            activeConnections = status.connectionsIn + status.connectionsOut
                        )
                    }
                }
            }

            val client = Libbox.newCommandClient(clientHandler, clientOptions)
            commandClient = client
            var connected = false
            for (attempt in 1..3) {
                try {
                    client.connect()
                    connected = true
                    android.util.Log.i("LibboxCore", "CommandClient connected successfully on attempt $attempt")
                    break
                } catch (e: Exception) {
                    android.util.Log.w("LibboxCore", "CommandClient connect attempt $attempt failed: ${e.message}")
                    kotlinx.coroutines.delay(100L)
                }
            }
            if (!connected) {
                android.util.Log.e("LibboxCore", "Failed to connect CommandClient after 3 attempts")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private var defaultInterfaceMonitor: InterfaceUpdateListener? = null
    private var defaultNetworkCallback: ConnectivityManager.NetworkCallback? = null
    @Volatile private var lastReportedName: String? = null
    @Volatile private var lastReportedIndex: Int = -2
    @Volatile private var lastReportedExpensive: Boolean = false
    @Volatile private var lastReportedConstrained: Boolean = false

    private fun notifyDefaultInterface(listener: InterfaceUpdateListener) {
        try {
            var ifaceName: String? = null
            var isExpensive = false
            var isConstrained = false

            // 1. Check all physical networks that are not VPNs
            val allNetworks = connectivityManager.allNetworks
            var candidateIface: String? = null
            var candidateExpensive = false
            var candidateConstrained = false

            for (net in allNetworks) {
                val caps = connectivityManager.getNetworkCapabilities(net) ?: continue
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
                val isPhysical = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                if (!isPhysical) continue

                val lp = connectivityManager.getLinkProperties(net)
                val name = lp?.interfaceName
                if (!name.isNullOrBlank()) {
                    val expensive = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                    val constrained = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED)
                    } else false

                    if (candidateIface == null) {
                        candidateIface = name
                        candidateExpensive = expensive
                        candidateConstrained = constrained
                    }

                    // Prefer WiFi / Ethernet over cellular for stability
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                    ) {
                        ifaceName = name
                        isExpensive = expensive
                        isConstrained = constrained
                        break
                    } else if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                        ifaceName = name
                        isExpensive = expensive
                        isConstrained = constrained
                    }
                }
            }

            if (ifaceName == null) {
                ifaceName = candidateIface
                isExpensive = candidateExpensive
                isConstrained = candidateConstrained
            }

            // 2. Fallback to activeNetwork if not VPN
            if (ifaceName == null) {
                val activeNet = connectivityManager.activeNetwork
                if (activeNet != null) {
                    val caps = connectivityManager.getNetworkCapabilities(activeNet)
                    if (caps != null && !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                        val lp = connectivityManager.getLinkProperties(activeNet)
                        if (!lp?.interfaceName.isNullOrBlank()) {
                            ifaceName = lp?.interfaceName
                            isExpensive = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                            isConstrained = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED)
                            } else false
                        }
                    }
                }
            }

            // 3. Fallback to first active physical interface from java.net
            if (ifaceName == null) {
                val nifs = java.net.NetworkInterface.getNetworkInterfaces()
                while (nifs?.hasMoreElements() == true) {
                    val nif = nifs.nextElement()
                    if (!nif.isLoopback && nif.isUp && !nif.name.startsWith("tun") && !nif.name.startsWith("dummy")) {
                        ifaceName = nif.name
                        break
                    }
                }
            }

            val finalName = ifaceName ?: ""
            val index = if (finalName.isNotEmpty()) {
                try {
                    java.net.NetworkInterface.getByName(finalName)?.index ?: 0
                } catch (_: Exception) { 0 }
            } else {
                -1
            }

            // DEDUPLICATION: Return early if state has not changed to eliminate Go routing thrashing
            if (finalName == lastReportedName &&
                index == lastReportedIndex &&
                isExpensive == lastReportedExpensive &&
                isConstrained == lastReportedConstrained
            ) {
                return
            }

            lastReportedName = finalName
            lastReportedIndex = index
            lastReportedExpensive = isExpensive
            lastReportedConstrained = isConstrained

            android.util.Log.i("LibboxCore", "updateDefaultInterface changed: name='$finalName', index=$index, expensive=$isExpensive, constrained=$isConstrained")
            listener.updateDefaultInterface(finalName, index, isExpensive, isConstrained)
        } catch (e: Exception) {
            android.util.Log.e("LibboxCore", "Failed to notify default interface: ${e.message}", e)
        }
    }

    private fun createPlatformInterface(tunProvider: TunProvider): PlatformInterface {
        return object : PlatformInterface {
            override fun autoDetectInterfaceControl(fd: Int) {
                val protected = tunProvider.protect(fd)
                android.util.Log.d("LibboxCore", "autoDetectInterfaceControl: fd=$fd, protected=$protected")
            }

            override fun cancelNotification(tag: String?, id: Int) {}

            override fun checkPlatformShell() {}

            override fun clearDNSCache() {}

            override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener?) {
                defaultNetworkCallback?.let {
                    try {
                        connectivityManager.unregisterNetworkCallback(it)
                    } catch (_: Exception) {}
                    defaultNetworkCallback = null
                }
                defaultInterfaceMonitor = null
                lastReportedName = null
                lastReportedIndex = -2
            }

            override fun closeNeighborMonitor(listener: NeighborUpdateListener?) {}

            override fun createBridge(options: BridgeOptions?): BridgeSession? = null

            override fun findConnectionOwner(
                ipProtocol: Int,
                sourceAddress: String?,
                sourcePort: Int,
                destinationAddress: String?,
                destinationPort: Int
            ): ConnectionOwner {
                val owner = ConnectionOwner()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && sourceAddress != null && destinationAddress != null) {
                    try {
                        val local = InetSocketAddress(sourceAddress, sourcePort)
                        val remote = InetSocketAddress(destinationAddress, destinationPort)
                        val uid = connectivityManager.getConnectionOwnerUid(ipProtocol, local, remote)
                        owner.userId = uid
                    } catch (e: Exception) {
                        owner.userId = 0
                    }
                } else {
                    owner.userId = 0
                }
                return owner
            }

            override fun getInterfaces(): NetworkInterfaceIterator? {
                return try {
                    val nifs = java.net.NetworkInterface.getNetworkInterfaces() ?: return null
                    val list = mutableListOf<io.nekohasekai.libbox.NetworkInterface>()
                    for (nif in nifs.asSequence()) {
                        if (nif.isLoopback || !nif.isUp) continue
                        val libboxIface = io.nekohasekai.libbox.NetworkInterface().apply {
                            name = nif.name
                            index = nif.index
                            mtu = try { nif.mtu } catch (_: Exception) { 1500 }

                            val addrs = mutableListOf<String>()
                            for (addr in nif.interfaceAddresses) {
                                val ip = addr.address?.hostAddress ?: continue
                                val cleanIp = ip.substringBefore('%')
                                addrs.add("$cleanIp/${addr.networkPrefixLength}")
                            }
                            addresses = object : StringIterator {
                                private var i = 0
                                override fun hasNext(): Boolean = i < addrs.size
                                override fun len(): Int = addrs.size
                                override fun next(): String = addrs[i++]
                            }
                        }
                        list.add(libboxIface)
                    }
                    object : NetworkInterfaceIterator {
                        private var i = 0
                        override fun hasNext(): Boolean = i < list.size
                        override fun next(): io.nekohasekai.libbox.NetworkInterface = list[i++]
                    }
                } catch (e: Exception) {
                    android.util.Log.e("LibboxCore", "Error in getInterfaces: ${e.message}", e)
                    null
                }
            }

            override fun includeAllNetworks(): Boolean = false

            override fun localDNSTransport(): LocalDNSTransport {
                return object : LocalDNSTransport {
                    override fun raw(): Boolean = false

                    override fun exchange(ctx: io.nekohasekai.libbox.ExchangeContext?, message: ByteArray?) {
                        ctx?.errorCode(1)
                    }

                    override fun lookup(ctx: io.nekohasekai.libbox.ExchangeContext?, network: String?, domain: String?) {
                        if (ctx == null || domain.isNullOrBlank()) {
                            ctx?.errorCode(1)
                            return
                        }
                        try {
                            val addrs = java.net.InetAddress.getAllByName(domain)
                            if (addrs.isNullOrEmpty()) {
                                ctx.errorCode(1)
                            } else {
                                val cleanIp = addrs.first().hostAddress?.substringBefore('%') ?: ""
                                if (cleanIp.isNotEmpty()) {
                                    ctx.success(cleanIp)
                                } else {
                                    ctx.errorCode(1)
                                }
                            }
                        } catch (e: Exception) {
                            ctx.errorCode(1)
                        }
                    }
                }
            }

            override fun lookupSFTPServer(): String = ""

            override fun lookupUser(username: String?): PlatformUser? = null

            override fun openShellSession(
                user: PlatformUser?,
                command: String?,
                args: StringIterator?,
                workingDir: String?,
                uid: Int,
                gid: Int
            ): ShellSession? = null

            override fun openTun(options: TunOptions): Int {
                return tunProvider.openTun(options)
            }

            override fun readSystemSSHHostKey(): String = ""

            override fun readWIFIState(): WIFIState? = null

            override fun registerMyInterface(name: String?) {}

            override fun sendNotification(notification: Notification?) {}

            override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener?) {
                if (listener == null) return
                defaultInterfaceMonitor = listener
                notifyDefaultInterface(listener)

                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        defaultInterfaceMonitor?.let { notifyDefaultInterface(it) }
                    }
                    override fun onLost(network: Network) {
                        defaultInterfaceMonitor?.let { notifyDefaultInterface(it) }
                    }
                    override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                        defaultInterfaceMonitor?.let { notifyDefaultInterface(it) }
                    }
                    // onCapabilitiesChanged is intentionally omitted:
                    // RSSI/bandwidth/validation updates on cellular do not change physical default interface
                    // and previously fired multiple times per second causing severe CPU spinning in Go.
                }
                defaultNetworkCallback = callback
                try {
                    val request = NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                        .build()
                    connectivityManager.registerNetworkCallback(request, callback)
                } catch (e: Exception) {
                    android.util.Log.e("LibboxCore", "Failed to register network callback: ${e.message}", e)
                }
            }

            override fun startNeighborMonitor(listener: NeighborUpdateListener?) {}

            override fun tailscaleHostname(): String = ""

            override fun underNetworkExtension(): Boolean = false

            override fun usePlatformAutoDetectInterfaceControl(): Boolean = true

            override fun usePlatformBridge(): Boolean = false

            override fun usePlatformShell(): Boolean = false
        }
    }

    private fun createCommandServerHandler(): CommandServerHandler {
        return object : CommandServerHandler {
            override fun connectSSHAgent(): Int = -1

            override fun getSystemProxyStatus(): SystemProxyStatus {
                return SystemProxyStatus().apply {
                    available = false
                    enabled = false
                }
            }

            override fun serviceReload() {}

            override fun serviceStop() {
                scope.launch { stop() }
            }

            override fun setSystemProxyEnabled(enabled: Boolean) {}

            override fun triggerNativeCrash() {}

            override fun writeDebugMessage(message: String?) {}
        }
    }
}
