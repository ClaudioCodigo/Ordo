package dev.claudiocodigo.nexo.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.claudiocodigo.nexo.domain.publication.DrainOutcome
import dev.claudiocodigo.nexo.domain.publication.PublicationCoordinator

@HiltWorker
class PublicationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val coordinator: PublicationCoordinator
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val outcome = coordinator.drainNext()
        return when (outcome) {
            is DrainOutcome.QueueEmpty -> Result.success()
            is DrainOutcome.Success -> Result.success()
            is DrainOutcome.Conflict -> Result.success() // Terminal state: persisted in Room for user review
            is DrainOutcome.PermanentFailure -> Result.success() // Terminal state: persisted in Room
            is DrainOutcome.TransientFailure -> Result.retry() // Network/Timeout: retry via WorkManager
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "nexo_publication_drain"
    }
}
