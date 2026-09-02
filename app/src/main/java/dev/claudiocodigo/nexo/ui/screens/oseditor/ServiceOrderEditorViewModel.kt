package dev.claudiocodigo.nexo.ui.screens.oseditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.claudiocodigo.nexo.data.preferences.RecentServiceOrderPreferences
import dev.claudiocodigo.nexo.domain.model.ServiceOrderStatus
import dev.claudiocodigo.nexo.domain.repository.ServiceOrderRepository
import dev.claudiocodigo.nexo.domain.serviceorder.ConclusionState
import dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderItem
import dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderPreset
import dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderFlow
import dev.claudiocodigo.nexo.domain.serviceorder.TechnicalOpinion
import dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderUpdate
import dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderValidation
import dev.claudiocodigo.nexo.domain.serviceorder.ValidationResult
import dev.claudiocodigo.nexo.domain.time.ClockProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ServiceOrderEditorViewModel @Inject constructor(
    private val repository: ServiceOrderRepository,
    private val preferences: RecentServiceOrderPreferences,
    private val clock: ClockProvider
) : ViewModel() {

    private val _state = MutableStateFlow(ServiceOrderEditorState())
    val state: StateFlow<ServiceOrderEditorState> = _state.asStateFlow()

    private var autosaveJob: Job? = null
    private val saveMutex = Mutex()
    private var revision = 0L
    private var preparingPublication = false

    fun loadOrder(orderId: UUID) {
        viewModelScope.launch {
            val structured = repository.getStructuredOrderById(orderId)
            val recentTech = preferences.recentTechnician.firstOrNull()
            val recentClient = preferences.recentClient.firstOrNull()
            val recentUnit = preferences.recentUnit.firstOrNull()
            val recentCategory = preferences.recentCategory.firstOrNull()

            if (structured != null) {
                _state.value = ServiceOrderEditorState(
                    id = structured.id,
                    isLinked = structured.occurrenceKey != null,
                    occurrenceKey = structured.occurrenceKey,
                    // Extracted calendar data still requires explicit technician confirmation.
                    currentStep = EditorStep.IDENTIFICACAO,
                    externalId = structured.externalId.orEmpty(),
                    title = structured.title,
                    clientName = structured.clientName,
                    unitName = structured.unitName,
                    technician = structured.technician ?: recentTech.orEmpty(),
                    category = structured.category ?: recentCategory.orEmpty(),
                    preset = structured.preset,
                    flow = structured.normalizedFlow(),
                    originalDemand = structured.originalDemand,
                    updates = structured.updates,
                    items = structured.items,
                    closureCause = structured.closureCause.orEmpty(),
                    closureSolution = structured.closureSolution.orEmpty(),
                    closurePending = structured.closurePending.orEmpty(),
                    conclusionState = structured.conclusionState,
                    technicalOpinion = structured.technicalOpinion,
                    observations = structured.observations.orEmpty(),
                    updateDraft = structured.updateDraft.orEmpty(),
                    updateDraftRevision = structured.updateDraftRevision,
                    draftRevision = structured.draftRevision,
                    status = structured.status,
                    publicationState = structured.publicationState,
                    scheduledStart = structured.scheduledStart,
                    scheduledEnd = structured.scheduledEnd,
                    allDay = structured.allDay,
                    recentTechnicianSuggestion = recentTech,
                    recentClientSuggestion = recentClient,
                    recentUnitSuggestion = recentUnit,
                    recentCategorySuggestion = recentCategory
                )
                if (structured.officialNumberJustAssigned) {
                    repository.saveStructuredOrder(structured.copy(officialNumberJustAssigned = false))
                }
            } else {
                _state.value = _state.value.copy(
                    id = orderId,
                    technician = recentTech.orEmpty(),
                    clientName = recentClient.orEmpty(),
                    unitName = recentUnit.orEmpty(),
                    category = recentCategory.orEmpty(),
                    recentTechnicianSuggestion = recentTech,
                    recentClientSuggestion = recentClient,
                    recentUnitSuggestion = recentUnit,
                    recentCategorySuggestion = recentCategory,
                    scheduledStart = clock.nowMillis(),
                    scheduledEnd = clock.nowMillis() + 3_600_000L
                )
            }
        }
    }

    fun goToStep(step: EditorStep) {
        val active = _state.value.activeSteps
        if (step !in active) return
        val current = _state.value.currentStep
        val currentIndex = active.indexOf(current).coerceAtLeast(0)
        val targetIndex = active.indexOf(step).coerceAtLeast(0)

        if (targetIndex <= currentIndex) {
            mutate { it.copy(currentStep = step) }
            return
        }

        val firstInvalid = active.subList(0, targetIndex)
            .firstNotNullOfOrNull { candidate -> validateStep(candidate)?.let { candidate to it } }

        if (firstInvalid != null) {
            _state.update {
                it.copy(currentStep = firstInvalid.first, validationError = firstInvalid.second)
            }
            return
        }
        mutate { it.copy(currentStep = step) }
    }

    fun nextStep() {
        val active = _state.value.activeSteps
        val current = _state.value.currentStep
        val currentIndex = active.indexOf(current).coerceAtLeast(0)

        val error = validateStep(current)
        if (error != null) {
            _state.update { it.copy(validationError = error) }
            return
        }

        if (currentIndex < active.lastIndex) {
            val next = active[currentIndex + 1]
            mutate { it.copy(currentStep = next) }
        }
    }

    fun previousStep() {
        val active = _state.value.activeSteps
        val current = _state.value.currentStep
        val currentIndex = active.indexOf(current).coerceAtLeast(0)

        if (currentIndex > 0) {
            val prev = active[currentIndex - 1]
            mutate { it.copy(currentStep = prev) }
        }
    }

    fun onExternalIdChange(value: String) = mutate { it.copy(externalId = value) }
    fun onTitleChange(value: String) = mutate { it.copy(title = value) }
    fun onClientNameChange(value: String) = mutate { it.copy(clientName = value) }
    fun onUnitNameChange(value: String) = mutate { it.copy(unitName = value) }
    fun onScheduledStartChange(value: Long?) = mutate { it.copy(scheduledStart = value) }
    fun onScheduledEndChange(value: Long?) = mutate { it.copy(scheduledEnd = value) }
    fun onTechnicianChange(value: String) = mutate { it.copy(technician = value) }
    fun onCategoryChange(value: String) = mutate { it.copy(category = value) }
    fun onPresetChange(preset: ServiceOrderPreset) = mutate {
        it.copy(
            preset = preset,
            flow = if (preset == ServiceOrderPreset.SERVICO_SOLICITADO) ServiceOrderFlow.REQUEST else ServiceOrderFlow.RESOLUTION
        )
    }
    fun onFlowChange(flow: ServiceOrderFlow) = mutate { current ->
        val updated = current.copy(
            flow = flow,
            preset = if (flow == ServiceOrderFlow.REQUEST) ServiceOrderPreset.SERVICO_SOLICITADO else ServiceOrderPreset.DIAGNOSTICO_CORRECAO
        )
        val active = updated.activeSteps
        if (updated.currentStep !in active) {
            updated.copy(currentStep = active.firstOrNull() ?: EditorStep.IDENTIFICACAO)
        } else {
            updated
        }
    }
    fun onDemandChange(value: String) = mutate { it.copy(originalDemand = value) }
    fun onUpdateDraftChange(value: String) = mutate { it.copy(updateDraft = value) }
    fun onObservationsChange(value: String) = mutate { it.copy(observations = value) }
    fun onClosureCauseChange(value: String) = mutate { it.copy(closureCause = value) }
    fun onClosureSolutionChange(value: String) = mutate { it.copy(closureSolution = value) }
    fun onClosurePendingChange(value: String) = mutate { it.copy(closurePending = value) }

    fun onConclusionStateChange(state: ConclusionState) = mutate {
        it.copy(
            conclusionState = state,
            status = when {
                state.isCompletion -> ServiceOrderStatus.CONCLUIDA
                state == ConclusionState.NAO_CONCLUIDO -> ServiceOrderStatus.EM_ANDAMENTO
                it.status == ServiceOrderStatus.CONCLUIDA -> ServiceOrderStatus.EM_ANDAMENTO
                else -> it.status
            }
        )
    }

    fun onTechnicalOpinionChange(opinion: TechnicalOpinion) = mutate {
        it.copy(
            technicalOpinion = opinion,
            conclusionState = if (opinion == TechnicalOpinion.NOT_CONCLUDED) ConclusionState.NAO_CONCLUIDO else ConclusionState.CONCLUIDO,
            status = ServiceOrderStatus.CONCLUIDA
        )
    }

    fun onStatusChange(status: ServiceOrderStatus) = mutate { it.copy(status = status) }

    fun addUpdate(text: String, executionDate: Long = clock.nowMillis()) {
        if (text.isBlank()) return
        mutate { current ->
            val nextSeq = (current.updates.maxOfOrNull { it.sequenceOrder } ?: 0) + 1
            val newUpdate = ServiceOrderUpdate(
                sequenceOrder = nextSeq,
                text = text.trim(),
                executionDate = executionDate,
                createdAt = clock.nowMillis()
            )
            current.copy(updates = current.updates + newUpdate)
        }
    }

    fun removeUpdate(updateId: UUID) = mutate { current ->
        current.copy(updates = current.updates.filterNot { it.id == updateId })
    }

    fun addItem(item: ServiceOrderItem) = mutate { current ->
        current.copy(items = current.items + item)
    }

    fun removeItem(itemId: UUID) = mutate { current ->
        current.copy(items = current.items.filterNot { it.id == itemId })
    }

    fun validateForPublication(): Boolean {
        val current = _state.value
        val check = when {
            current.flow == ServiceOrderFlow.UPDATE -> ServiceOrderValidation.validateForUpdate(current.toStructured())
            current.applicableRemoteAction == dev.claudiocodigo.nexo.domain.publication.OutboxAction.FINALIZE -> ServiceOrderValidation.validateForCompletion(current.toStructured())
            else -> ServiceOrderValidation.validateForPublication(current.toStructured())
        }

        return when (check) {
            is ValidationResult.Valid -> {
                _state.update { it.copy(validationError = null) }
                true
            }
            is ValidationResult.Invalid -> {
                _state.update { it.copy(validationError = check.reason) }
                false
            }
        }
    }

    fun flushNow() {
        autosaveJob?.cancel()
        viewModelScope.launch {
            persistCurrent()
        }
    }

    fun saveBeforePublication(onSaved: (UUID) -> Unit) {
        if (preparingPublication) return
        preparingPublication = true
        val orderId = _state.value.id
        autosaveJob?.cancel()
        viewModelScope.launch {
            try {
                if (persistCurrent()) onSaved(orderId)
            } finally {
                preparingPublication = false
            }
        }
    }

    private fun mutate(transform: (ServiceOrderEditorState) -> ServiceOrderEditorState) {
        revision++
        _state.update {
            val next = transform(it)
            val nextRevision = it.draftRevision + 1
            next.copy(
                draftRevision = nextRevision,
                updateDraftRevision = if (next.flow == ServiceOrderFlow.UPDATE) nextRevision else next.updateDraftRevision,
                saveState = EditorSaveState.Saving,
                validationError = null
            )
        }
        scheduleAutosave()
    }

    private fun validateStep(step: EditorStep): String? {
        val current = _state.value
        return when (step) {
            EditorStep.IDENTIFICACAO -> when {
                current.title.isBlank() -> "Informe o título do atendimento para continuar."
                current.clientName.isBlank() -> "Informe o cliente ou empresa para continuar."
                current.unitName.isBlank() -> "Informe o local ou unidade para continuar."
                else -> null
            }
            EditorStep.DEMANDA -> if (current.originalDemand.isBlank()) {
                "Descreva a demanda ou solicitação original para continuar."
            } else null
            EditorStep.ATUALIZACAO -> if (current.updateDraft.isBlank()) {
                "Informe o texto da atualização para continuar."
            } else null
            EditorStep.CONCLUSAO -> null
        }
    }

    fun discardDraft(onDiscarded: () -> Unit) {
        autosaveJob?.cancel()
        val orderId = _state.value.id
        viewModelScope.launch {
            repository.deleteStructuredOrder(orderId)
            onDiscarded()
        }
    }

    private fun scheduleAutosave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(500)
            persistCurrent()
        }
    }

    private suspend fun persistCurrent(): Boolean = saveMutex.withLock {
        val current = _state.value
        if (!current.hasMeaningfulContent) {
            _state.update { it.copy(saveState = EditorSaveState.Idle) }
            return false
        }
        val structured = current.toStructured()
        val result = runCatching {
            repository.saveStructuredOrder(structured)
            preferences.saveRecentSelections(
                technician = current.technician,
                client = current.clientName,
                unit = current.unitName,
                category = current.category
            )
        }
        result.onSuccess {
            _state.update { it.copy(saveState = EditorSaveState.Saved) }
        }.onFailure { e ->
            _state.update { it.copy(saveState = EditorSaveState.Error(e.message ?: "Erro ao salvar")) }
        }
        result.isSuccess
    }
}
