package app.nebula.vpn.core.proxy

import android.content.Context
import android.net.ConnectivityManager
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

    private val logBuffer = mutableListOf<String>()

    init {
        try {
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
            commandClient?.disconnect()
            commandClient = null

            commandServer?.closeService()
            commandServer?.close()
            commandServer = null
            activeTunProvider = null

            _state.value = ConnectionState.Disconnected
            _stats.value = TrafficStats()
        } catch (e: Exception) {
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

    override suspend fun selectOutbound(groupTag: String, outboundTag: String): Boolean = withContext(Dispatchers.IO) {
        val client = commandClient ?: return@withContext false
        try {
            android.util.Log.i("LibboxCore", "selectOutbound: group=$groupTag, outbound=$outboundTag")
            client.selectOutbound(groupTag, outboundTag)
            android.util.Log.i("LibboxCore", "selectOutbound succeeded!")
            true
        } catch (e: Exception) {
            android.util.Log.w("LibboxCore", "selectOutbound failed: ${e.message}")
            false
        }
    }

    private fun initCommandClient() {
        try {
            val clientOptions = CommandClientOptions().apply {
                statusInterval = 1000 // 1 second updates
                addCommand(Libbox.CommandStatus)
                addCommand(Libbox.CommandLog)
                addCommand(Libbox.CommandGroup)
                addCommand(Libbox.CommandConnections)
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

                override fun writeLogs(logs: LogIterator?) {
                    if (logs == null) return
                    synchronized(logBuffer) {
                        while (logs.hasNext()) {
                            val entry = logs.next()
                            val line = entry?.message ?: ""
                            logBuffer.add(line)
                            if (logBuffer.size > 2000) {
                                logBuffer.removeAt(0)
                            }
                        }
                    }
                }

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
            client.connect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createPlatformInterface(tunProvider: TunProvider): PlatformInterface {
        return object : PlatformInterface {
            override fun autoDetectInterfaceControl(fd: Int) {
                tunProvider.protect(fd)
            }

            override fun cancelNotification(tag: String?, id: Int) {}

            override fun checkPlatformShell() {}

            override fun clearDNSCache() {}

            override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener?) {}

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

            override fun getInterfaces(): NetworkInterfaceIterator? = null

            override fun includeAllNetworks(): Boolean = false

            override fun localDNSTransport(): LocalDNSTransport? = null

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

            override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener?) {}

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
