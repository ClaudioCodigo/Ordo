package dev.claudiocodigo.nexo.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import dev.claudiocodigo.nexo.domain.caldav.EventColor
import dev.claudiocodigo.nexo.domain.caldav.RemoteEvent

/**
 * The read-only mirror of a calendar event. Kept fully separate from
 * [ServiceOrderEntity]: a change received from the calendar must never
 * overwrite fields the technician is editing locally.
 *
 * Identity is account + calendar + href (CAL-01). `uid` is indexed but is NOT
 * a global unique key: the same `uid` can legitimately appear under two
 * different `href`s, and both must be preserved (CAL-05).
 */
@Entity(
    tableName = "remote_events",
    primaryKeys = ["accountId", "calendarHref", "href"],
    indices = [
        Index("uid"),
        Index("start"),
        Index("calendarHref")
    ]
)
data class RemoteEventEntity(
    val accountId: String,
    val calendarHref: String,
    val href: String,
    val uid: String,
    val etag: String?,
    val sequence: Int?,
    val rawIcs: String,
    val summary: String?,
    val description: String?,
    val location: String?,
    val start: Long?,
    val end: Long?,
    val allDay: Boolean,
    /** [EventColor] stored as its `.name`. */
    val color: String,
    val rawEventColor: String?,
    val timeZone: String?,
    val recurrenceText: String?,
    val lastModified: Long?,
    val lastSyncMillis: Long
) {
    fun toDomain() = RemoteEvent(
        accountId = accountId,
        calendarHref = calendarHref,
        href = href,
        uid = uid,
        etag = etag,
        sequence = sequence,
        rawIcs = rawIcs,
        summary = summary,
        description = description,
        location = location,
        start = start,
        end = end,
        allDay = allDay,
        color = EventColor.entries.firstOrNull { it.name == color } ?: EventColor.NAO_CLASSIFICADO,
        rawEventColor = rawEventColor,
        timeZone = timeZone,
        recurrenceText = recurrenceText,
        lastModified = lastModified,
        lastSyncMillis = lastSyncMillis
    )

    companion object {
        fun fromDomain(event: RemoteEvent) = RemoteEventEntity(
            accountId = event.accountId,
            calendarHref = event.calendarHref,
            href = event.href,
            uid = event.uid,
            etag = event.etag,
            sequence = event.sequence,
            rawIcs = event.rawIcs,
            summary = event.summary,
            description = event.description,
            location = event.location,
            start = event.start,
            end = event.end,
            allDay = event.allDay,
            color = event.color.name,
            rawEventColor = event.rawEventColor,
            timeZone = event.timeZone,
            recurrenceText = event.recurrenceText,
            lastModified = event.lastModified,
            lastSyncMillis = event.lastSyncMillis
        )
    }
}
