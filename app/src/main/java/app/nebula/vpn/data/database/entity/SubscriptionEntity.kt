package app.nebula.vpn.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey val id: String,
    val url: String,
    val title: String,
    val uploadBytes: Long?,
    val downloadBytes: Long?,
    val totalBytes: Long?,
    val expireEpochSeconds: Long?,
    val updatedAt: Long,
    val autoUpdateIntervalHours: Int = 24
)
