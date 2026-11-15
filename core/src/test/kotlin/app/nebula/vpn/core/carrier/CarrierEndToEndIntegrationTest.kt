package app.nebula.vpn.core.carrier

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Milestone T4 End-to-End Proof:
 * Proves that bridge connections from the Tor Engine travel strictly through
 * the Carrier SOCKS5 proxy to the destination bridge, logging the SOCKS5 CONNECT target.
 */
class CarrierEndToEndIntegrationTest {

    @Test
    fun testCarrierEndToEndDeliveryAndConnectTarget() = runBlocking {
        val auditLogs = CopyOnWriteArrayList<String>()
        val connectionFinishedLatch = CountDownLatch(1)

        // 1. Setup Fake Bridge Server (representing the remote Tor Bridge)
        val fakeBridgeServer = ServerSocket()
        fakeBridgeServer.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
        val fakeBridgePort = fakeBridgeServer.localPort

        val fakeBridgeThread = thread(isDaemon = true, name = "FakeBridge") {
            try {
                val bridgeSocket = fakeBridgeServer.accept()
                auditLogs.add("[FakeBridge] Accepted incoming bridge TCP connection from ${bridgeSocket.inetAddress.hostAddress}:${bridgeSocket.port}")
                val dis = DataInputStream(bridgeSocket.getInputStream())
                val dos = DataOutputStream(bridgeSocket.getOutputStream())

                val buffer = ByteArray(256)
                val read = dis.read(buffer)
                val payload = String(buffer, 0, read, Charsets.UTF_8)
                auditLogs.add("[FakeBridge] Received handshake payload: '$payload'")

                // Respond with Tor cell / certificate response
                val response = "TOR_BRIDGE_SERVER_HANDSHAKE_OK"
                dos.write(response.toByteArray(Charsets.UTF_8))
                dos.flush()

                bridgeSocket.close()
            } catch (e: Exception) {
                auditLogs.add("[FakeBridge] Error: ${e.message}")
            }
        }

        // 2. Setup Real Functional SOCKS5 Server (representing sing-box Carrier Mode inbound)
        val socksServer = ServerSocket()
        socksServer.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
        val socksPort = socksServer.localPort

        val socksThread = thread(isDaemon = true, name = "SocksCarrier") {
            try {
                val clientSocket = socksServer.accept()
                val dis = DataInputStream(clientSocket.getInputStream())
                val dos = DataOutputStream(clientSocket.getOutputStream())

                // 2a. SOCKS5 Greeting (RFC 1928)
                val ver = dis.readByte().toInt()
                val nmethods = dis.readByte().toInt()
                val methods = ByteArray(nmethods)
                dis.readFully(methods)
                assertEquals(5, ver)

                // No Auth required (0x00)
                dos.writeByte(5)
                dos.writeByte(0)
                dos.flush()

                // 2b. SOCKS5 Request
                val reqVer = dis.readByte().toInt()
                val cmd = dis.readByte().toInt() // 1 = CONNECT
                val rsv = dis.readByte().toInt()
                val atyp = dis.readByte().toInt()
                assertEquals(5, reqVer)
                assertEquals(1, cmd) // Must be SOCKS5 CONNECT

                val targetHost: String
                val targetPort: Int

                when (atyp) {
                    1 -> { // IPv4 (4 bytes)
                        val ipBytes = ByteArray(4)
                        dis.readFully(ipBytes)
                        targetHost = InetAddress.getByAddress(ipBytes).hostAddress
                        targetPort = dis.readUnsignedShort()
                    }
                    3 -> { // Domain name (1 byte length + ASCII)
                        val len = dis.readUnsignedByte()
                        val domainBytes = ByteArray(len)
                        dis.readFully(domainBytes)
                        targetHost = String(domainBytes, Charsets.US_ASCII)
                        targetPort = dis.readUnsignedShort()
                    }
                    else -> throw IllegalArgumentException("Unsupported SOCKS5 atyp: $atyp")
                }

                auditLogs.add("[SOCKS5-Carrier] Received CONNECT target: $targetHost:$targetPort")

                // 2c. SOCKS5 server dials the target bridge
                val bridgeOutSocket = Socket(targetHost, targetPort)
                dos.write(byteArrayOf(5, 0, 0, 1, 0, 0, 0, 0, 0, 0)) // 0x00 = Success
                dos.flush()

                // 2d. Bi-directional forwarding
                val t1 = thread(isDaemon = true) {
                    try {
                        clientSocket.getInputStream().copyTo(bridgeOutSocket.getOutputStream())
                    } catch (_: Exception) {}
                }
                val t2 = thread(isDaemon = true) {
                    try {
                        bridgeOutSocket.getInputStream().copyTo(clientSocket.getOutputStream())
                    } catch (_: Exception) {}
                }

                t1.join(3000)
                t2.join(3000)
                clientSocket.close()
                bridgeOutSocket.close()
            } catch (e: Exception) {
                auditLogs.add("[SOCKS5-Carrier] Error: ${e.message}")
            }
        }

        // 3. Start LocalCarrierForwarder targeting the Fake Bridge via SOCKS5
        val forwarder = LocalCarrierForwarder(
            targetHost = "127.0.0.1",
            targetPort = fakeBridgePort,
            socksHost = "127.0.0.1",
            socksPort = socksPort
        )
        val engineForwarderPort = forwarder.start()
        assertTrue("Forwarder must bind to local port", engineForwarderPort > 0)
        auditLogs.add("[TorEngine] LocalCarrierForwarder listening on 127.0.0.1:$engineForwarderPort pointing to 127.0.0.1:$fakeBridgePort")

        // 4. Client (Tor / Arti engine) connects to forwarderPort
        val artiSocket = Socket("127.0.0.1", engineForwarderPort)
        val artiOut = artiSocket.getOutputStream()
        val artiIn = artiSocket.getInputStream()

        // Send simulated Tor TLS client handshake
        val torClientHello = "TOR_CLIENT_HELLO_VERSIONS_CELL"
        artiOut.write(torClientHello.toByteArray(Charsets.UTF_8))
        artiOut.flush()

        // Read response received through carrier tunnel
        val respBuffer = ByteArray(128)
        val readCount = artiIn.read(respBuffer)
        val response = String(respBuffer, 0, readCount, Charsets.UTF_8)
        auditLogs.add("[TorEngine] Received response from bridge via carrier: '$response'")

        assertEquals("TOR_BRIDGE_SERVER_HANDSHAKE_OK", response)

        artiSocket.close()
        forwarder.stop()
        socksServer.close()
        fakeBridgeServer.close()
        fakeBridgeThread.join(2000)
        socksThread.join(2000)

        // 5. Assertions on Audit Trail
        println("=== CARRIER MODE AUDIT TRAIL ===")
        auditLogs.forEach { println(it) }

        assertTrue(
            "Carrier SOCKS5 proxy must log the exact destination bridge CONNECT target",
            auditLogs.any { it.contains("[SOCKS5-Carrier] Received CONNECT target: 127.0.0.1:$fakeBridgePort") }
        )
        assertTrue(
            "Fake Bridge must receive connection from carrier and log payload",
            auditLogs.any { it.contains("TOR_CLIENT_HELLO_VERSIONS_CELL") }
        )
        assertTrue(
            "Tor Engine must receive response forwarded through carrier tunnel",
            auditLogs.any { it.contains("TOR_BRIDGE_SERVER_HANDSHAKE_OK") }
        )
    }
}
