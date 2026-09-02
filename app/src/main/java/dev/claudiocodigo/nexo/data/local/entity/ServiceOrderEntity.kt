package dev.claudiocodigo.nexo.data.local.entity

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey
import dev.claudiocodigo.nexo.domain.model.ServiceOrder
import dev.claudiocodigo.nexo.domain.model.ServiceOrderStatus
import dev.claudiocodigo.nexo.domain.serviceorder.PublicationState
import dev.claudiocodigo.nexo.domain.serviceorder.ConclusionState
import dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderPreset
import dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderFlow
import dev.claudiocodigo.nexo.domain.serviceorder.TechnicalOpinion
import dev.claudiocodigo.nexo.domain.serviceorder.StructuredServiceOrder
import java.util.UUID

/**
 * The canonical local database entity for a Service Order.
 *
 * Schema v5 persists the guided flow, technical opinion and draft revisions.
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
    @ColumnInfo(defaultValue = "'NAO_DEFINIDO'")
    val conclusionState: String = ConclusionState.NAO_DEFINIDO.name,
    @ColumnInfo(defaultValue = "'RESOLUTION'")
    val flow: String = ServiceOrderFlow.RESOLUTION.name,
    @ColumnInfo(defaultValue = "'CONCLUDED'")
    val technicalOpinion: String = TechnicalOpinion.CONCLUDED.name,
    val observations: String? = null,
    val updateDraft: String? = null,
    @ColumnInfo(defaultValue = "0")
    val updateDraftRevision: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val draftRevision: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val officialNumberJustAssigned: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val allDay: Boolean = false,
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
        conclusionState = ConclusionState.entries.firstOrNull { it.name == conclusionState }
            ?: ConclusionState.NAO_DEFINIDO,
        flow = ServiceOrderFlow.entries.firstOrNull { it.name == flow }
            ?: when (ServiceOrderPreset.entries.firstOrNull { it.name == preset }) {
                ServiceOrderPreset.SERVICO_SOLICITADO -> ServiceOrderFlow.REQUEST
                else -> ServiceOrderFlow.RESOLUTION
            },
        technicalOpinion = TechnicalOpinion.entries.firstOrNull { it.name == technicalOpinion }
            ?: TechnicalOpinion.CONCLUDED,
        observations = observations,
        updateDraft = updateDraft,
        updateDraftRevision = updateDraftRevision,
        draftRevision = draftRevision,
        officialNumberJustAssigned = officialNumberJustAssigned,
        allDay = allDay,
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
            conclusionState = structured.conclusionState.name,
            flow = structured.normalizedFlow().name,
            technicalOpinion = structured.technicalOpinion.name,
            observations = structured.observations,
            updateDraft = structured.updateDraft,
            updateDraftRevision = structured.updateDraftRevision,
            draftRevision = structured.draftRevision,
            officialNumberJustAssigned = structured.officialNumberJustAssigned,
            allDay = structured.allDay,
            sequence = structured.sequence,
            scheduledStart = structured.scheduledStart,
            scheduledEnd = structured.scheduledEnd
        )
    }
}
