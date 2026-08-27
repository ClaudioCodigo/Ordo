package dev.claudiocodigo.nexo.data.caldav

import dev.claudiocodigo.nexo.data.ical.IcsParser
import dev.claudiocodigo.nexo.domain.caldav.ColorClassifier
import dev.claudiocodigo.nexo.domain.caldav.EventResource
import dev.claudiocodigo.nexo.domain.caldav.RemoteEvent

/**
 * Maps a raw Calendar resource (ICS) into the [RemoteEvent] mirror. The raw
 * ICS, SUMMARY and DESCRIPTION are always preserved; extraction is best-effort
 * and never discards the raw payload (CAL-01, CAL-02).
 */
object RemoteEventMapper {

    fun map(
        resource: EventResource,
        accountId: String,
        calendarHref: String,
        nowMillis: Long
    ): RemoteEvent? {
        val calendar = IcsParser.parse(resource.ics)
        // If a recurrence set has a master event and exceptions, prefer the
        // master (no RECURRENCE-ID); otherwise take the first VEVENT present.
        val event = calendar.events.firstOrNull { it.recurrenceId == null }
            ?: calendar.events.firstOrNull()
            ?: return null

        return RemoteEvent(
            accountId = accountId,
            calendarHref = calendarHref,
            href = resource.href,
            uid = event.uid ?: resource.href,
            etag = resource.etag,
            sequence = event.sequence,
            rawIcs = resource.ics,
            summary = event.summary,
            description = event.description,
            location = event.location,
            start = event.dtStart?.epochMillis,
            end = event.dtEnd?.epochMillis,
            allDay = event.allDay,
            color = ColorClassifier.classify(event.color),
            rawEventColor = event.color,
            timeZone = event.dtStart?.zoneId,
            recurrenceText = listOfNotNull(event.recurrenceRule).joinToString("; ").ifBlank { null },
            lastModified = event.lastModified?.epochMillis,
            lastSyncMillis = nowMillis
        )
    }
}
