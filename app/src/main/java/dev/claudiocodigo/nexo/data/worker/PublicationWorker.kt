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
        while (true) {
            when (coordinator.drainNext()) {
                is DrainOutcome.QueueEmpty -> return Result.success()
                is DrainOutcome.Success -> Unit
                is DrainOutcome.Conflict -> Unit // Terminal state persisted; continue with the rest of the queue.
                is DrainOutcome.PermanentFailure -> Unit // One bad item must not block later publications.
                is DrainOutcome.TransientFailure -> return Result.retry()
            }
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "nexo_publication_drain"
    }
}
