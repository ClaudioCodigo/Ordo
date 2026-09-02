package dev.claudiocodigo.nexo.data.repository

import dev.claudiocodigo.nexo.data.local.NexoDatabase
import dev.claudiocodigo.nexo.data.local.dao.PublicationOutboxDao
import dev.claudiocodigo.nexo.data.local.dao.ServiceOrderStoreDao
import dev.claudiocodigo.nexo.data.local.entity.PublicationOutboxEntity
import dev.claudiocodigo.nexo.data.local.entity.ServiceOrderEntity
import dev.claudiocodigo.nexo.data.local.entity.ServiceOrderVersionEntity
import dev.claudiocodigo.nexo.domain.publication.ConfirmedPreviewSnapshot
import dev.claudiocodigo.nexo.domain.publication.OutboxAction
import dev.claudiocodigo.nexo.domain.publication.OutboxOperation
import dev.claudiocodigo.nexo.domain.publication.OutboxStatus
import dev.claudiocodigo.nexo.domain.publication.PublicationRepository
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject

class RoomPublicationRepository @Inject constructor(
    private val database: NexoDatabase,
    private val outboxDao: PublicationOutboxDao,
    private val storeDao: ServiceOrderStoreDao
) : PublicationRepository {

    override fun observeOperations(): Flow<List<OutboxOperation>> =
        outboxDao.observeAll().map { entities -> entities.map(::toDomain) }

    override suspend fun getOperationById(id: UUID): OutboxOperation? =
        outboxDao.getById(id)?.let(::toDomain)

    override suspend fun getLatestForOrder(orderId: UUID): OutboxOperation? =
        outboxDao.getLatestForOrder(orderId)?.let(::toDomain)

    override suspend fun confirmPreview(snapshot: ConfirmedPreviewSnapshot, forceOverwrite: Boolean): OutboxOperation {
        val opId = UUID.randomUUID()
        val now = System.currentTimeMillis()

        val operation = OutboxOperation(
            id = opId,
            orderId = snapshot.orderId,
            action = snapshot.action,
            payloadIcs = snapshot.rawIcsPayload,
            ifMatchEtag = snapshot.baseEtag,
            status = OutboxStatus.PENDING,
            confirmedRevision = snapshot.confirmedRevision,
            createdAt = now,
            updatedAt = now
        )

        database.withTransaction {
            val currentOrder = storeDao.getStructuredOrderById(snapshot.orderId)
            val hasActive = outboxDao.hasActiveOperation(snapshot.orderId)
            val hasBlocking = outboxDao.hasBlockingOperation(snapshot.orderId) && currentOrder?.publicationState != dev.claudiocodigo.nexo.domain.serviceorder.PublicationState.LOCAL_DRAFT
            
            if (hasActive || hasBlocking) {
                if (forceOverwrite) {
                    outboxDao.clearPendingForOrder(snapshot.orderId)
                } else {
                    throw IllegalStateException("BLOCKED_BY_PENDING")
                }
            }
            // A conflict review changes the order back to LOCAL_DRAFT. Only then
            // is the resolved conflict record retired before the new publication.
            outboxDao.clearConflictsForOrder(snapshot.orderId)
            val nextVersionNum =
                (storeDao.getVersionsByOrderId(snapshot.orderId).maxOfOrNull { it.versionNumber } ?: 0) + 1
            val versionEntity = ServiceOrderVersionEntity(
                id = UUID.randomUUID(),
                orderId = snapshot.orderId,
                versionNumber = nextVersionNum,
                formattedDescription = snapshot.formattedDescription,
                publishedEtag = null,
                publishedAt = now,
                confirmedRevision = snapshot.confirmedRevision
            )

            storeDao.upsertVersion(versionEntity)
            outboxDao.insert(toEntity(operation))
            storeDao.getStructuredOrderById(snapshot.orderId)?.let {
                storeDao.upsertOrder(ServiceOrderEntity.fromStructured(it.copy(publicationState = dev.claudiocodigo.nexo.domain.serviceorder.PublicationState.QUEUED)))
            }
        }

        return operation
    }

    override suspend fun claimNextEligible(nowMillis: Long, leaseDurationMillis: Long): OutboxOperation? {
        val expiredThreshold = nowMillis - leaseDurationMillis
        val eligible = outboxDao.findNextEligible(expiredThreshold) ?: return null
        val updated = outboxDao.claim(eligible.id, nowMillis)
        return if (updated > 0) toDomain(eligible.copy(status = OutboxStatus.SENDING.name, updatedAt = nowMillis)) else null
    }

    override suspend fun markSent(operationId: UUID, newEtag: String?, nowMillis: Long) {
        database.withTransaction {
            val operation = outboxDao.getById(operationId)
            outboxDao.markSent(operationId, nowMillis)
            operation?.let { storeDao.markVersionPublished(it.orderId, it.confirmedRevision, newEtag, nowMillis) }
        }
    }

    override suspend fun markConflict(operationId: UUID, reason: String, nowMillis: Long) {
        outboxDao.markConflict(operationId, reason, nowMillis)
    }

    override suspend fun markFailed(operationId: UUID, reason: String, permanent: Boolean, nowMillis: Long) {
        val status = if (permanent) OutboxStatus.PERMANENT_FAILURE.name else OutboxStatus.PENDING.name
        outboxDao.markFailed(operationId, status, reason, nowMillis)
    }

    override suspend fun cancelPending(operationId: UUID): Boolean {
        return outboxDao.deleteIfPending(operationId) > 0
    }

    override suspend fun cancelOperation(operationId: UUID): Boolean {
        return outboxDao.deleteOperation(operationId) > 0
    }

    override suspend fun cancelAllForOrder(orderId: UUID) {
        // Clearing anything pending, sending, failed, or conflicts
        outboxDao.clearPendingForOrder(orderId)
        outboxDao.clearConflictsForOrder(orderId)
    }

    private fun toDomain(entity: PublicationOutboxEntity): OutboxOperation = OutboxOperation(
        id = entity.id,
        orderId = entity.orderId,
        action = OutboxAction.entries.firstOrNull { it.name == entity.action } ?: OutboxAction.UPDATE,
        payloadIcs = entity.payloadIcs,
        ifMatchEtag = entity.ifMatchEtag,
        status = OutboxStatus.entries.firstOrNull { it.name == entity.status } ?: OutboxStatus.PENDING,
        lastError = entity.lastError,
        retryCount = entity.retryCount,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
        confirmedRevision = entity.confirmedRevision
    )

    private fun toEntity(domain: OutboxOperation): PublicationOutboxEntity = PublicationOutboxEntity(
        id = domain.id,
        orderId = domain.orderId,
        action = domain.action.name,
        payloadIcs = domain.payloadIcs,
        ifMatchEtag = domain.ifMatchEtag,
        status = domain.status.name,
        lastError = domain.lastError,
        retryCount = domain.retryCount,
        createdAt = domain.createdAt,
        updatedAt = domain.updatedAt,
        confirmedRevision = domain.confirmedRevision
    )
}
