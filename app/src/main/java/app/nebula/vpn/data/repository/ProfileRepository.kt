package app.nebula.vpn.data.repository

import app.nebula.vpn.core.crypto.SecretBox
import app.nebula.vpn.core.model.ProtocolType
import app.nebula.vpn.core.model.ProxyProfile
import app.nebula.vpn.core.model.TlsConfig
import app.nebula.vpn.core.model.TransportConfig
import app.nebula.vpn.data.database.dao.ProfileDao
import app.nebula.vpn.data.database.entity.ProfileEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(
    private val profileDao: ProfileDao
) {
    private val json = Json { ignoreUnknownKeys = true }

    private data class CachedProfile(
        val contentHash: Int,
        val profile: ProxyProfile
    )

    private val profileCache = java.util.concurrent.ConcurrentHashMap<String, CachedProfile>()

    private fun ProfileEntity.computeContentHash(): Int {
        var res = id.hashCode()
        res = 31 * res + name.hashCode()
        res = 31 * res + protocol.hashCode()
        res = 31 * res + server.hashCode()
        res = 31 * res + port
        res = 31 * res + encryptedSecret.hashCode()
        res = 31 * res + encryption.hashCode()
        res = 31 * res + tlsJson.hashCode()
        res = 31 * res + transportJson.hashCode()
        res = 31 * res + (detour?.hashCode() ?: 0)
        res = 31 * res + (subscriptionId?.hashCode() ?: 0)
        res = 31 * res + packetEncoding.hashCode()
        res = 31 * res + headerType.hashCode()
        return res
    }

    private fun ProfileEntity.toCachedModel(): ProxyProfile {
        val hash = computeContentHash()
        val cached = profileCache[id]
        if (cached != null && cached.contentHash == hash) {
            val p = cached.profile
            return if (p.lastPingMs == lastPingMs && p.isFavorite == isFavorite && p.updatedAt == updatedAt) {
                p
            } else {
                val updated = p.copy(lastPingMs = lastPingMs, isFavorite = isFavorite, updatedAt = updatedAt)
                profileCache[id] = CachedProfile(hash, updated)
                updated
            }
        }
        val fresh = toModel()
        profileCache[id] = CachedProfile(hash, fresh)
        return fresh
    }

    val allProfiles: Flow<List<ProxyProfile>> = profileDao.getAllProfiles().map { entities ->
        entities.map { it.toCachedModel() }
    }

    suspend fun getProfileById(id: String): ProxyProfile? {
        return profileDao.getProfileById(id)?.toCachedModel()
    }

    suspend fun saveProfile(profile: ProxyProfile) {
        profileDao.insertProfile(profile.toEntity())
    }

    suspend fun saveProfiles(profiles: List<ProxyProfile>) {
        profileDao.insertProfiles(profiles.map { it.toEntity() })
    }

    suspend fun updatePing(id: String, pingMs: Long) {
        profileDao.updatePing(id, pingMs)
    }

    suspend fun updatePingsBatch(updates: Map<String, Long>) {
        profileDao.updatePingsBatch(updates)
    }

    suspend fun toggleFavorite(id: String) {
        val entity = profileDao.getProfileById(id) ?: return
        profileDao.updateProfile(entity.copy(isFavorite = !entity.isFavorite))
    }

    suspend fun deleteProfile(id: String) {
        profileCache.remove(id)
        profileDao.deleteProfileById(id)
    }

    suspend fun deleteProfilesBySubscription(subId: String) {
        profileCache.clear()
        profileDao.deleteProfilesBySubscription(subId)
    }

    suspend fun getProfilesBySubscription(subId: String): List<ProxyProfile> {
        return profileDao.getProfilesBySubscription(subId).map { it.toCachedModel() }
    }

    suspend fun deleteAll() {
        profileCache.clear()
        profileDao.deleteAllProfiles()
    }

    private fun ProfileEntity.toModel(): ProxyProfile {
        val plainSecret = SecretBox.decrypt(encryptedSecret)
        val tls = try {
            json.decodeFromString<TlsConfig>(tlsJson)
        } catch (e: Exception) {
            TlsConfig()
        }
        val transport = try {
            json.decodeFromString<TransportConfig>(transportJson)
        } catch (e: Exception) {
            TransportConfig()
        }

        return ProxyProfile(
            id = id,
            name = name,
            protocol = ProtocolType.valueOf(protocol),
            server = server,
            port = port,
            authSecret = plainSecret,
            encryption = encryption,
            tls = tls,
            transport = transport,
            packetEncoding = packetEncoding,
            headerType = headerType,
            detour = detour,
            subscriptionId = subscriptionId,
            isFavorite = isFavorite,
            lastPingMs = lastPingMs,
            updatedAt = updatedAt
        )
    }

    private fun ProxyProfile.toEntity(): ProfileEntity {
        val encryptedSecret = SecretBox.encrypt(authSecret)
        return ProfileEntity(
            id = id,
            name = name,
            protocol = protocol.name,
            server = server,
            port = port,
            encryptedSecret = encryptedSecret,
            encryption = encryption,
            tlsJson = json.encodeToString(TlsConfig.serializer(), tls),
            transportJson = json.encodeToString(TransportConfig.serializer(), transport),
            detour = detour,
            subscriptionId = subscriptionId,
            isFavorite = isFavorite,
            lastPingMs = lastPingMs,
            updatedAt = updatedAt,
            packetEncoding = packetEncoding,
            headerType = headerType
        )
    }
}
