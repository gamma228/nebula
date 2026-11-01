package app.nebula.vpn.desktop

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

class SingleInstanceGuard(
    private val port: Int = 58392,
    private val onRestoreRequest: () -> Unit
) {
    private var serverSocket: ServerSocket? = null

    fun acquireOrSignal(): Boolean {
        return try {
            val server = ServerSocket(port, 1, InetAddress.getByName("127.0.0.1"))
            serverSocket = server

            Thread({
                while (!server.isClosed) {
                    try {
                        val client = server.accept()
                        val reader = BufferedReader(InputStreamReader(client.inputStream))
                        val line = reader.readLine()
                        if (line == "RESTORE" || line == "SHOW") {
                            onRestoreRequest()
                        }
                        client.close()
                    } catch (_: Exception) {}
                }
            }, "Nebula-SingleInstanceGuard").apply { isDaemon = true }.start()

            true
        } catch (_: Exception) {
            // Another instance is already running; signal it to restore window
            try {
                val client = Socket(InetAddress.getByName("127.0.0.1"), port)
                client.outputStream.write("RESTORE\n".toByteArray(Charsets.UTF_8))
                client.outputStream.flush()
                client.close()
            } catch (_: Exception) {}
            false
        }
    }

    fun release() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
    }
}
