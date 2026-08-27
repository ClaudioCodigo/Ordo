package dev.claudiocodigo.nexo.domain.caldav

/**
 * Remote CalDAV discovery: resolves the server principal, the calendar
 * home-set and the list of calendars the user can read.
 *
 * This interface is deliberately read-only. There is no method that can
 * create, modify, colorize or delete a remote resource.
 */
interface CalDavDiscoveryClient {
    /**
     * Resolves `/.well-known/caldav`, `current-user-principal`,
     * `calendar-home-set` and the resulting calendar collections.
     *
     * @param credentials the non-secret server, user and application password.
     * @return a [DiscoveryResult] with the calendars, or a failure kind mapped
     *         to a user-facing message.
     */
    suspend fun discover(credentials: CalDavCredentials): DiscoveryResult
}
