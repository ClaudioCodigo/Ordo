package dev.claudiocodigo.nexo.domain.publication

import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface PublicationRepository {
    fun observeOperations(): Flow<List<OutboxOperation>>
    suspend fun getOperationById(id: UUID): OutboxOperation?
    suspend fun getLatestForOrder(orderId: UUID): OutboxOperation?

    suspend fun confirmPreview(snapshot: ConfirmedPreviewSnapshot): OutboxOperation
    suspend fun claimNextEligible(nowMillis: Long, leaseDurationMillis: Long = 60_000L): OutboxOperation?

    suspend fun markSent(operationId: UUID, newEtag: String?, nowMillis: Long)
    suspend fun markConflict(operationId: UUID, reason: String, nowMillis: Long)
    suspend fun markFailed(operationId: UUID, reason: String, permanent: Boolean, nowMillis: Long)
    suspend fun cancelPending(operationId: UUID): Boolean
}
