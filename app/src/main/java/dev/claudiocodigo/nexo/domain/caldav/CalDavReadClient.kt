package dev.claudiocodigo.nexo.domain.caldav

/**
 * Read-only CalDAV client for a specific calendar collection.
 *
 * It only lists resource `href`/`etag` pairs, fetches raw event resources and
 * reads the collection `sync-token`. By design there is no mutating method:
 * the client cannot publish, edit, colorize or delete remote resources.
 */
interface CalDavReadClient {
    /**
     * Fetches the `sync-token` advertised by the collection, when available.
     * Returns `null` when the server does not announce one.
     */
    suspend fun getSyncToken(calendarHref: String, credentials: CalDavCredentials): String?

    /**
     * Lists collection members as `href`/`etag` pairs for change detection.
     */
    suspend fun listHrefAndEtags(calendarHref: String, credentials: CalDavCredentials): List<ResourceEtag>

    /** Structured variant used by synchronization to protect the cache from
     * empty or malformed responses. Existing test/dummy clients can continue
     * implementing the list method; those responses are considered complete. */
    suspend fun listHrefAndEtagsResult(
        calendarHref: String,
        credentials: CalDavCredentials
    ): ResourceListing = ResourceListing(listHrefAndEtags(calendarHref, credentials), complete = true)

    /** Reads an RFC 6578 sync-collection delta using [syncToken]. */
    suspend fun syncCollection(
        calendarHref: String,
        syncToken: String,
        credentials: CalDavCredentials
    ): SyncCollectionResult = throw SyncCollectionUnsupportedException()

    /**
     * Fetches the raw iCalendar bodies for the given resource `href`s.
     * The raw ICS is always preserved for downstream parsing.
     */
    suspend fun fetchEvents(
        calendarHref: String,
        hrefs: List<String>,
        credentials: CalDavCredentials
    ): List<EventResource>
}

class SyncCollectionUnsupportedException : Exception("Servidor não suporta sync-collection")

class InvalidSyncTokenException : Exception("sync-token inválido ou expirado")
