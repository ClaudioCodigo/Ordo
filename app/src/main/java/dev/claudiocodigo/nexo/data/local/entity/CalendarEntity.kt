package dev.claudiocodigo.nexo.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import dev.claudiocodigo.nexo.domain.caldav.CalendarInfo

/**
 * A calendar collection discovered on the server. Mirroring the discovery
 * result lets the app re-list the user's calendars offline and remember the
 * selected work calendar without a network round-trip.
 */
@Entity(
    tableName = "calendars",
    primaryKeys = ["accountId", "href"],
    indices = [Index("accountId")]
)
data class CalendarEntity(
    val accountId: String,
    val href: String,
    val displayName: String?,
    val description: String?,
    val color: String?,
    val supportsVeEvent: Boolean,
    val hasWritePrivilege: Boolean,
    val syncToken: String?,
    val isSelected: Boolean,
    val updatedAt: Long
) {
    fun toDomain() = CalendarInfo(
        href = href,
        displayName = displayName,
        description = description,
        color = color,
        supportsVeEvent = supportsVeEvent,
        hasWritePrivilege = hasWritePrivilege,
        syncToken = syncToken
    )

    companion object {
        fun fromDomain(accountId: String, info: CalendarInfo, isSelected: Boolean, updatedAt: Long) =
            CalendarEntity(
                accountId = accountId,
                href = info.href,
                displayName = info.displayName,
                description = info.description,
                color = info.color,
                supportsVeEvent = info.supportsVeEvent,
                hasWritePrivilege = info.hasWritePrivilege,
                syncToken = info.syncToken,
                isSelected = isSelected,
                updatedAt = updatedAt
            )
    }
}
