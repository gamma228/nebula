package app.nebula.vpn.core.storage

import app.nebula.vpn.core.contract.StorageRepository
import app.nebula.vpn.core.model.ProxyProfile
import app.nebula.vpn.core.model.RoutingRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class VersionedProfilesFile(
    val schemaVersion: Int = 1,
    val selectedProfileId: String? = null,
    val profiles: List<ProxyProfile> = emptyList()
)

@Serializable
data class VersionedRulesFile(
    val schemaVersion: Int = 1,
    val rules: List<RoutingRule> = emptyList()
)

@Serializable
data class DesktopSettings(
    val schemaVersion: Int = 1,
    val languageCode: String = "ru",
    val activeThemeId: String = "emerald_cyber",
    val customAccentHex: String = "#00D084",
    val backgroundImagePath: String? = null,
    val backgroundDimAlpha: Float = 0.35f,
    val mode: String = "SYSTEM_PROXY", // "TUN" or "SYSTEM_PROXY"
    val autoConnectOnLaunch: Boolean = false,
    val startMinimized: Boolean = false,
    val closeToTray: Boolean = true,
    val autoUpdateSubscriptionHours: Int = 24,
    val bypassRu: Boolean = true,
    val blockQuic: Boolean = true,
    val fragmentEnabled: Boolean = true,
    val sniSpoofPreset: String = "rutube.ru",
    val remoteDnsUrl: String = "https://1.1.1.1/dns-query",
    val fakeDnsEnabled: Boolean = true,
    val disableIpv6: Boolean = true,
    val killSwitchEnabled: Boolean = false,
    val tunMtu: Int = 1400,
    val mixedInboundPort: Int = 2080
)

class DesktopStorageRepository(
    private val baseDir: File = File(
        System.getenv("APPDATA")?.let { "$it${File.separator}Nebula" }
            ?: (System.getProperty("user.home") + "${File.separator}.nebula")
    ),
    private val cipher: SecretCipher = WindowsDpapiCipher
) : StorageRepository {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val mutex = Mutex()
    private val profilesFile = File(baseDir, "profiles.json")
    private val rulesFile = File(baseDir, "rules.json")
    private val settingsFile = File(baseDir, "settings.json")

    init {
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
    }

    override suspend fun getAllProfiles(): List<ProxyProfile> = withContext(Dispatchers.IO) {
        mutex.withLock {
            readProfilesLocked()
        }
    }

    override suspend fun getProfileById(id: String): ProxyProfile? = withContext(Dispatchers.IO) {
        mutex.withLock {
            readProfilesLocked().firstOrNull { it.id == id }
        }
    }

    override suspend fun saveProfile(profile: ProxyProfile): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = readProfilesLocked().toMutableList()
            val index = current.indexOfFirst { it.id == profile.id }
            if (index >= 0) {
                current[index] = profile
            } else {
                current.add(profile)
            }
            writeProfilesLocked(current)
        }
    }

    override suspend fun saveProfiles(profiles: List<ProxyProfile>): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = readProfilesLocked().associateBy { it.id }.toMutableMap()
            for (p in profiles) {
                current[p.id] = p
            }
            writeProfilesLocked(current.values.toList())
        }
    }

    override suspend fun deleteProfile(id: String): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = readProfilesLocked().filter { it.id != id }
            writeProfilesLocked(current)
        }
    }

    override suspend fun getSelectedProfileId(): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!profilesFile.exists()) return@withLock null
            try {
                val content = profilesFile.readText()
                val parsed = json.decodeFromString<VersionedProfilesFile>(content)
                parsed.selectedProfileId
            } catch (e: Exception) {
                null
            }
        }
    }

    override suspend fun setSelectedProfileId(id: String?): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            val currentProfiles = readProfilesLocked()
            writeProfilesLocked(currentProfiles, selectedId = id)
        }
    }

    override suspend fun getAllRules(): List<RoutingRule> = withContext(Dispatchers.IO) {
        mutex.withLock {
            readRulesLocked()
        }
    }

    override suspend fun saveRule(rule: RoutingRule): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = readRulesLocked().toMutableList()
            val index = current.indexOfFirst { it.id == rule.id }
            if (index >= 0) {
                current[index] = rule
            } else {
                current.add(rule)
            }
            writeRulesLocked(current)
        }
    }

    override suspend fun deleteRule(id: String): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = readRulesLocked().filter { it.id != id }
            writeRulesLocked(current)
        }
    }

    override suspend fun updateRulePriorities(ruleIdsInOrder: List<String>): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = readRulesLocked()
            val map = current.associateBy { it.id }
            val reordered = ruleIdsInOrder.mapNotNull { map[it] }
            writeRulesLocked(reordered)
        }
    }

    suspend fun getSettings(): DesktopSettings = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!settingsFile.exists()) return@withLock DesktopSettings()
            try {
                json.decodeFromString<DesktopSettings>(settingsFile.readText())
            } catch (e: Exception) {
                DesktopSettings()
            }
        }
    }

    suspend fun updateSettings(transform: (DesktopSettings) -> DesktopSettings): DesktopSettings = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = if (settingsFile.exists()) {
                try {
                    json.decodeFromString<DesktopSettings>(settingsFile.readText())
                } catch (e: Exception) {
                    DesktopSettings()
                }
            } else {
                DesktopSettings()
            }
            val updated = transform(current)
            settingsFile.writeText(json.encodeToString(updated))
            updated
        }
    }

    private fun readProfilesLocked(): List<ProxyProfile> {
        if (!profilesFile.exists()) return emptyList()
        return try {
            val raw = profilesFile.readText()
            val data = json.decodeFromString<VersionedProfilesFile>(raw)
            data.profiles.map { p ->
                p.copy(
                    authSecret = cipher.decrypt(p.authSecret),
                    tls = p.tls.copy(
                        realityPublicKey = cipher.decrypt(p.tls.realityPublicKey)
                    )
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun writeProfilesLocked(profiles: List<ProxyProfile>, selectedId: String? = null) {
        val encryptedProfiles = profiles.map { p ->
            p.copy(
                authSecret = cipher.encrypt(p.authSecret),
                tls = p.tls.copy(
                    realityPublicKey = cipher.encrypt(p.tls.realityPublicKey)
                )
            )
        }
        val currentSelected = selectedId ?: (if (profilesFile.exists()) {
            try {
                json.decodeFromString<VersionedProfilesFile>(profilesFile.readText()).selectedProfileId
            } catch (_: Exception) { null }
        } else null)

        val fileData = VersionedProfilesFile(
            schemaVersion = 1,
            selectedProfileId = currentSelected,
            profiles = encryptedProfiles
        )
        profilesFile.writeText(json.encodeToString(fileData))
    }

    private fun readRulesLocked(): List<RoutingRule> {
        if (!rulesFile.exists()) return emptyList()
        return try {
            val raw = rulesFile.readText()
            val data = json.decodeFromString<VersionedRulesFile>(raw)
            data.rules
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun writeRulesLocked(rules: List<RoutingRule>) {
        val fileData = VersionedRulesFile(
            schemaVersion = 1,
            rules = rules
        )
        rulesFile.writeText(json.encodeToString(fileData))
    }
}
