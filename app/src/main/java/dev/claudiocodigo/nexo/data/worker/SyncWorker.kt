package dev.claudiocodigo.nexo.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        // A fila de sincronização só será ativada quando o cliente CalDAV existir.
        // Falhar explicitamente evita apresentar uma sincronização fictícia como concluída.
        return Result.failure()
    }
}
