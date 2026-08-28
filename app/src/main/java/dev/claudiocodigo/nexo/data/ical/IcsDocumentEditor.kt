package dev.claudiocodigo.nexo.data.ical

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Surgical, lossless editor for iCalendar VEVENT components.
 *
 * Rules:
 * - Selects the target VEVENT by UID and optional RECURRENCE-ID;
 * - Only modifies DESCRIPTION, SEQUENCE, DTSTAMP and LAST-MODIFIED;
 * - Preserves exact unchanged lines, VTIMEZONE, PARTICIPANTS, ALARMS and unknown properties;
 * - Folds newly generated lines per RFC 5545 (max 75 octets with CRLF + space);
 * - Properly escapes text characters (\, ;, ,, and newlines).
 */
object IcsDocumentEditor {

    private val UTC_FORMAT = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun updateVEvent(
        rawIcs: String,
        targetUid: String?,
        targetRecurrenceId: String?,
        newDescription: String,
        nowMillis: Long = System.currentTimeMillis(),
        incrementSequence: Boolean = true
    ): String {
        val doc = IcsDocument.parse(rawIcs)
        val lines = doc.lines
        val outputLines = mutableListOf<String>()

        var inTargetEvent = false
        var insideAnyEvent = false
        var eventStartIdx = -1
        var eventLines = mutableListOf<String>()
        var foundTarget = false

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            if (trimmed.equals("BEGIN:VEVENT", ignoreCase = true)) {
                insideAnyEvent = true
                eventStartIdx = i
                eventLines = mutableListOf(line)
                i++
                continue
            }

            if (insideAnyEvent) {
                eventLines.add(line)
                if (trimmed.equals("END:VEVENT", ignoreCase = true)) {
                    insideAnyEvent = false
                    val matches = matchesTarget(eventLines, targetUid, targetRecurrenceId)
                    if (matches && !foundTarget) {
                        foundTarget = true
                        outputLines.addAll(modifyEvent(eventLines, newDescription, nowMillis, incrementSequence))
                    } else {
                        outputLines.addAll(eventLines)
                    }
                }
                i++
                continue
            }

            outputLines.add(line)
            i++
        }

        require(foundTarget) {
            "VEVENT alvo não encontrado no ICS; publicação cancelada para evitar envio sem alterações"
        }
        return outputLines.joinToString(doc.lineEnding)
    }

    fun createProvisionalIcs(
        uid: String,
        summary: String,
        description: String,
        startMillis: Long,
        endMillis: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        val dtStamp = UTC_FORMAT.format(Date(nowMillis))
        val dtStart = UTC_FORMAT.format(Date(startMillis))
        val dtEnd = UTC_FORMAT.format(Date(endMillis))

        val descFolded = foldContentLine("DESCRIPTION:" + escapeIcsText(description))
        val summaryFolded = foldContentLine("SUMMARY:" + escapeIcsText(summary))

        return buildString {
            appendLine("BEGIN:VCALENDAR")
            appendLine("VERSION:2.0")
            appendLine("PRODID:-//Nexo//Nexo Field Service//PT")
            appendLine("BEGIN:VEVENT")
            appendLine("UID:$uid")
            appendLine("DTSTAMP:$dtStamp")
            appendLine("CREATED:$dtStamp")
            appendLine("LAST-MODIFIED:$dtStamp")
            appendLine("SEQUENCE:0")
            appendLine("DTSTART:$dtStart")
            appendLine("DTEND:$dtEnd")
            appendLine(summaryFolded)
            appendLine(descFolded)
            appendLine("STATUS:CONFIRMED")
            append("END:VEVENT\r\nEND:VCALENDAR")
        }
    }

    private fun matchesTarget(eventLines: List<String>, targetUid: String?, targetRecurrenceId: String?): Boolean {
        if (targetUid == null) return true // Match first event if no UID specified

        val fullText = eventLines.joinToString("\n")
        val uidMatch = Regex("""(?m)^UID:(.*)$""").find(fullText)?.groupValues?.get(1)?.trim()
        if (uidMatch != targetUid) return false

        val recIdMatch = Regex("""(?m)^RECURRENCE-ID[^:]*:(.*)$""").find(fullText)?.groupValues?.get(1)?.trim()
        return if (targetRecurrenceId.isNullOrEmpty()) {
            recIdMatch.isNullOrEmpty()
        } else {
            recIdMatch == targetRecurrenceId
        }
    }

    private fun modifyEvent(
        eventLines: List<String>,
        newDescription: String,
        nowMillis: Long,
        incrementSequence: Boolean
    ): List<String> {
        val filtered = mutableListOf<String>()
        var currentSequence = 0
        val timestamp = UTC_FORMAT.format(Date(nowMillis))

        var i = 0
        while (i < eventLines.size) {
            val line = eventLines[i]
            val isFoldedContinuation = line.startsWith(" ") || line.startsWith("\t")

            if (!isFoldedContinuation) {
                val colonIdx = line.indexOf(':')
                val propName = if (colonIdx > 0) line.substring(0, colonIdx).split(';')[0].uppercase() else ""

                if (propName == "DESCRIPTION" || propName == "LAST-MODIFIED" || propName == "DTSTAMP") {
                    // Skip this property and its folded continuation lines
                    i++
                    while (i < eventLines.size && (eventLines[i].startsWith(" ") || eventLines[i].startsWith("\t"))) {
                        i++
                    }
                    continue
                }

                if (propName == "SEQUENCE") {
                    val seqVal = line.substring(colonIdx + 1).trim().toIntOrNull() ?: 0
                    currentSequence = seqVal
                    i++
                    while (i < eventLines.size && (eventLines[i].startsWith(" ") || eventLines[i].startsWith("\t"))) {
                        i++
                    }
                    continue
                }
            }

            filtered.add(line)
            i++
        }

        // Now insert new DESCRIPTION, SEQUENCE, DTSTAMP, LAST-MODIFIED right before END:VEVENT
        val endIdx = filtered.indexOfLast { it.trim().equals("END:VEVENT", ignoreCase = true) }
        val insertIdx = if (endIdx >= 0) endIdx else filtered.size

        val newSeq = if (incrementSequence) currentSequence + 1 else currentSequence
        val additions = mutableListOf<String>()
        additions.add("DTSTAMP:$timestamp")
        additions.add("LAST-MODIFIED:$timestamp")
        additions.add("SEQUENCE:$newSeq")

        val foldedDesc = foldContentLine("DESCRIPTION:" + escapeIcsText(newDescription))
        foldedDesc.lines().forEach { additions.add(it) }

        filtered.addAll(insertIdx, additions)
        return filtered
    }

    fun escapeIcsText(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace("\r\n", "\\n")
            .replace("\n", "\\n")
            .replace("\r", "\\n")
    }

    fun foldContentLine(line: String, maxOctets: Int = 75): String {
        val bytes = line.toByteArray(Charsets.UTF_8)
        if (bytes.size <= maxOctets) return line

        val result = StringBuilder()
        var currentLineOctets = 0
        var i = 0

        while (i < line.length) {
            val char = line[i]
            val charBytes = char.toString().toByteArray(Charsets.UTF_8).size

            if (currentLineOctets + charBytes > maxOctets) {
                result.append("\r\n ")
                currentLineOctets = 1 // counting the leading space
            }

            result.append(char)
            currentLineOctets += charBytes
            i++
        }

        return result.toString()
    }
}
