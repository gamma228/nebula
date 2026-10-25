package app.nebula.vpn.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.nebula.vpn.data.database.entity.ProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    @Query("SELECT * FROM profiles ORDER BY isFavorite DESC, updatedAt DESC")
    fun getAllProfiles(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getProfileById(id: String): ProfileEntity?

    @Query("SELECT * FROM profiles WHERE subscriptionId = :subId")
    suspend fun getProfilesBySubscription(subId: String): List<ProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfiles(profiles: List<ProfileEntity>)

    @Update
    suspend fun updateProfile(profile: ProfileEntity)

    @Query("UPDATE profiles SET lastPingMs = :pingMs WHERE id = :id")
    suspend fun updatePing(id: String, pingMs: Long)

    @androidx.room.Transaction
    suspend fun updatePingsBatch(updates: Map<String, Long>) {
        for ((id, ping) in updates) {
            updatePing(id, ping)
        }
    }

    @Delete
    suspend fun deleteProfile(profile: ProfileEntity)

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun deleteProfileById(id: String)

    @Query("DELETE FROM profiles WHERE subscriptionId = :subId")
    suspend fun deleteProfilesBySubscription(subId: String)

    @Query("DELETE FROM profiles")
    suspend fun deleteAllProfiles()
}
