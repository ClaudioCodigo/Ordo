package dev.claudiocodigo.nexo.ui.screens.oseditor

import dev.claudiocodigo.nexo.domain.model.ServiceOrderStatus
import dev.claudiocodigo.nexo.domain.publication.OutboxAction
import dev.claudiocodigo.nexo.domain.serviceorder.PublicationState
import dev.claudiocodigo.nexo.domain.serviceorder.RemoteOccurrenceKey
import dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderItem
import dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderPreset
import dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderUpdate
import dev.claudiocodigo.nexo.domain.serviceorder.StructuredServiceOrder
import java.util.UUID

sealed interface EditorSaveState {
    data object Idle : EditorSaveState
    data object Saving : EditorSaveState
    data object Saved : EditorSaveState
    data class Error(val message: String) : EditorSaveState
}

data class ServiceOrderEditorState(
    val id: UUID = UUID.randomUUID(),
    val isLinked: Boolean = false,
    val occurrenceKey: RemoteOccurrenceKey? = null,
    val externalId: String = "",
    val title: String = "",
    val clientName: String = "",
    val unitName: String = "",
    val technician: String = "",
    val category: String = "",
    val preset: ServiceOrderPreset = ServiceOrderPreset.DIAGNOSTICO_CORRECAO,
    val originalDemand: String = "",
    val updates: List<ServiceOrderUpdate> = emptyList(),
    val items: List<ServiceOrderItem> = emptyList(),
    val closureCause: String = "",
    val closureSolution: String = "",
    val closurePending: String = "",
    val status: ServiceOrderStatus = ServiceOrderStatus.PENDENTE,
    val publicationState: PublicationState = PublicationState.LOCAL_DRAFT,
    val scheduledStart: Long? = null,
    val scheduledEnd: Long? = null,
    val saveState: EditorSaveState = EditorSaveState.Idle,
    val validationError: String? = null,
    val recentTechnicianSuggestion: String? = null,
    val recentClientSuggestion: String? = null,
    val recentUnitSuggestion: String? = null
) {
    val applicableRemoteAction: OutboxAction
        get() = when {
            status == ServiceOrderStatus.CONCLUIDA -> OutboxAction.FINALIZE
            isLinked -> OutboxAction.UPDATE
            else -> OutboxAction.CREATE
        }

    fun toStructured(): StructuredServiceOrder = StructuredServiceOrder(
        id = id,
        occurrenceKey = occurrenceKey,
        externalId = externalId.trim().ifBlank { null },
        title = title.trim(),
        clientName = clientName.trim(),
        unitName = unitName.trim(),
        technician = technician.trim().ifBlank { null },
        category = category.trim().ifBlank { null },
        preset = preset,
        originalDemand = originalDemand.trim(),
        status = status,
        publicationState = publicationState,
        updates = updates,
        items = items,
        closureCause = closureCause.trim().ifBlank { null },
        closureSolution = closureSolution.trim().ifBlank { null },
        closurePending = closurePending.trim().ifBlank { null },
        scheduledStart = scheduledStart,
        scheduledEnd = scheduledEnd
    )
}
