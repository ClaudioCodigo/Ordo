package dev.claudiocodigo.nexo.data.ical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertThrows

class IcsDocumentEditorTest {

    @Test
    fun updateVEvent_replacesDescriptionAndIncrementsSequenceWhilePreservingVtimezoneAndLocation() {
        val originalIcs = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Nextcloud//Calendar//EN
            BEGIN:VTIMEZONE
            TZID:America/Sao_Paulo
            BEGIN:STANDARD
            DTSTART:19700101T000000
            TZOFFSETFROM:-0300
            TZOFFSETTO:-0300
            TZNAME:BRT
            END:STANDARD
            END:VTIMEZONE
            BEGIN:VEVENT
            UID:evt-123
            DTSTAMP:20260825T100000Z
            DTSTART;TZID=America/Sao_Paulo:20260826T090000
            DTEND;TZID=America/Sao_Paulo:20260826T100000
            SUMMARY:Manutenção Nobreak
            LOCATION:Sala de Servidores
            COLOR:#008000
            SEQUENCE:2
            DESCRIPTION:Descrição antiga
            X-CUSTOM-PROP:custom_value
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val updatedIcs = IcsDocumentEditor.updateVEvent(
            rawIcs = originalIcs,
            targetUid = "evt-123",
            targetRecurrenceId = null,
            newDescription = "Nova descrição com acentuação e detalhes técnicos.",
            nowMillis = 1735689600000L,
            incrementSequence = true
        )

        // Preserves exact existing VTIMEZONE, SUMMARY, LOCATION, COLOR, X-CUSTOM-PROP
        assertTrue(updatedIcs.contains("BEGIN:VTIMEZONE"))
        assertTrue(updatedIcs.contains("TZID:America/Sao_Paulo"))
        assertTrue(updatedIcs.contains("SUMMARY:Manutenção Nobreak"))
        assertTrue(updatedIcs.contains("LOCATION:Sala de Servidores"))
        assertTrue(updatedIcs.contains("COLOR:#008000"))
        assertTrue(updatedIcs.contains("X-CUSTOM-PROP:custom_value"))

        // Replaces old description and increments sequence
        assertFalse(updatedIcs.contains("Descrição antiga"))
        assertTrue(updatedIcs.contains("DESCRIPTION:Nova descrição com acentuação e detalhes técnicos."))
        assertTrue(updatedIcs.contains("SEQUENCE:3"))
    }

    @Test
    fun foldContentLine_foldsLongLinesAtMax75Octets() {
        val longText = "DESCRIPTION:Esta é uma linha extremamente longa criada para testar a dobra correta de caracteres UTF-8 de acordo com a especificação RFC 5545."
        val folded = IcsDocumentEditor.foldContentLine(longText, maxOctets = 75)

        assertTrue(folded.contains("\r\n "))
        val lines = folded.split("\r\n")
        assertTrue(lines.size >= 2)
        lines.forEach { line ->
            assertTrue(line.toByteArray(Charsets.UTF_8).size <= 75)
        }
    }

    @Test
    fun updateVEvent_rejectsMissingUidInsteadOfPublishingOriginalIcs() {
        val originalIcs = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:uid-real-diferente-do-arquivo
            DESCRIPTION:Descrição antiga
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        assertThrows(IllegalArgumentException::class.java) {
            IcsDocumentEditor.updateVEvent(
                rawIcs = originalIcs,
                targetUid = "nome-do-arquivo",
                targetRecurrenceId = null,
                newDescription = "Descrição nova"
            )
        }
    }
}
