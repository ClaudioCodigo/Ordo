package dev.claudiocodigo.nexo.data.repository

import dev.claudiocodigo.nexo.data.local.NexoDatabase
import dev.claudiocodigo.nexo.data.local.dao.PublicationOutboxDao
import dev.claudiocodigo.nexo.data.local.dao.ServiceOrderStoreDao
import dev.claudiocodigo.nexo.data.local.entity.PublicationOutboxEntity
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

    override suspend fun confirmPreview(snapshot: ConfirmedPreviewSnapshot): OutboxOperation {
        val opId = UUID.randomUUID()
        val now = System.currentTimeMillis()

        val operation = OutboxOperation(
            id = opId,
            orderId = snapshot.orderId,
            action = snapshot.action,
            payloadIcs = snapshot.rawIcsPayload,
            ifMatchEtag = snapshot.baseEtag,
            status = OutboxStatus.PENDING,
            createdAt = now,
            updatedAt = now
        )

        database.withTransaction {
            val nextVersionNum =
                (storeDao.getVersionsByOrderId(snapshot.orderId).maxOfOrNull { it.versionNumber } ?: 0) + 1
            val versionEntity = ServiceOrderVersionEntity(
                id = UUID.randomUUID(),
                orderId = snapshot.orderId,
                versionNumber = nextVersionNum,
                formattedDescription = snapshot.formattedDescription,
                publishedEtag = null,
                publishedAt = now
            )

            storeDao.upsertVersion(versionEntity)
            outboxDao.insert(toEntity(operation))
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
        outboxDao.markSent(operationId, nowMillis)
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
        updatedAt = entity.updatedAt
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
        updatedAt = domain.updatedAt
    )
}
