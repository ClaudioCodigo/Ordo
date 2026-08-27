package dev.claudiocodigo.nexo.domain.serviceorder

import dev.claudiocodigo.nexo.domain.model.ServiceOrder
import dev.claudiocodigo.nexo.domain.model.ServiceOrderStatus
import java.util.UUID

/**
 * Composite identity of a remote calendar event occurrence.
 *
 * A recurring series shares (accountId, calendarHref, eventHref) and distinguishes
 * instances by [recurrenceId] (in normalized ISO/UTC string format).
 */
data class RemoteOccurrenceKey(
    val accountId: String,
    val calendarHref: String,
    val eventHref: String,
    val recurrenceId: String? = null
) {
    val normalizedRecurrenceId: String get() = recurrenceId.orEmpty()
}

/** Preset that configures the labels, problem description and closure workflow of the OS. */
enum class ServiceOrderPreset {
    DIAGNOSTICO_CORRECAO,
    SERVICO_SOLICITADO
}

/** Publication state of a service order relative to the CalDAV calendar. */
enum class PublicationState {
    LOCAL_DRAFT,
    QUEUED,
    PUBLISHED,
    CONFLICT
}

/** An incremental update entry recorded during service order execution. */
data class ServiceOrderUpdate(
    val id: UUID = UUID.randomUUID(),
    val sequenceOrder: Int,
    val text: String,
    val executionDate: Long,
    val createdAt: Long = System.currentTimeMillis()
)

/** A generic equipment or item record attached to the service order. */
data class ServiceOrderItem(
    val id: UUID = UUID.randomUUID(),
    val action: String,
    val itemType: String,
    val brand: String? = null,
    val model: String? = null,
    val serialNumber: String? = null,
    val relatedEquipment: String? = null,
    val location: String? = null,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/** Immutable snapshot of the remote CalDAV event at the time of link or revalidation. */
data class RemoteBaseSnapshot(
    val id: UUID = UUID.randomUUID(),
    val etag: String?,
    val rawIcs: String,
    val rawSummary: String?,
    val rawDescription: String?,
    val capturedAt: Long = System.currentTimeMillis()
)

/** An immutable published version record of a service order. */
data class ServiceOrderVersion(
    val id: UUID = UUID.randomUUID(),
    val versionNumber: Int,
    val formattedDescription: String,
    val publishedEtag: String?,
    val publishedAt: Long = System.currentTimeMillis()
)

/**
 * Structured Service Order aggregate representing the complete state of a local or linked OS.
 */
data class StructuredServiceOrder(
    val id: UUID = UUID.randomUUID(),
    val occurrenceKey: RemoteOccurrenceKey? = null,
    val externalId: String? = null,
    val title: String,
    val clientName: String,
    val unitName: String,
    val technician: String? = null,
    val category: String? = null,
    val preset: ServiceOrderPreset = ServiceOrderPreset.DIAGNOSTICO_CORRECAO,
    val originalDemand: String = "",
    val status: ServiceOrderStatus = ServiceOrderStatus.PENDENTE,
    val publicationState: PublicationState = PublicationState.LOCAL_DRAFT,
    val updates: List<ServiceOrderUpdate> = emptyList(),
    val items: List<ServiceOrderItem> = emptyList(),
    val closureCause: String? = null,
    val closureSolution: String? = null,
    val closurePending: String? = null,
    val sequence: Int? = null,
    val scheduledStart: Long? = null,
    val scheduledEnd: Long? = null,
    val baseSnapshot: RemoteBaseSnapshot? = null,
    val versions: List<ServiceOrderVersion> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    /** Converts the structured aggregate to the legacy lightweight model. */
    fun toLegacy(): ServiceOrder = ServiceOrder(
        id = id,
        externalId = externalId,
        title = title,
        description = originalDemand,
        status = status,
        clientName = clientName,
        unitName = unitName,
        scheduledDate = scheduledStart,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
