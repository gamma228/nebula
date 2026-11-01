package app.nebula.vpn.desktop

import java.io.File
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileChannel
import java.nio.channels.FileLock

class SingleInstanceGuard(
    private val lockFile: File = File(
        System.getenv("APPDATA") ?: System.getProperty("user.home"),
        "Nebula/app.lock"
    ),
    private val onRestoreRequest: () -> Unit
) {
    private val portFile: File = File(lockFile.parentFile ?: File("."), "${lockFile.nameWithoutExtension}.port")
    private var randomAccessFile: RandomAccessFile? = null
    private var fileChannel: FileChannel? = null
    private var fileLock: FileLock? = null
    private var serverSocket: ServerSocket? = null

    fun acquireOrSignal(): Boolean {
        return try {
            lockFile.parentFile?.mkdirs()
            val raf = RandomAccessFile(lockFile, "rw")
            randomAccessFile = raf
            val channel = raf.channel
            fileChannel = channel
            val lock = channel.tryLock()

            if (lock != null) {
                fileLock = lock
                // We are the single primary instance.
                // Port 0 tells the OS kernel to bind to any available unreserved local port.
                val server = ServerSocket(0, 5, InetAddress.getByName("127.0.0.1"))
                serverSocket = server
                val port = server.localPort

                try {
                    portFile.writeText("$port\n")
                } catch (_: Exception) {}

                Thread({
                    while (!server.isClosed) {
                        try {
                            val client = server.accept()
                            val line = client.inputStream.bufferedReader().readLine()
                            if (line == "RESTORE" || line == "SHOW") {
                                onRestoreRequest()
                            }
                            client.close()
                        } catch (_: Exception) {}
                    }
                }, "Nebula-SingleInstanceGuard").apply { isDaemon = true }.start()

                true
            } else {
                // Another instance holds the kernel file lock. Signal it to restore.
                signalExistingInstance()
                false
            }
        } catch (_: Exception) {
            // Lock collision or access exception -> another instance is active
            signalExistingInstance()
            false
        }
    }

    private fun signalExistingInstance() {
        try {
            if (portFile.exists()) {
                val portText = portFile.readText().trim()
                val port = portText.toIntOrNull()
                if (port != null && port > 0) {
                    Socket(InetAddress.getByName("127.0.0.1"), port).use { client ->
                        client.soTimeout = 2000
                        client.outputStream.write("RESTORE\n".toByteArray(Charsets.UTF_8))
                        client.outputStream.flush()
                    }
                }
            }
        } catch (_: Exception) {}
    }

    fun release() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        try {
            portFile.delete()
        } catch (_: Exception) {}
        try {
            fileLock?.release()
        } catch (_: Exception) {}
        try {
            fileChannel?.close()
        } catch (_: Exception) {}
        try {
            randomAccessFile?.close()
        } catch (_: Exception) {}
    }
}
