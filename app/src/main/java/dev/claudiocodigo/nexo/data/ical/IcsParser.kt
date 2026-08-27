package dev.claudiocodigo.nexo.data.ical

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.zone.ZoneRulesException

/**
 * A minimal RFC 5545 reader for the fields Nexo needs in Fase 2.
 *
 * It is intentionally small and dependency-free: it unfolds folded lines,
 * parses content lines respecting quoted parameter values, decodes the four
 * standard text escapes and optional quoted-printable encoding, resolves
 * date/date-time values (all-day, UTC, floating and TZID) and preserves the
 * raw ICS at all times. It uses no regular expressions and no XML parsing.
 */
object IcsParser {

    fun parse(rawIcs: String): IcsCalendar {
        val lines = unfold(rawIcs)
        var prodId: String? = null
        var version: String? = null
        var calendarColor: String? = null
        val events = mutableListOf<IcsEvent>()

        var inEvent = false
        val properties = mutableListOf<ContentLine>()

        for (line in lines) {
            val cl = parseContentLine(line) ?: continue
            when {
                cl.name == "BEGIN" -> {
                    if (cl.value.equals("VEVENT", ignoreCase = true)) {
                        inEvent = true
                        properties.clear()
                    }
                }
                cl.name == "END" -> {
                    if (cl.value.equals("VEVENT", ignoreCase = true) && inEvent) {
                        events.add(buildEvent(properties))
                        inEvent = false
                        properties.clear()
                    }
                }
                !inEvent -> when (cl.name) {
                    "PRODID" -> prodId = cl.value
                    "VERSION" -> version = cl.value
                    "COLOR" -> calendarColor = cl.value
                    // ignore VCALENDAR-level content we do not need
                    else -> Unit
                }
                else -> properties += cl
            }
        }

        return IcsCalendar(
            rawIcs = rawIcs,
            prodId = prodId,
            version = version,
            calendarColor = calendarColor,
            events = events
        )
    }

    private fun buildEvent(properties: List<ContentLine>): IcsEvent {
        fun first(name: String): ContentLine? = properties.firstOrNull { it.name == name }
        fun many(name: String): List<ContentLine> = properties.filter { it.name == name }

        val dtStart = first("DTSTART")
        val dtEnd = first("DTEND")
        val dtStamp = first("DTSTAMP")
        val lastModified = first("LAST-MODIFIED")
        val recurrenceId = first("RECURRENCE-ID")

        val allDay = dtStart?.let { isAllDay(it) } ?: false

        val exceptions = buildList {
            for (cl in many("EXDATE")) {
                // EXDATE can carry a comma-separated list of date-times.
                for (token in cl.value.split(',')) {
                    if (token.isBlank()) continue
                    add(parseDateTime(token.trim(), cl.params))
                }
            }
        }

        return IcsEvent(
            uid = first("UID")?.value?.let(::unescapeText),
            summary = first("SUMMARY")?.let { extractText(it) },
            description = first("DESCRIPTION")?.let { extractText(it) },
            location = first("LOCATION")?.let { extractText(it) },
            dtStart = dtStart?.let { parseDateTime(it.value, it.params) },
            dtEnd = dtEnd?.let { parseDateTime(it.value, it.params) },
            allDay = allDay,
            color = first("COLOR")?.value,
            sequence = first("SEQUENCE")?.value?.trim()?.toIntOrNull(),
            status = first("STATUS")?.value,
            dtStamp = dtStamp?.let { parseDateTime(it.value, it.params) },
            lastModified = lastModified?.let { parseDateTime(it.value, it.params) },
            recurrenceRule = first("RRULE")?.value,
            recurrenceExceptionDates = exceptions,
            recurrenceId = recurrenceId?.let { parseDateTime(it.value, it.params) },
            duration = first("DURATION")?.value,
            classValue = first("CLASS")?.value,
            transparency = first("TRANSP")?.value
        )
    }

    private fun extractText(cl: ContentLine): String {
        val decoded = if (cl.params["ENCODING"]?.equals("QUOTED-PRINTABLE", ignoreCase = true) == true) {
            decodeQuotedPrintable(cl.value)
        } else {
            cl.value
        }
        return unescapeText(decoded)
    }

    private fun isAllDay(cl: ContentLine): Boolean =
        cl.params["VALUE"]?.equals("DATE", ignoreCase = true) == true ||
            cl.value.length == 8 && cl.value.all { it.isDigit() }

    private fun parseDateTime(value: String, params: Map<String, String>): IcsDateTime {
        val valueType = when {
            isAllDayValue(value, params) -> IcsDateTimeType.DATE
            params["TZID"] != null -> IcsDateTimeType.DATE_TIME_ZONE
            value.endsWith("Z") -> IcsDateTimeType.DATE_TIME_UTC
            else -> IcsDateTimeType.DATE_TIME_LOCAL
        }
        val isAllDay = valueType == IcsDateTimeType.DATE
        val zoneId: String? = params["TZID"]

        val epochMillis = safeEpochMillis(value, valueType, zoneId)
        return IcsDateTime(
            raw = value,
            type = valueType,
            zoneId = zoneId,
            epochMillis = epochMillis,
            isAllDay = isAllDay
        )
    }

