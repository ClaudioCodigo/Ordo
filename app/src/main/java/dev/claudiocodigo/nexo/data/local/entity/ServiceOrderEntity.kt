package dev.claudiocodigo.nexo.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.claudiocodigo.nexo.domain.model.ServiceOrder
import dev.claudiocodigo.nexo.domain.model.ServiceOrderStatus
import dev.claudiocodigo.nexo.domain.serviceorder.PublicationState
import dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderPreset
import dev.claudiocodigo.nexo.domain.serviceorder.StructuredServiceOrder
import java.util.UUID

/**
 * The canonical local database entity for a Service Order.
 *
 * In Schema v3, new additive columns support rich structured workflow without
 * breaking legacy Phase 1 drafts or requiring destructive migrations.
 */
@Entity(tableName = "service_orders")
data class ServiceOrderEntity(
    @PrimaryKey val id: UUID,
    val externalId: String?,
    val title: String,
    val description: String,
    val status: String,
    val clientName: String,
    val unitName: String,
    val scheduledDate: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val technician: String? = null,
    val category: String? = null,
    val preset: String = ServiceOrderPreset.DIAGNOSTICO_CORRECAO.name,
    val originalDemand: String = "",
    val publicationState: String = PublicationState.LOCAL_DRAFT.name,
    val closureCause: String? = null,
    val closureSolution: String? = null,
    val closurePending: String? = null,
    val sequence: Int? = null,
    val scheduledStart: Long? = null,
    val scheduledEnd: Long? = null
) {
    fun toDomain(): ServiceOrder = ServiceOrder(
        id = id,
        externalId = externalId,
        title = title,
        description = if (originalDemand.isNotBlank()) originalDemand else description,
        status = ServiceOrderStatus.entries.firstOrNull { it.name == status }
            ?: ServiceOrderStatus.PENDENTE,
        clientName = clientName,
        unitName = unitName,
        scheduledDate = scheduledDate ?: scheduledStart,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    fun toStructured(
        link: ServiceOrderLinkEntity? = null,
        snapshot: ServiceOrderSnapshotEntity? = null,
        updates: List<ServiceOrderUpdateEntity> = emptyList(),
        items: List<ServiceOrderItemEntity> = emptyList(),
        versions: List<ServiceOrderVersionEntity> = emptyList()
    ): StructuredServiceOrder = StructuredServiceOrder(
        id = id,
        occurrenceKey = link?.toOccurrenceKey(),
        externalId = externalId,
        title = title,
        clientName = clientName,
        unitName = unitName,
        technician = technician,
        category = category,
        preset = ServiceOrderPreset.entries.firstOrNull { it.name == preset }
            ?: ServiceOrderPreset.DIAGNOSTICO_CORRECAO,
        originalDemand = if (originalDemand.isNotBlank()) originalDemand else description,
        status = ServiceOrderStatus.entries.firstOrNull { it.name == status }
            ?: ServiceOrderStatus.PENDENTE,
        publicationState = PublicationState.entries.firstOrNull { it.name == publicationState }
            ?: PublicationState.LOCAL_DRAFT,
        updates = updates.sortedBy { it.sequenceOrder }.map { it.toDomain() },
        items = items.map { it.toDomain() },
        closureCause = closureCause,
        closureSolution = closureSolution,
        closurePending = closurePending,
        sequence = sequence,
        scheduledStart = scheduledStart ?: scheduledDate,
        scheduledEnd = scheduledEnd,
        baseSnapshot = snapshot?.toDomain(),
        versions = versions.sortedBy { it.versionNumber }.map { it.toDomain() },
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(domain: ServiceOrder) = ServiceOrderEntity(
            id = domain.id,
            externalId = domain.externalId,
            title = domain.title,
            description = domain.description,
            status = domain.status.name,
            clientName = domain.clientName,
            unitName = domain.unitName,
            scheduledDate = domain.scheduledDate,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt,
            originalDemand = domain.description,
            scheduledStart = domain.scheduledDate
        )

        fun fromStructured(structured: StructuredServiceOrder) = ServiceOrderEntity(
            id = structured.id,
            externalId = structured.externalId,
            title = structured.title,
            description = structured.originalDemand,
            status = structured.status.name,
            clientName = structured.clientName,
            unitName = structured.unitName,
            scheduledDate = structured.scheduledStart,
            createdAt = structured.createdAt,
            updatedAt = structured.updatedAt,
            technician = structured.technician,
            category = structured.category,
            preset = structured.preset.name,
            originalDemand = structured.originalDemand,
            publicationState = structured.publicationState.name,
            closureCause = structured.closureCause,
            closureSolution = structured.closureSolution,
            closurePending = structured.closurePending,
            sequence = structured.sequence,
            scheduledStart = structured.scheduledStart,
            scheduledEnd = structured.scheduledEnd
        )
    }
}
