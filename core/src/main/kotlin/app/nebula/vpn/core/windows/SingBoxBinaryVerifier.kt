package app.nebula.vpn.core.windows

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object SingBoxBinaryVerifier {

    const val EXPECTED_SING_BOX_AMD64_SHA256 = "2DC46C2E2E2B0EA94E5CCD6D0B63CBFC583C66A1BAE8AFD9D44F19B49BD0A16D"
    const val EXPECTED_WINTUN_AMD64_SHA256 = "E5DA8447DC2C320EDC0FC52FA01885C103DE8C118481F683643CACC3220DAFCE"

    fun calculateSha256(file: File): String {
        require(file.exists() && file.isFile) { "File does not exist or is not a regular file: ${file.absolutePath}" }
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(65536)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        val hashBytes = digest.digest()
        val hexString = StringBuilder()
        for (b in hashBytes) {
            hexString.append(String.format("%02X", b))
        }
        return hexString.toString()
    }

    fun verifySingBox(file: File): Boolean {
        if (!file.exists()) return false
        val hash = calculateSha256(file)
        return hash.equals(EXPECTED_SING_BOX_AMD64_SHA256, ignoreCase = true)
    }

    fun verifyWintun(file: File): Boolean {
        if (!file.exists()) return false
        val hash = calculateSha256(file)
        return hash.equals(EXPECTED_WINTUN_AMD64_SHA256, ignoreCase = true)
    }
}