    private fun isAllDayValue(value: String, params: Map<String, String>): Boolean =
        params["VALUE"]?.equals("DATE", ignoreCase = true) == true ||
            (value.length == 8 && value.all { it.isDigit() })

    private fun safeEpochMillis(value: String, type: IcsDateTimeType, zoneId: String?): Long? = try {
        when (type) {
            IcsDateTimeType.DATE -> {
                val date = LocalDate.parse(value.trim(), BASIC_DATE)
                (zoneId?.let { asZone(it) } ?: java.time.ZoneId.systemDefault())
                    .let { date.atStartOfDay(it).toInstant().toEpochMilli() }
            }
            IcsDateTimeType.DATE_TIME_UTC -> {
                val dt = LocalDateTime.parse(value.trim().removeSuffix("Z"), BASIC_DATE_TIME)
                dt.toInstant(ZoneOffset.UTC).toEpochMilli()
            }
            IcsDateTimeType.DATE_TIME_ZONE -> {
                val dt = LocalDateTime.parse(value.trim(), BASIC_DATE_TIME)
                ZonedDateTime.of(dt, asZone(zoneId!!)).toInstant().toEpochMilli()
            }
            IcsDateTimeType.DATE_TIME_LOCAL -> {
                val dt = LocalDateTime.parse(value.trim(), BASIC_DATE_TIME)
                dt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
        }
    } catch (_: Exception) {
        null
    }

    private fun asZone(zoneId: String): java.time.ZoneId =
        try {
            java.time.ZoneId.of(zoneId)
        } catch (_: ZoneRulesException) {
            java.time.ZoneId.systemDefault()
        }

    // --- Structured line primitives (no regex, no XML) ---

    /** Unfolds physical lines into logical content lines. */
    private fun unfold(text: String): List<String> {
        val physical = text.split('\n')
        val logical = mutableListOf<String>()
        var current = StringBuilder()
        for (rawLine in physical) {
            val line = rawLine.trimEnd('\r')
            if (line.isEmpty()) {
                if (current.isNotEmpty()) {
                    logical.add(current.toString())
                    current = StringBuilder()
                }
                continue
            }
            val firstChar = line[0]
            if (firstChar == ' ' || firstChar == '\t') {
                current.append(line.substring(1))
            } else {
                if (current.isNotEmpty()) {
                    logical.add(current.toString())
                }
                current = StringBuilder(line)
            }
        }
        if (current.isNotEmpty()) logical.add(current.toString())
        return logical
    }

    private fun parseContentLine(line: String): ContentLine? {
        val colon = indexOfUnquotedColon(line)
        if (colon < 0) return null
        val head = line.substring(0, colon)
        val value = line.substring(colon + 1)
        val segments = splitOutsideQuotes(head, ';')
        if (segments.isEmpty()) return null
        val name = segments[0].uppercase()
        val params = mutableMapOf<String, String>()
        for (seg in segments.drop(1)) {
            val eq = seg.indexOf('=')
            if (eq < 0) {
                params[seg.trim().uppercase()] = ""
            } else {
                val key = seg.substring(0, eq).trim().uppercase()
                val rawValue = seg.substring(eq + 1).trim()
                params[key] = unquote(rawValue)
            }
        }
        return ContentLine(name, params, value)
    }

    private fun indexOfUnquotedColon(line: String): Int {
        var inQuote = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '"') inQuote = !inQuote
            else if (c == ':' && !inQuote) return i
            i++
        }
        return -1
    }

    private fun splitOutsideQuotes(input: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        var inQuote = false
        var current = StringBuilder()
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c == '"') inQuote = !inQuote
            if (c == delimiter && !inQuote) {
                result.add(current.toString())
                current = StringBuilder()
            } else {
                current.append(c)
            }
            i++
        }
        result.add(current.toString())
        return result
    }

    private fun unquote(value: String): String =
        if (value.length >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value.substring(1, value.length - 1)
        } else {
            value
        }

    private fun unescapeText(value: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                val next = value[i + 1]
                when (next.lowercaseChar()) {
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    '\\' -> sb.append('\\')
                    ';' -> sb.append(';')
                    ',' -> sb.append(',')
                    else -> {
                        sb.append('\\')
                        sb.append(next)
                    }
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    private fun decodeQuotedPrintable(value: String): String {
        // Quoted-printable encodes bytes, so =XX sequences must be accumulated
        // and decoded as a UTF-8 byte stream rather than char-by-char, otherwise
        // =C3=A7 ("ç") turns into two Latin-1 characters.
        val bytes = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '=') {
                if (value.startsWith("=\r\n", i)) {
                    i += 3
                    continue
                }
                if (value.startsWith("=\n", i)) {
                    i += 2
                    continue
                }
                if (i + 2 <= value.length) {
                    val hex = value.substring(i + 1, i + 3)
                    val byte = hex.toIntOrNull(16)
                    if (byte != null && hex[0] != '\r' && hex[0] != '\n') {
                        bytes.write(byte)
                        i += 3
                        continue
                    }
                }
            }
            // Literal (ASCII) characters map to a single byte in QP.
            bytes.write(c.code)
            i++
        }
        return bytes.toByteArray().toString(Charsets.UTF_8)
    }

    private data class ContentLine(
        val name: String,
        val params: Map<String, String>,
        val value: String
    )

    private val BASIC_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val BASIC_DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
}
