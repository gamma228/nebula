package app.nebula.vpn.core.storage

import app.nebula.vpn.core.model.ProxyProfile
import app.nebula.vpn.core.model.RoutingRule
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@Serializable
data class NebulaBackupPayload(
    val schemaVersion: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val profiles: List<ProxyProfile> = emptyList(),
    val rules: List<RoutingRule> = emptyList(),
    val settings: DesktopSettings? = null
)

@Serializable
data class NebulaBackupEnvelope(
    val format: String = "nebula-backup",
    val version: Int = 1,
    val encrypted: Boolean = false,
    val kdf: String? = null,
    val iterations: Int? = null,
    val saltBase64: String? = null,
    val ivBase64: String? = null,
    val payloadBase64: String
)

object BackupManager {
    private const val PBKDF2_ALGO = "PBKDF2WithHmacSHA256"
    private const val AES_GCM_ALGO = "AES/GCM/NoPadding"
    private const val ITERATIONS = 100_000
    private const val KEY_LEN_BITS = 256
    private const val SALT_LEN_BYTES = 16
    private const val IV_LEN_BYTES = 12
    private const val TAG_LEN_BITS = 128

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val secureRandom = SecureRandom()

    fun exportBackup(payload: NebulaBackupPayload, password: String? = null): String {
        val payloadJson = json.encodeToString(payload)
        val payloadBytes = payloadJson.toByteArray(StandardCharsets.UTF_8)

        if (password.isNullOrBlank()) {
            val envelope = NebulaBackupEnvelope(
                encrypted = false,
                payloadBase64 = Base64.getEncoder().encodeToString(payloadBytes)
            )
            return json.encodeToString(envelope)
        }

        val salt = ByteArray(SALT_LEN_BYTES).apply { secureRandom.nextBytes(this) }
        val iv = ByteArray(IV_LEN_BYTES).apply { secureRandom.nextBytes(this) }
        val key = deriveKey(password, salt)

        val cipher = Cipher.getInstance(AES_GCM_ALGO)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LEN_BITS, iv))
        val ciphertext = cipher.doFinal(payloadBytes)

        val envelope = NebulaBackupEnvelope(
            encrypted = true,
            kdf = PBKDF2_ALGO,
            iterations = ITERATIONS,
            saltBase64 = Base64.getEncoder().encodeToString(salt),
            ivBase64 = Base64.getEncoder().encodeToString(iv),
            payloadBase64 = Base64.getEncoder().encodeToString(ciphertext)
        )
        return json.encodeToString(envelope)
    }

    fun importBackup(jsonString: String, password: String? = null): NebulaBackupPayload {
        val envelope = json.decodeFromString<NebulaBackupEnvelope>(jsonString)
        require(envelope.format == "nebula-backup") { "Invalid backup format: ${envelope.format}" }

        val payloadBytes = if (envelope.encrypted) {
            require(!password.isNullOrBlank()) { "Backup is encrypted; password required" }
            requireNotNull(envelope.saltBase64) { "Missing salt in encrypted backup" }
            requireNotNull(envelope.ivBase64) { "Missing IV in encrypted backup" }

            val salt = Base64.getDecoder().decode(envelope.saltBase64)
            val iv = Base64.getDecoder().decode(envelope.ivBase64)
            val ciphertext = Base64.getDecoder().decode(envelope.payloadBase64)
            val key = deriveKey(password, salt, envelope.iterations ?: ITERATIONS)

            val cipher = Cipher.getInstance(AES_GCM_ALGO)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LEN_BITS, iv))
            cipher.doFinal(ciphertext)
        } else {
            Base64.getDecoder().decode(envelope.payloadBase64)
        }

        val payloadJson = String(payloadBytes, StandardCharsets.UTF_8)
        return json.decodeFromString<NebulaBackupPayload>(payloadJson)
    }

    fun createMigrationBackup(baseDir: File): File? {
        if (!baseDir.exists()) return null
        val backupsDir = File(baseDir, "backups").apply { mkdirs() }
        val stamp = System.currentTimeMillis()
        val destDir = File(backupsDir, "backup_$stamp").apply { mkdirs() }

        listOf("profiles.json", "rules.json", "settings.json").forEach { name ->
            val src = File(baseDir, name)
            if (src.exists()) {
                src.copyTo(File(destDir, name), overwrite = true)
            }
        }
        return destDir
    }

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int = ITERATIONS): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LEN_BITS)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGO)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }
}
