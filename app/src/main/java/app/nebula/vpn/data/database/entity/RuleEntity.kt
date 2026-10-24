package app.nebula.vpn.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "routing_rules")
data class RuleEntity(
    @PrimaryKey val id: String,
    val type: String,
    val value: String,
    val action: String,
    val targetOutbound: String?,
    val priority: Int,
    val isEnabled: Boolean
)
