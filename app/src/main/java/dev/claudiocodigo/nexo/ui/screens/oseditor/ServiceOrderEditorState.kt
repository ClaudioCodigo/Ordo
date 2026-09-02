package dev.claudiocodigo.nexo.ui.screens.oseditor

import dev.claudiocodigo.nexo.domain.model.ServiceOrderStatus
import dev.claudiocodigo.nexo.domain.publication.OutboxAction
import dev.claudiocodigo.nexo.domain.serviceorder.ConclusionState
import dev.claudiocodigo.nexo.domain.serviceorder.PublicationState
import dev.claudiocodigo.nexo.domain.serviceorder.RemoteOccurrenceKey
import dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderItem
import dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderPreset
import dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderUpdate
import dev.claudiocodigo.nexo.domain.serviceorder.StructuredServiceOrder
import dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderFlow
import dev.claudiocodigo.nexo.domain.serviceorder.TechnicalOpinion
import java.util.UUID

sealed interface EditorSaveState {
    data object Idle : EditorSaveState
    data object Saving : EditorSaveState
    data object Saved : EditorSaveState
    data class Error(val message: String) : EditorSaveState
}

enum class EditorStep(val title: String) {
    IDENTIFICACAO("Identificação"),
    DEMANDA("Demanda"),
    ATUALIZACAO("Atualização"),
    CONCLUSAO("Conclusão")
}

data class ServiceOrderEditorState(
    val id: UUID = UUID.randomUUID(),
    val isLinked: Boolean = false,
    val occurrenceKey: RemoteOccurrenceKey? = null,
    val currentStep: EditorStep = EditorStep.IDENTIFICACAO,
    val externalId: String = "",
    val title: String = "",
    val clientName: String = "",
    val unitName: String = "",
    val technician: String = "",
    val category: String = "",
    val preset: ServiceOrderPreset = ServiceOrderPreset.DIAGNOSTICO_CORRECAO,
    val flow: ServiceOrderFlow = ServiceOrderFlow.RESOLUTION,
    val originalDemand: String = "",
    val updates: List<ServiceOrderUpdate> = emptyList(),
    val items: List<ServiceOrderItem> = emptyList(),
    val closureCause: String = "",
    val closureSolution: String = "",
    val closurePending: String = "",
    val conclusionState: ConclusionState = ConclusionState.NAO_DEFINIDO,
    val technicalOpinion: TechnicalOpinion = TechnicalOpinion.CONCLUDED,
    val observations: String = "",
    val updateDraft: String = "",
    val updateDraftRevision: Long = 0L,
    val draftRevision: Long = 0L,
    val status: ServiceOrderStatus = ServiceOrderStatus.PENDENTE,
    val publicationState: PublicationState = PublicationState.LOCAL_DRAFT,
    val scheduledStart: Long? = System.currentTimeMillis(),
    val scheduledEnd: Long? = System.currentTimeMillis() + 3_600_000L,
    val allDay: Boolean = false,
    val saveState: EditorSaveState = EditorSaveState.Idle,
    val validationError: String? = null,
    val recentTechnicianSuggestion: String? = null,
    val recentClientSuggestion: String? = null,
    val recentUnitSuggestion: String? = null,
    val recentCategorySuggestion: String? = null,
    val categorySuggestions: List<String> = emptyList()
) {
    val activeSteps: List<EditorStep>
        get() = when {
            !isLinked -> listOf(EditorStep.IDENTIFICACAO, EditorStep.DEMANDA)
            flow == ServiceOrderFlow.UPDATE -> listOf(EditorStep.IDENTIFICACAO, EditorStep.ATUALIZACAO)
            else -> listOf(EditorStep.IDENTIFICACAO, EditorStep.DEMANDA, EditorStep.CONCLUSAO)
        }

    val hasMeaningfulContent: Boolean
        get() = isLinked ||
            title.isNotBlank() ||
            originalDemand.isNotBlank() ||
            externalId.isNotBlank() ||
            updates.isNotEmpty() ||
            updateDraft.isNotBlank() ||
            closureSolution.isNotBlank() ||
            closureCause.isNotBlank() ||
            observations.isNotBlank()

    val applicableRemoteAction: OutboxAction
        get() = when {
            !isLinked -> OutboxAction.CREATE
            flow == ServiceOrderFlow.UPDATE -> OutboxAction.UPDATE
            conclusionState == ConclusionState.NAO_CONCLUIDO && technicalOpinion == TechnicalOpinion.CONCLUDED -> OutboxAction.UPDATE
            else -> OutboxAction.FINALIZE
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
        flow = flow,
        originalDemand = originalDemand.trim(),
        status = when {
            conclusionState.isCompletion -> ServiceOrderStatus.CONCLUIDA
            conclusionState == ConclusionState.NAO_CONCLUIDO && status == ServiceOrderStatus.CONCLUIDA -> ServiceOrderStatus.EM_ANDAMENTO
            else -> status
        },
        publicationState = publicationState,
        updates = updates,
        items = items,
        closureCause = closureCause.trim().ifBlank { null },
        closureSolution = closureSolution.trim().ifBlank { null },
        closurePending = closurePending.trim().ifBlank { null },
        conclusionState = conclusionState,
        technicalOpinion = technicalOpinion,
        observations = observations.trim().ifBlank { null },
        updateDraft = updateDraft.trim().ifBlank { null },
        updateDraftRevision = updateDraftRevision,
        draftRevision = draftRevision,
        scheduledStart = scheduledStart,
        scheduledEnd = scheduledEnd,
        allDay = allDay
    )
}
