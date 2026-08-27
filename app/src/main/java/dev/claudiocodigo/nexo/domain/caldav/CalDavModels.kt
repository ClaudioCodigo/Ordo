package dev.claudiocodigo.nexo.domain.caldav

/**
 * Non-secret credentials handed to the CalDAV read-only clients.
 *
 * The application password is held as a [CharArray] so callers can wipe it
 * from memory once the request is complete. It must never be copied into a
 * log, a repository, a report or a serializable model.
 */
class CalDavCredentials(
    val server: String,
    val user: String,
    password: CharArray
) {
    private val held = password.copyOf()

    /** A copy of the application password. Callers must not log or persist it. */
    fun appPassword(): CharArray = held.copyOf()

    /** Wipes the held password from memory. */
    fun wipe() {
        held.fill('\u0000')
    }
}

/** A calendar collection discovered on the server. */
data class CalendarInfo(
    val href: String,
    val displayName: String?,
    val description: String?,
    /** Raw calendar color from the collection (e.g. `#RRGGBB` or a name). */
    val color: String?,
    /** True when the collection supports the `VEVENT` component. */
    val supportsVeEvent: Boolean,
    /** True when the server advertises read/write access to this collection. */
    val hasWritePrivilege: Boolean,
    /** `sync-token` advertised by the collection, when available. */
    val syncToken: String?
)

/** Discovery navigation result. */
data class Principal(
    /** `current-user-principal` href, when advertised. */
    val currentUserPrincipal: String?,
    /** `calendar-home-set` href, when advertised. */
    val calendarHomeSet: String?
)

/**
 * The color semantics Nexo applies to an event's `COLOR` property.
 *
 * - `VALIDADO`: green `#008000` — the event was validated by the technical control.
 * - `REQUER_ATENCAO`: red `#B22222` — the event requires attention.
 * - `NAO_CLASSIFICADO`: any other color, or an absent color.
 *
 * The app never flips a color to green on its own; it only interprets.
 */
enum class EventColor {
    VALIDADO,
    REQUER_ATENCAO,
    NAO_CLASSIFICADO;

    fun isGreen(): Boolean = this == VALIDADO
    fun isRed(): Boolean = this == REQUER_ATENCAO
}

/** A remote calendar event mirrored locally. Never conflated with a local draft. */
data class RemoteEvent(
    /** Internal identity of the account this event belongs to. */
    val accountId: String,
    /** `href` of the owning calendar collection. */
    val calendarHref: String,
    /** Resource `href` of this event on the server. */
    val href: String,
    /** CalDAV `UID` property. Never a global unique key (see CAL-05). */
    val uid: String,
    /** `ETag` of the resource at the last successful sync. */
    val etag: String?,
    /** `SEQUENCE` value, when present. */
    val sequence: Int?,
    /** The raw iCalendar payload, always preserved before any extraction (CAL-02). */
    val rawIcs: String,
    /** Raw `SUMMARY`, preserved even when extraction is partial. */
    val summary: String?,
    /** Raw `DESCRIPTION`, preserved even when extraction is partial. */
    val description: String?,
    /** Raw `LOCATION`. */
    val location: String?,
    /** Resolved start (epoch millis), best-effort. */
    val start: Long?,
    /** Resolved end (epoch millis), best-effort. */
    val end: Long?,
    /** True for all-day events (`VALUE=DATE`). */
    val allDay: Boolean,
    /** Interpreted color semantics (CAL-04). */
    val color: EventColor,
    /** Raw event `COLOR` string, when present. */
    val rawEventColor: String?,
    /** Time-zone identifier that applies to floating times, when known. */
    val timeZone: String?,
    /** Recurrence rule (`RRULE`) plus exceptions, preserved as text. */
    val recurrenceText: String?,
    /** `LAST-MODIFIED` / best-effort remote change time, epoch millis. */
    val lastModified: Long?,
    /** Local wall-clock time of the last successful sync, epoch millis. */
    val lastSyncMillis: Long
)

/** `href` plus `getetag` for one collection member, used for change detection. */
data class ResourceEtag(
    val href: String,
    val etag: String?
)

/** A collection listing together with whether the server response was
 * complete and structurally trustworthy. */
data class ResourceListing(
    val resources: List<ResourceEtag>,
    val complete: Boolean,
    val errorMessage: String? = null,
    val syncToken: String? = null
)

/** RFC 6578 sync-collection delta. A 404 response is represented in
 * [removed], while 200 responses are represented in [changed]. */
data class SyncCollectionResult(
    val newToken: String?,
    val changed: List<ResourceEtag>,
    val removed: Set<String>,
    val complete: Boolean,
    val errorMessage: String? = null
)

/** A raw event resource returned by the read client, before iCalendar parsing. */
data class EventResource(
    val href: String,
    val etag: String?,
    /** The raw iCalendar body for this resource. */
    val ics: String
)

/** Discovery outcome. */
sealed interface DiscoveryResult {
    data class Success(
        val principal: Principal?,
        val calendars: List<CalendarInfo>
    ) : DiscoveryResult

    data class Failure(
        val kind: FailureKind,
        val message: String
    ) : DiscoveryResult
}

enum class FailureKind {
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    REDIRECT_INSECURE,
    TLS_INVALID,
    TIMEOUT,
    NETWORK,
    PARSE,
    UNKNOWN
}

/** Outcome of a read-only synchronization attempt. */
sealed interface SyncOutcome {
    data class Success(
        val added: Int,
        val updated: Int,
        val removed: Int,
        val token: String?
    ) : SyncOutcome

    data object SkippedNoAccount : SyncOutcome
    data object AlreadyRunning : SyncOutcome
    data class Failure(val kind: FailureKind, val message: String) : SyncOutcome
}
