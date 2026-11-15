package app.nebula.vpn.core.windows

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

@Serializable
data class CoreReleaseInfo(
    val tagName: String,
    val version: String,
    val downloadUrl: String,
    val checksumsUrl: String? = null,
    val releaseNotes: String = ""
)

object SingBoxUpdater {

    private val json = Json { ignoreUnknownKeys = true }

    fun getCurrentVersion(workingDir: File): String {
        return try {
            val exe = File(workingDir, "sing-box.exe")
            if (!exe.exists()) return "Missing"
            val process = ProcessBuilder(exe.absolutePath, "version").redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            val match = Regex("""sing-box version (\d+\.\d+\.\d+)""").find(output)
            match?.groupValues?.get(1) ?: output.lines().firstOrNull()?.trim() ?: "Unknown"
        } catch (_: Exception) {
            "Unknown"
        }
    }

    suspend fun checkLatestRelease(): CoreReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URI("https://api.github.com/repos/SagerNet/sing-box/releases/latest").toURL()
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "Nebula-Desktop-Updater")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            if (conn.responseCode !in 200..299) return@withContext null
            val body = conn.inputStream.bufferedReader().use { it.readText() }

            // Extract tag_name and assets
            val tagMatch = Regex(""""tag_name"\s*:\s*"([^"]+)"""").find(body)
            val tagName = tagMatch?.groupValues?.get(1) ?: return@withContext null
            val version = tagName.removePrefix("v")

            // Look for windows-amd64 zip asset
            val winZipMatch = Regex(""""browser_download_url"\s*:\s*"([^"]+windows-amd64\.zip)"""").find(body)
            val downloadUrl = winZipMatch?.groupValues?.get(1) ?: return@withContext null

            // Look for sha256sums or checksums asset
            val checksumMatch = Regex(""""browser_download_url"\s*:\s*"([^"]+sha256[^"]*)"""").find(body)
            val checksumsUrl = checksumMatch?.groupValues?.get(1)

            CoreReleaseInfo(
                tagName = tagName,
                version = version,
                downloadUrl = downloadUrl,
                checksumsUrl = checksumsUrl
            )
        } catch (_: Exception) {
            null
        }
    }

    fun verifySha256(file: File, expectedSha256: String): Boolean {
        if (!file.exists()) return false
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = file.readBytes()
        val hash = md.digest(bytes).joinToString("") { "%02x".format(it) }
        return hash.equals(expectedSha256.trim(), ignoreCase = true)
    }

    suspend fun rollback(workingDir: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val current = File(workingDir, "sing-box.exe")
            val backup = File(workingDir, "sing-box.exe.bak")
            if (backup.exists()) {
                if (current.exists()) current.delete()
                backup.renameTo(current)
                true
            } else false
        } catch (_: Exception) {
            false
        }
    }
}
