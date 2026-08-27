package dev.claudiocodigo.nexo.data.caldav

import dev.claudiocodigo.nexo.domain.caldav.EventColor
import dev.claudiocodigo.nexo.domain.caldav.EventResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteEventMapperTest {

    @Test
    fun `maps an ICS resource preserving raw and interpreting color`() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:uid-1
            DTSTAMP:20260825T120000Z
            DTSTART;TZID=America/Sao_Paulo:20260826T090000
            DTEND;TZID=America/Sao_Paulo:20260826T093000
            SUMMARY:Manutenção
            DESCRIPTION:Troca de bateria
            LOCATION:Unidade Centro
            SEQUENCE:2
            COLOR:#B22222
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val event = RemoteEventMapper.map(
            EventResource(href = "/cal/e1.ics", etag = "\"abc\"", ics = ics),
            accountId = "acct-1",
            calendarHref = "/cal/",
            nowMillis = 1000L
        )!!

        assertEquals("uid-1", event.uid)
        assertEquals("Manutenção", event.summary)
        assertEquals("Troca de bateria", event.description)
        assertEquals("Unidade Centro", event.location)
        assertEquals("\"abc\"", event.etag)
        assertEquals(2, event.sequence)
        assertEquals(EventColor.REQUER_ATENCAO, event.color)
        assertEquals("#B22222", event.rawEventColor)
        assertNotNull(event.start)
        assertNotNull(event.end)
        assertEquals(ics, event.rawIcs)
        assertEquals(1000L, event.lastSyncMillis)
    }

    @Test
    fun `maps multiple occurrences from recurring series with exceptions`() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:series-1
            DTSTART:20260826T090000Z
            SUMMARY:Reunião Semanal
            RRULE:FREQ=WEEKLY
            END:VEVENT
            BEGIN:VEVENT
            UID:series-1
            RECURRENCE-ID:20260902T090000Z
            SUMMARY:Reunião Semanal - Exceção
            DESCRIPTION:Pauta especial
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val occurrences = RemoteEventMapper.mapOccurrences(
            EventResource(href = "/cal/series.ics", etag = "\"etag\"", ics = ics),
            accountId = "acct-1",
            calendarHref = "/cal/",
            nowMillis = 2000L
        )

        assertEquals(2, occurrences.size)
        assertEquals("", occurrences[0].recurrenceId)
        assertEquals("Reunião Semanal", occurrences[0].summary)

        assertEquals("20260902T090000Z", occurrences[1].recurrenceId)
        assertEquals("Reunião Semanal - Exceção", occurrences[1].summary)
    }
}
