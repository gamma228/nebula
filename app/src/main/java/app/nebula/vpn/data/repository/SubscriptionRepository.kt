package app.nebula.vpn.data.repository

import app.nebula.vpn.core.model.Result
import app.nebula.vpn.core.parser.SubscriptionFetcher
import app.nebula.vpn.data.database.dao.SubscriptionDao
import app.nebula.vpn.data.database.entity.SubscriptionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubscriptionRepository @Inject constructor(
    private val subscriptionDao: SubscriptionDao,
    private val profileRepository: ProfileRepository,
    private val settingsRepository: SettingsRepository
) {

    val allSubscriptions: Flow<List<SubscriptionEntity>> = subscriptionDao.getAllSubscriptions()

    suspend fun getSubscriptionById(id: String): SubscriptionEntity? {
        return subscriptionDao.getSubscriptionById(id)
    }

    suspend fun addSubscription(
        url: String,
        customTitle: String? = null
    ): Result<SubscriptionEntity> {
        val fetchResult = SubscriptionFetcher.fetch(url)
        if (fetchResult is Result.Error) {
            return fetchResult
        }

        val data = (fetchResult as Result.Success).data
        val subId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val title = customTitle?.ifBlank { null } ?: data.title

        val entity = SubscriptionEntity(
            id = subId,
            url = url.trim(),
            title = title,
            uploadBytes = data.uploadBytes,
            downloadBytes = data.downloadBytes,
            totalBytes = data.totalBytes,
            expireEpochSeconds = data.expireEpochSeconds,
            updatedAt = now,
            autoUpdateIntervalHours = data.updateIntervalHours
        )

        subscriptionDao.insertSubscription(entity)

        // Assign subscriptionId to all parsed profiles
        val linkedProfiles = data.profiles.map { profile ->
            profile.copy(subscriptionId = subId)
        }
        profileRepository.saveProfiles(linkedProfiles)

        // If no profile is selected currently, select the first profile
        val currentSelected = settingsRepository.selectedProfileId.first()
        if (currentSelected == null && linkedProfiles.isNotEmpty()) {
            settingsRepository.setSelectedProfileId(linkedProfiles.first().id)
        }

        return Result.Success(entity)
    }

    suspend fun refreshSubscription(id: String): Result<SubscriptionEntity> {
        val existing = subscriptionDao.getSubscriptionById(id)
            ?: return Result.Error(IllegalArgumentException("Subscription not found: $id"))

        val fetchResult = SubscriptionFetcher.fetch(existing.url)
        if (fetchResult is Result.Error) {
            return fetchResult
        }

        val data = (fetchResult as Result.Success).data
        val now = System.currentTimeMillis()
        val updated = existing.copy(
            title = existing.title.ifBlank { data.title },
            uploadBytes = data.uploadBytes,
            downloadBytes = data.downloadBytes,
            totalBytes = data.totalBytes,
            expireEpochSeconds = data.expireEpochSeconds,
            updatedAt = now,
            autoUpdateIntervalHours = data.updateIntervalHours
        )

        subscriptionDao.updateSubscription(updated)

        // Remove old profiles for this subscription and insert updated profiles
        profileRepository.deleteProfilesBySubscription(id)
        val linkedProfiles = data.profiles.map { profile ->
            profile.copy(subscriptionId = id)
        }
        profileRepository.saveProfiles(linkedProfiles)

        // If the selected profile was deleted, select first available
        val currentSelected = settingsRepository.selectedProfileId.first()
        if (currentSelected != null && profileRepository.getProfileById(currentSelected) == null) {
            val fallback = linkedProfiles.firstOrNull()?.id
            settingsRepository.setSelectedProfileId(fallback)
        }

        return Result.Success(updated)
    }

    suspend fun deleteSubscription(id: String) {
        val sub = subscriptionDao.getSubscriptionById(id)
        if (sub != null) {
            subscriptionDao.deleteSubscription(sub)
            profileRepository.deleteProfilesBySubscription(id)

            val currentSelected = settingsRepository.selectedProfileId.first()
            if (currentSelected != null && profileRepository.getProfileById(currentSelected) == null) {
                val remaining = profileRepository.allProfiles.first().firstOrNull()?.id
                settingsRepository.setSelectedProfileId(remaining)
            }
        }
    }
}
