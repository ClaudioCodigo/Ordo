package dev.claudiocodigo.nexo.domain.repository

import dev.claudiocodigo.nexo.domain.caldav.CalendarInfo
import kotlinx.coroutines.flow.Flow

/**
 * Local (Room) setup operations for the Nextcloud account and the calendar
 * selection. These write to the local database only — they never issue a remote
 * write. Switching or disconnecting never touches independent local drafts.
 */
interface CalendarSetupRepository {

    /** Returns the account id for the given identity, creating it if absent. */
    suspend fun ensureAccount(server: String, user: String): String

    /** The active account id, if any. */
    suspend fun getActiveAccountId(): String?

    /** Replaces the discovered calendar list for an account. */
    suspend fun saveCalendars(accountId: String, calendars: List<CalendarInfo>)

    /** Selects exactly one work calendar, clearing the previous selection's remote cache. */
    suspend fun selectWorkingCalendar(accountId: String, href: String)

    /** Observes the discovered calendars for an account. */
    fun observeCalendars(accountId: String): Flow<List<CalendarInfo>>

    /** Observes the currently selected work calendar. */
    fun observeSelectedCalendar(): Flow<CalendarInfo?>

    /** Deletes the account row, a selection caches' mirror and sync state, but never draft OS. */
    suspend fun disconnectLocal()
}
