package app.nebula.vpn.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "proxy_groups")
data class GroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val profileIdsJson: String,
    val selectedProfileId: String?,
    val testUrl: String,
    val testIntervalSeconds: Int
)
