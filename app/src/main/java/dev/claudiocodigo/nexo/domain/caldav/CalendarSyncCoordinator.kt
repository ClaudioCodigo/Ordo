package dev.claudiocodigo.nexo.domain.caldav

import kotlinx.coroutines.flow.StateFlow

/**
 * Orchestrates a read-only synchronization of the selected work calendar into
 * the local cache.
 *
 * Implementations must never perform a remote write, must never delete a local
 * draft, must refuse concurrent syncs for the same calendar and must keep the
 * last consistent view on partial failures.
 */
interface CalendarSyncCoordinator {
    /**
     * Runs one synchronization pass.
     *
     * @return the outcome: counts of added/updated/removed remotely-mirrored
     *         resources, or a typed failure. The UI must show a real result,
     *         never a fabricated "success".
     */
    suspend fun syncNow(): SyncOutcome

    /** Whether a synchronization pass is currently running. */
    val isSyncing: StateFlow<Boolean>
}
