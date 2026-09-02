package dev.claudiocodigo.nexo.domain.publication

import java.util.UUID

enum class OutboxAction {
    CREATE,
    UPDATE,
    FINALIZE
}

enum class OutboxStatus {
    PENDING,
    SENDING,
    SENT,
    CONFLICT,
    PERMANENT_FAILURE
}

data class ConfirmedPreviewSnapshot(
    val orderId: UUID,
    val action: OutboxAction,
    val formattedDescription: String,
    val baseEtag: String?,
    val rawIcsPayload: String,
    val targetHref: String,
    val uid: String,
    val confirmedRevision: Long = 0L
)

data class OutboxOperation(
    val id: UUID = UUID.randomUUID(),
    val orderId: UUID,
    val action: OutboxAction,
    val payloadIcs: String,
    val ifMatchEtag: String?,
    val status: OutboxStatus,
    val lastError: String? = null,
    val retryCount: Int = 0,
    val leaseExpiresAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val confirmedRevision: Long = 0L
)

sealed interface DrainOutcome {
    data object QueueEmpty : DrainOutcome
    data class Success(val operationId: UUID, val newEtag: String?) : DrainOutcome
    data class Conflict(val operationId: UUID, val reason: String) : DrainOutcome
    data class TransientFailure(val operationId: UUID, val reason: String) : DrainOutcome
    data class PermanentFailure(val operationId: UUID, val reason: String) : DrainOutcome
}
