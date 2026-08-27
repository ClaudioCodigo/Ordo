package dev.claudiocodigo.nexo.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import dev.claudiocodigo.nexo.domain.serviceorder.RemoteOccurrenceKey

/**
 * Individual occurrence of a remote calendar event (handles recurring events and master instances).
 */
@Entity(
    tableName = "remote_event_occurrences",
    primaryKeys = ["accountId", "calendarHref", "eventHref", "recurrenceId"],
    indices = [
        Index("uid"),
        Index("start"),
        Index("calendarHref")
    ]
)
data class RemoteEventOccurrenceEntity(
    val accountId: String,
    val calendarHref: String,
    val eventHref: String,
    val recurrenceId: String,
    val uid: String,
    val start: Long?,
    val end: Long?,
    val allDay: Boolean,
    val summary: String?,
    val description: String?,
    val color: String,
    val lastSyncMillis: Long
) {
    fun toOccurrenceKey() = RemoteOccurrenceKey(
        accountId = accountId,
        calendarHref = calendarHref,
        eventHref = eventHref,
        recurrenceId = recurrenceId.takeIf { it.isNotEmpty() }
    )
}
