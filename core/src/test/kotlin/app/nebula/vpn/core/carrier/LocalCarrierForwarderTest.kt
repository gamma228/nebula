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
import kotlin.concurrent.thread

class LocalCarrierForwarderTest {

    @Test
    fun testLocalCarrierForwarderHandshakeAndPipe() = runBlocking {
        // 1. Setup mock SOCKS5 server that acts as an echo target
        val mockSocksServer = ServerSocket()
        mockSocksServer.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
        val socksPort = mockSocksServer.localPort

        val serverThread = thread(isDaemon = true) {
            try {
                val sock = mockSocksServer.accept()
                val dis = DataInputStream(sock.getInputStream())
                val dos = DataOutputStream(sock.getOutputStream())

                // Read greeting (ver=5, nmethods=1, method=0)
                val ver = dis.readByte().toInt()
                val nmethods = dis.readByte().toInt()
                val method = dis.readByte().toInt()
                assertEquals(5, ver)
                assertEquals(0, method)

                // Reply greeting (ver=5, method=0)
                dos.writeByte(5)
                dos.writeByte(0)
                dos.flush()

                // Read CONNECT command
                val cmdVer = dis.readByte().toInt()
                val cmd = dis.readByte().toInt()
                val rsv = dis.readByte().toInt()
                val atyp = dis.readByte().toInt()
                assertEquals(5, cmdVer)
                assertEquals(1, cmd) // CONNECT

                if (atyp == 1) { // IPv4
                    dis.skipBytes(4 + 2)
                } else if (atyp == 3) { // Domain
                    val len = dis.readUnsignedByte()
                    dis.skipBytes(len + 2)
                }

                // Reply CONNECT success (ver=5, rep=0, rsv=0, atyp=1, 0.0.0.0:0)
                dos.write(byteArrayOf(5, 0, 0, 1, 0, 0, 0, 0, 0, 0))
                dos.flush()

                // Echo data back
                val buf = ByteArray(1024)
                val n = dis.read(buf)
                if (n > 0) {
                    dos.write(buf, 0, n)
                    dos.flush()
                }

                sock.close()
            } catch (_: Exception) {}
        }

        // 2. Start LocalCarrierForwarder targeting a dummy destination
        val forwarder = LocalCarrierForwarder(
            targetHost = "1.2.3.4",
            targetPort = 443,
            socksHost = "127.0.0.1",
            socksPort = socksPort
        )
        val forwarderPort = forwarder.start()
        assertTrue(forwarderPort > 0)

        // 3. Client connects to forwarderPort and sends payload
        val client = Socket("127.0.0.1", forwarderPort)
        val clientOut = client.getOutputStream()
        val clientIn = client.getInputStream()

        clientOut.write("PING_TOR_CARRIER".toByteArray(Charsets.UTF_8))
        clientOut.flush()

        val respBuf = ByteArray(64)
        val readBytes = clientIn.read(respBuf)
        val response = String(respBuf, 0, readBytes, Charsets.UTF_8)
        assertEquals("PING_TOR_CARRIER", response)

        client.close()
        forwarder.stop()
        mockSocksServer.close()
        serverThread.join(2000)
    }
}
