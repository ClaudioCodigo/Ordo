package dev.claudiocodigo.nexo.data.ical

/**
 * Structured (best-effort) representation of an iCalendar payload.
 *
 * The raw ICS is always preserved; [parse] only extracts fields and never
 * discards raw text, so a partially parsed event keeps everything that was
 * present on the server (see CAL-02).
 */
data class IcsCalendar(
    val rawIcs: String,
    val prodId: String?,
    val version: String?,
    val calendarColor: String?,
    val events: List<IcsEvent>
)

data class IcsEvent(
    val uid: String?,
    val summary: String?,
    val description: String?,
    val location: String?,
    val dtStart: IcsDateTime?,
    val dtEnd: IcsDateTime?,
    val allDay: Boolean,
    val color: String?,
    val sequence: Int?,
    val status: String?,
    val dtStamp: IcsDateTime?,
    val lastModified: IcsDateTime?,
    val recurrenceRule: String?,
    val recurrenceExceptionDates: List<IcsDateTime>,
    val recurrenceId: IcsDateTime?,
    val duration: String?,
    val classValue: String?,
    val transparency: String?
)

data class IcsDateTime(
    val raw: String,
    val type: IcsDateTimeType,
    val zoneId: String?,
    val epochMillis: Long?,
    val isAllDay: Boolean
)

enum class IcsDateTimeType {
    DATE,
    DATE_TIME_UTC,
    DATE_TIME_LOCAL,
    DATE_TIME_ZONE
}
