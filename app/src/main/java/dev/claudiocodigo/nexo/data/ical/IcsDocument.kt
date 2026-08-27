package dev.claudiocodigo.nexo.data.ical

/**
 * Lossless representation of an iCalendar (RFC 5545) text document.
 *
 * Preserves the exact physical lines, line breaks, ordering, parameters,
 * custom X-properties, timezones and subcomponents without destructive round-trips.
 */
data class IcsDocument(
    val lines: List<String>,
    val lineEnding: String = "\r\n"
) {
    fun render(): String = lines.joinToString(lineEnding)

    companion object {
        fun parse(raw: String): IcsDocument {
            val lineEnding = if (raw.contains("\r\n")) "\r\n" else "\n"
            val lines = raw.split(Regex("""\r\n|\r|\n"""))
            return IcsDocument(lines, lineEnding)
        }
    }
}
