package dev.claudiocodigo.nexo.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.claudiocodigo.nexo.domain.caldav.CalendarSyncCoordinator
import dev.claudiocodigo.nexo.domain.caldav.FailureKind
import dev.claudiocodigo.nexo.domain.caldav.SyncOutcome

/**
 * Background read-only synchronization of the selected work calendar.
 *
 * The worker never performs a remote write: it only pulls the mirror into the
 * local Room cache. Network/transient failures are retried; authorization and
 * permanent errors fail by marking the account for reconnect (never clearing
 * the cache).
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val coordinator: CalendarSyncCoordinator
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = when (val outcome = coordinator.syncNow()) {
        is SyncOutcome.Success -> Result.success()
        is SyncOutcome.SkippedNoAccount -> Result.success()
        is SyncOutcome.AlreadyRunning -> Result.retry()
        is SyncOutcome.Failure ->
            if (outcome.kind == FailureKind.NETWORK || outcome.kind == FailureKind.TIMEOUT) Result.retry()
            else Result.failure()
    }
}
