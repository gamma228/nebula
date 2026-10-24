package app.nebula.vpn.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_themes")
data class ThemeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val configJson: String,
    val isPreset: Boolean,
    val createdAt: Long = System.currentTimeMillis()
)
