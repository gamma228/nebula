package app.nebula.vpn.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import app.nebula.vpn.data.database.entity.TrafficStatEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrafficStatDao {

    @Query("SELECT * FROM traffic_stats ORDER BY timestamp DESC LIMIT 100")
    fun getRecentStats(): Flow<List<TrafficStatEntity>>

    @Insert
    suspend fun insertStat(stat: TrafficStatEntity)

    @Query("SELECT SUM(uploadBytes) FROM traffic_stats")
    suspend fun getTotalUpload(): Long?

    @Query("SELECT SUM(downloadBytes) FROM traffic_stats")
    suspend fun getTotalDownload(): Long?

    @Query("DELETE FROM traffic_stats")
    suspend fun deleteAllStats()
}
