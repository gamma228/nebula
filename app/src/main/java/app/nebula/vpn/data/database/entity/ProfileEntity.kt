package app.nebula.vpn.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val protocol: String,
    val server: String,
    val port: Int,
    val encryptedSecret: String, // Keystore-encrypted auth secret/UUID
    val encryption: String,
    val tlsJson: String,
    val transportJson: String,
    val detour: String?,
    val subscriptionId: String?,
    val isFavorite: Boolean,
    val lastPingMs: Long?,
    val updatedAt: Long,
    val packetEncoding: String = "xudp",
    val headerType: String = ""
)
