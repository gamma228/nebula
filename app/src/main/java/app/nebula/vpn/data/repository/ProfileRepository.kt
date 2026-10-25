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

    val allProfiles: Flow<List<ProxyProfile>> = profileDao.getAllProfiles().map { entities ->
        entities.map { it.toModel() }
    }

    suspend fun getProfileById(id: String): ProxyProfile? {
        return profileDao.getProfileById(id)?.toModel()
    }

    suspend fun saveProfile(profile: ProxyProfile) {
        profileDao.insertProfile(profile.toEntity())
    }

    suspend fun saveProfiles(profiles: List<ProxyProfile>) {
        profileDao.insertProfiles(profiles.map { it.toEntity() })
    }

    suspend fun updatePing(id: String, pingMs: Long) {
        val entity = profileDao.getProfileById(id) ?: return
        profileDao.updateProfile(entity.copy(lastPingMs = pingMs))
    }

    suspend fun toggleFavorite(id: String) {
        val entity = profileDao.getProfileById(id) ?: return
        profileDao.updateProfile(entity.copy(isFavorite = !entity.isFavorite))
    }

    suspend fun deleteProfile(id: String) {
        profileDao.deleteProfileById(id)
    }

    suspend fun deleteProfilesBySubscription(subId: String) {
        profileDao.deleteProfilesBySubscription(subId)
    }

    suspend fun getProfilesBySubscription(subId: String): List<ProxyProfile> {
        return profileDao.getProfilesBySubscription(subId).map { it.toModel() }
    }

    suspend fun deleteAll() {
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
            updatedAt = updatedAt
        )
    }
}
