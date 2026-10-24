package app.nebula.vpn.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.nebula.vpn.data.database.entity.RuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {

    @Query("SELECT * FROM routing_rules ORDER BY priority ASC")
    fun getAllRules(): Flow<List<RuleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: RuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRules(rules: List<RuleEntity>)

    @Update
    suspend fun updateRule(rule: RuleEntity)

    @Delete
    suspend fun deleteRule(rule: RuleEntity)

    @Query("DELETE FROM routing_rules WHERE id = :id")
    suspend fun deleteRuleById(id: String)

    @Query("DELETE FROM routing_rules")
    suspend fun deleteAllRules()
}
