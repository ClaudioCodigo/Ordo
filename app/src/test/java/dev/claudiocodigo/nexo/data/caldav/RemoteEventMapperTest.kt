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
    fun `falls back to href as uid when ICS has none`() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            DTSTART:20260826T090000Z
            SUMMARY:Sem UID
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val event = RemoteEventMapper.map(
            EventResource(href = "/cal/no-uid.ics", etag = "etag", ics = ics),
            "acct-1", "/cal/", 5L
        )!!
        assertEquals("/cal/no-uid.ics", event.uid)
    }

    @Test
    fun `returns null when the resource contains no VEVENT`() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VTODO
            SUMMARY:Tarefa
            END:VTODO
            END:VCALENDAR
        """.trimIndent()

        val event = RemoteEventMapper.map(
            EventResource(href = "/cal/todo.ics", etag = "e", ics = ics),
            "acct-1", "/cal/", 5L
        )
        assertNull(event)
    }

    @Test
    fun `green color maps to validated`() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:g
            DTSTART:20260826T090000Z
            COLOR:#008000
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val event = RemoteEventMapper.map(
            EventResource("/cal/g.ics", "e", ics), "a", "/cal/", 1L
        )!!
        assertEquals(EventColor.VALIDADO, event.color)
    }
}
