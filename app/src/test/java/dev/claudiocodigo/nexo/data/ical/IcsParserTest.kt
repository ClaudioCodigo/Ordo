package dev.claudiocodigo.nexo.data.ical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IcsParserTest {

    @Test
    fun `parses fields with timezone escapes and accents`() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Nextcloud//Nextcloud Calendar 4.0//EN
            BEGIN:VEVENT
            UID:abc-123
            DTSTAMP:20260825T120000Z
            DTSTART;TZID=America/Sao_Paulo:20260826T090000
            DTEND;TZID=America/Sao_Paulo:20260826T093000
            SUMMARY:OS 15428 - Manutenção
            DESCRIPTION:Linha um\nLinha dois\\ continua\, com vírgula
            LOCATION:Unidade Centro
            SEQUENCE:2
            COLOR:red
            RRULE:FREQ=WEEKLY;BYDAY=MO
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val cal = IcsParser.parse(ics)

        assertEquals("2.0", cal.version)
        assertTrue(cal.prodId!!.contains("Nextcloud"))
        assertEquals(1, cal.events.size)

        val e = cal.events[0]
        assertEquals("abc-123", e.uid)
        assertEquals("OS 15428 - Manutenção", e.summary)
        assertEquals("Linha um\nLinha dois\\ continua, com vírgula", e.description)
        assertEquals("Unidade Centro", e.location)
        assertEquals("red", e.color)
        assertEquals(2, e.sequence)
        assertEquals("FREQ=WEEKLY;BYDAY=MO", e.recurrenceRule)

        val start = e.dtStart!!
        assertEquals(IcsDateTimeType.DATE_TIME_ZONE, start.type)
        assertEquals("America/Sao_Paulo", start.zoneId)
        assertNotNull(start.epochMillis)
    }

    @Test
    fun `unfolds folded lines`() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:fold-1
            DTSTAMP:20260825T120000Z
            DTSTART;VALUE=DATE:20260826
            SUMMARY:Linha muito longa que ocupa mais que s
             ete d5 posições e precisa ser dobrada
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val cal = IcsParser.parse(ics)
        val e = cal.events[0]

        assertEquals("Linha muito longa que ocupa mais que sete d5 posições e precisa ser dobrada", e.summary)
        assertTrue(e.allDay)
        assertEquals(IcsDateTimeType.DATE, e.dtStart!!.type)
        assertNotNull(e.dtStart!!.epochMillis)
    }

    @Test
    fun `parses utc datetime and preserves raw ics`() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            COLOR:#008000
            BEGIN:VEVENT
            UID:utc-1
            DTSTAMP:20260825T120000Z
            DTSTART:20260826T140000Z
            DTEND:20260826T145500Z
            SUMMARY:Reunião
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val cal = IcsParser.parse(ics)

        assertEquals("#008000", cal.calendarColor)
        val e = cal.events[0]
        assertEquals(IcsDateTimeType.DATE_TIME_UTC, e.dtStart!!.type)
        assertNotNull(e.dtStart!!.epochMillis)
        assertEquals(ics, cal.rawIcs)
    }

    @Test
    fun `parses recurrence exceptions as the raw values`() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:rec-1
            DTSTAMP:20260825T120000Z
            DTSTART;TZID=America/Sao_Paulo:20260831T090000
            RRULE:FREQ=WEEKLY
            EXDATE;TZID=America/Sao_Paulo:20260907T090000
            RECURRENCE-ID;TZID=America/Sao_Paulo:20260907T090000
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val e = IcsParser.parse(ics).events[0]

        assertEquals("FREQ=WEEKLY", e.recurrenceRule)
        assertEquals(1, e.recurrenceExceptionDates.size)
        assertEquals(IcsDateTimeType.DATE_TIME_ZONE, e.recurrenceExceptionDates[0].type)
        assertNotNull(e.recurrenceId)
    }

    @Test
    fun `quoted printable and multi-line description are decoded`() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:qp-1
            DTSTAMP:20260825T120000Z
            DTSTART:20260826T090000Z
            SUMMARY:QP
            DESCRIPTION;ENCODING=QUOTED-PRINTABLE:A=C3=A7=C3=A3o=0A=20com acento
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val e = IcsParser.parse(ics).events[0]

        // =C3=A7 -> ç, =C3=A3 -> ã, =0A -> newline, =20 -> space
        assertEquals("Ação\n com acento", e.description)
        assertNull(e.dtEnd)
    }
}
