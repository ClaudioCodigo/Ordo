package dev.claudiocodigo.nexo.data.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps WorkManager scheduling for the read-only calendar sync.
 *
 * Sync only runs with a connected network; the worker itself never performs a
 * remote write. Independent periodic and manual work keys avoid duplicates.
 */
@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    /** Schedules a periodic read-only sync, keeping any existing schedule. */
    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(PERIOD_MINUTES, TimeUnit.MINUTES)
            .setConstraints(networkConstraint())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /** Runs one read-only sync now. */
    fun syncNow() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkConstraint())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(MANUAL_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    private fun networkConstraint(): Constraints =
        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    companion object {
        private const val PERIODIC_WORK = "nexo_calendar_sync_periodic"
        private const val MANUAL_WORK = "nexo_calendar_sync_manual"
        private const val PERIOD_MINUTES = 15L
    }
}
