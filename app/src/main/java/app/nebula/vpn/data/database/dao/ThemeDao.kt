package app.nebula.vpn.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.nebula.vpn.data.database.entity.ThemeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ThemeDao {

    @Query("SELECT * FROM saved_themes ORDER BY createdAt DESC")
    fun getAllThemes(): Flow<List<ThemeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTheme(theme: ThemeEntity)

    @Delete
    suspend fun deleteTheme(theme: ThemeEntity)

    @Query("DELETE FROM saved_themes")
    suspend fun deleteAllThemes()
}
