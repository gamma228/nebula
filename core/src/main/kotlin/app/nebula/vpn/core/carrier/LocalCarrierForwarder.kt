package app.nebula.vpn.core.carrier

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Local TCP Loopback Forwarder for Carrier Mode ("Tor via my server").
 *
 * For direct / vanilla Tor bridges that do not natively speak SOCKS5 upstream:
 * This forwarder listens on 127.0.0.1:<boundPort>. When Arti or the Tor engine connects,
 * it establishes a SOCKS5 tunnel through the local sing-box carrier proxy (127.0.0.1:<socksPort>)
 * to the target bridge (targetHost:targetPort) and pipes TCP streams bi-directionally.
 */
class LocalCarrierForwarder(
    val targetHost: String,
    val targetPort: Int,
    val socksHost: String = "127.0.0.1",
    val socksPort: Int,
    val authUsername: String? = null,
    val authPassword: String? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : Closeable {

    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private val activeConnections = ConcurrentHashMap.newKeySet<Socket>()

    var boundPort: Int = 0
        private set

    @Synchronized
    fun start(): Int {
        if (running.get()) return boundPort

        val ss = ServerSocket()
        ss.reuseAddress = true
        ss.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
        serverSocket = ss
        boundPort = ss.localPort
        running.set(true)

        acceptJob = scope.launch {
            while (isActive && running.get()) {
                try {
                    val clientSocket = ss.accept()
                    clientSocket.tcpNoDelay = true
                    activeConnections.add(clientSocket)

                    launch {
                        handleClient(clientSocket)
                    }
                } catch (e: Exception) {
                    if (!running.get()) break
                }
            }
        }

        return boundPort
    }

    private suspend fun handleClient(clientSocket: Socket) {
        var socksSocket: Socket? = null
        try {
            socksSocket = Socket()
            socksSocket.tcpNoDelay = true
            socksSocket.connect(InetSocketAddress(socksHost, socksPort), 5000)
            activeConnections.add(socksSocket)

            // Perform SOCKS5 Handshake with carrier proxy
            val socksIn = DataInputStream(socksSocket.getInputStream())
            val socksOut = DataOutputStream(socksSocket.getOutputStream())

            val hasAuth = !authUsername.isNullOrEmpty() && !authPassword.isNullOrEmpty()
            if (hasAuth) {
                // Greeting with Username/Password auth (method 0x02)
                socksOut.write(byteArrayOf(0x05, 0x01, 0x02))
                socksOut.flush()

                val ver = socksIn.readByte().toInt()
                val method = socksIn.readByte().toInt()
                if (ver != 0x05 || method != 0x02) {
                    throw IOException("SOCKS5 rejected auth method: ver=$ver, method=$method")
                }

                // Subnegotiation RFC 1929
                val uBytes = authUsername!!.toByteArray(Charsets.UTF_8)
                val pBytes = authPassword!!.toByteArray(Charsets.UTF_8)
                socksOut.writeByte(0x01)
                socksOut.writeByte(uBytes.size)
                socksOut.write(uBytes)
                socksOut.writeByte(pBytes.size)
                socksOut.write(pBytes)
                socksOut.flush()

                val uVer = socksIn.readByte().toInt()
                val status = socksIn.readByte().toInt()
                if (uVer != 0x01 || status != 0x00) {
                    throw IOException("SOCKS5 auth failed with status: $status")
                }
            } else {
                // Greeting with No Auth (method 0x00)
                socksOut.write(byteArrayOf(0x05, 0x01, 0x00))
                socksOut.flush()

                val ver = socksIn.readByte().toInt()
                val method = socksIn.readByte().toInt()
                if (ver != 0x05 || method != 0x00) {
                    throw IOException("SOCKS5 rejected no-auth method: ver=$ver, method=$method")
                }
            }

            // Send SOCKS5 CONNECT command to target bridge
            socksOut.writeByte(0x05) // Version
            socksOut.writeByte(0x01) // CMD: CONNECT
            socksOut.writeByte(0x00) // Reserved

            // Address type: Domain name (0x03) or IPv4 (0x01)
            val ipParts = targetHost.split('.')
            if (ipParts.size == 4 && ipParts.all { it.toIntOrNull() in 0..255 }) {
                socksOut.writeByte(0x01) // IPv4
                for (part in ipParts) {
                    socksOut.writeByte(part.toInt())
                }
            } else {
                socksOut.writeByte(0x03) // Domain name
                val hostBytes = targetHost.toByteArray(Charsets.UTF_8)
                socksOut.writeByte(hostBytes.size)
                socksOut.write(hostBytes)
            }

            // Port (2 bytes, big-endian)
            socksOut.writeShort(targetPort)
            socksOut.flush()

            // Read CONNECT response
            val respVer = socksIn.readByte().toInt()
            val respStatus = socksIn.readByte().toInt()
            socksIn.readByte() // Reserved
            val respAtyp = socksIn.readByte().toInt()

            // Skip BND.ADDR and BND.PORT
            when (respAtyp) {
                0x01 -> socksIn.skipBytes(4 + 2) // IPv4
                0x03 -> {
                    val len = socksIn.readUnsignedByte()
                    socksIn.skipBytes(len + 2) // Domain
                }
                0x04 -> socksIn.skipBytes(16 + 2) // IPv6
            }

            if (respVer != 0x05 || respStatus != 0x00) {
                throw IOException("SOCKS5 connect to $targetHost:$targetPort failed: status=$respStatus")
            }

            // Bi-directional streaming
            val clientIn = clientSocket.getInputStream()
            val clientOut = clientSocket.getOutputStream()

            val job1 = scope.launch {
                try {
                    clientIn.copyTo(socksOut)
                    socksOut.flush()
                } catch (_: Exception) {}
            }

            val job2 = scope.launch {
                try {
                    socksIn.copyTo(clientOut)
                    clientOut.flush()
                } catch (_: Exception) {}
            }

            job1.join()
            job2.join()
        } catch (_: Exception) {
            // Socket closed or error during transfer
        } finally {
            try { clientSocket.close() } catch (_: Exception) {}
            try { socksSocket?.close() } catch (_: Exception) {}
            activeConnections.remove(clientSocket)
            if (socksSocket != null) activeConnections.remove(socksSocket)
        }
    }

    override fun close() {
        stop()
    }

    @Synchronized
    fun stop() {
        if (!running.compareAndSet(true, false)) return

        acceptJob?.cancel()
        acceptJob = null

        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null

        for (sock in activeConnections) {
            try {
                sock.close()
            } catch (_: Exception) {}
        }
        activeConnections.clear()
    }
}
