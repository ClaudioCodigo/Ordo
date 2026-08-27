package dev.claudiocodigo.nexo.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The single Nextcloud account configured on the device. Stores only non-secret
 * identity material; the application password lives in the [CredentialStore]
 * and never in the database.
 */
@Entity(tableName = "calendar_accounts")
data class CalendarAccountEntity(
    @PrimaryKey val id: String,
    val server: String,
    val user: String,
    val createdAt: Long,
    val updatedAt: Long
)
