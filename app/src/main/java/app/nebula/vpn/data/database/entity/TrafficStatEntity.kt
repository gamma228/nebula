package app.nebula.vpn.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "traffic_stats")
data class TrafficStatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val uploadBytes: Long,
    val downloadBytes: Long
)
