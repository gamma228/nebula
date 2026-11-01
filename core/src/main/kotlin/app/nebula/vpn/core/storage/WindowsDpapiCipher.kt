package app.nebula.vpn.core.storage

import com.sun.jna.Platform
import com.sun.jna.platform.win32.Crypt32Util
import java.nio.charset.StandardCharsets
import java.util.Base64

interface SecretCipher {
    fun encrypt(plainText: String): String
    fun decrypt(cipherText: String): String
}

/**
 * Windows DPAPI encryption via JNA Crypt32Util.
 * Uses Windows CryptProtectData to securely bind credentials to the current Windows user login.
 */
object WindowsDpapiCipher : SecretCipher {

    private val isWindows: Boolean = Platform.isWindows()

    override fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        return try {
            if (isWindows) {
                val data = plainText.toByteArray(StandardCharsets.UTF_8)
                val encrypted = Crypt32Util.cryptProtectData(data)
                "DPAPI:" + Base64.getEncoder().encodeToString(encrypted)
            } else {
                "PLAIN:" + plainText
            }
        } catch (e: Exception) {
            System.err.println("[WARN DPAPI] Encryption failed, falling back: ${e.message}")
            "PLAIN:" + plainText
        }
    }

    override fun decrypt(cipherText: String): String {
        if (cipherText.isEmpty()) return ""
        return try {
            if (cipherText.startsWith("DPAPI:") && isWindows) {
                val rawBase64 = cipherText.removePrefix("DPAPI:")
                val encrypted = Base64.getDecoder().decode(rawBase64)
                val decrypted = Crypt32Util.cryptUnprotectData(encrypted)
                String(decrypted, StandardCharsets.UTF_8)
            } else if (cipherText.startsWith("PLAIN:")) {
                cipherText.removePrefix("PLAIN:")
            } else {
                cipherText
            }
        } catch (e: Exception) {
            System.err.println("[WARN DPAPI] Decryption failed: ${e.message}")
            cipherText
        }
    }
}
