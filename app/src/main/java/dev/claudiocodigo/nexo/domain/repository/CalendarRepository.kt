package dev.claudiocodigo.nexo.domain.repository

import dev.claudiocodigo.nexo.domain.caldav.AccountIdentity
import dev.claudiocodigo.nexo.domain.caldav.CalendarInfo
import dev.claudiocodigo.nexo.domain.caldav.CalendarSyncState
import dev.claudiocodigo.nexo.domain.caldav.EventColor
import dev.claudiocodigo.nexo.domain.caldav.RemoteEvent
import kotlinx.coroutines.flow.Flow

/**
 * Read-only local source of truth for the mirrored calendar cache.
 *
 * Hoje, Agenda, search, details and the real sync indicator read only from
 * this repository — never from the network. This interface exposes no remote
 * write, so the UI cannot publish, edit or delete a remote event.
 */
interface CalendarRepository {

    /** Mirrored events for a full date range, ordered by start. */
    fun observeEvents(): Flow<List<RemoteEvent>>

    /**
     * Mirrored events that intersect a single day (epoch millis at local
     * midnight). Used by Hoje and the day grouping in Agenda.
     */
    fun observeEventsForDay(dayStartMillis: Long, dayEndMillis: Long): Flow<List<RemoteEvent>>

    /** Mirrored events matching a free-text query on summary/description/location. */
    fun searchEvents(query: String): Flow<List<RemoteEvent>>

    /** Mirrored events whose end is before now (potential "atrasados"). */
    fun observeOverdue(nowMillis: Long): Flow<List<RemoteEvent>>

    /** Fetches one mirrored event by its identity. */
    suspend fun getEvent(accountId: String, calendarHref: String, href: String): RemoteEvent?

    /** The currently configured account identity, or `null`. */
    fun observeAccount(): Flow<AccountIdentity?>

    /** The currently selected work calendar, or `null`. */
    fun observeSelectedCalendar(): Flow<CalendarInfo?>

    /** Real sync state of the selected work calendar, or `null` when nothing is synced. */
    fun observeSyncState(): Flow<CalendarSyncState?>

    /** Interpretation helper exposed to the UI (CAL-04). */
    fun classifyColor(raw: String?): EventColor
}
