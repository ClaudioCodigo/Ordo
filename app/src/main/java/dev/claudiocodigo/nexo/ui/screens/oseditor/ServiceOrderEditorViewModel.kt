package dev.claudiocodigo.nexo.ui.screens.oseditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.claudiocodigo.nexo.data.preferences.RecentServiceOrderPreferences
import dev.claudiocodigo.nexo.domain.model.ServiceOrderStatus
import dev.claudiocodigo.nexo.domain.repository.ServiceOrderRepository
import dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderItem
import dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderPreset
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

    fun loadOrder(orderId: UUID) {
        viewModelScope.launch {
            val structured = repository.getStructuredOrderById(orderId)
            val recentTech = preferences.recentTechnician.firstOrNull()
            val recentClient = preferences.recentClient.firstOrNull()
            val recentUnit = preferences.recentUnit.firstOrNull()

            if (structured != null) {
                _state.value = ServiceOrderEditorState(
                    id = structured.id,
                    isLinked = structured.occurrenceKey != null,
                    occurrenceKey = structured.occurrenceKey,
                    externalId = structured.externalId.orEmpty(),
                    title = structured.title,
                    clientName = structured.clientName,
                    unitName = structured.unitName,
                    technician = structured.technician ?: recentTech.orEmpty(),
                    category = structured.category.orEmpty(),
                    preset = structured.preset,
                    originalDemand = structured.originalDemand,
                    updates = structured.updates,
                    items = structured.items,
                    closureCause = structured.closureCause.orEmpty(),
                    closureSolution = structured.closureSolution.orEmpty(),
                    closurePending = structured.closurePending.orEmpty(),
                    status = structured.status,
                    publicationState = structured.publicationState,
                    scheduledStart = structured.scheduledStart,
                    scheduledEnd = structured.scheduledEnd,
                    recentTechnicianSuggestion = recentTech,
                    recentClientSuggestion = recentClient,
                    recentUnitSuggestion = recentUnit
                )
            } else {
                _state.value = _state.value.copy(
                    id = orderId,
                    technician = recentTech.orEmpty(),
                    clientName = recentClient.orEmpty(),
                    unitName = recentUnit.orEmpty(),
                    recentTechnicianSuggestion = recentTech,
                    recentClientSuggestion = recentClient,
                    recentUnitSuggestion = recentUnit
                )
            }
        }
    }

    fun onExternalIdChange(value: String) = mutate { it.copy(externalId = value) }
    fun onTitleChange(value: String) = mutate { it.copy(title = value) }
    fun onClientNameChange(value: String) = mutate { it.copy(clientName = value) }
    fun onUnitNameChange(value: String) = mutate { it.copy(unitName = value) }
    fun onTechnicianChange(value: String) = mutate { it.copy(technician = value) }
    fun onCategoryChange(value: String) = mutate { it.copy(category = value) }
    fun onPresetChange(preset: ServiceOrderPreset) = mutate { it.copy(preset = preset) }
    fun onDemandChange(value: String) = mutate { it.copy(originalDemand = value) }
    fun onClosureCauseChange(value: String) = mutate { it.copy(closureCause = value) }
    fun onClosureSolutionChange(value: String) = mutate { it.copy(closureSolution = value) }
    fun onClosurePendingChange(value: String) = mutate { it.copy(closurePending = value) }

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
        val check = if (_state.value.status == ServiceOrderStatus.CONCLUIDA) {
            ServiceOrderValidation.validateForCompletion(_state.value.toStructured())
        } else {
            ServiceOrderValidation.validateForPublication(_state.value.toStructured())
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

    private fun mutate(transform: (ServiceOrderEditorState) -> ServiceOrderEditorState) {
        revision++
        _state.update { transform(it).copy(saveState = EditorSaveState.Saving, validationError = null) }
        scheduleAutosave()
    }

    private fun scheduleAutosave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(500)
            persistCurrent()
        }
    }

    private suspend fun persistCurrent() = saveMutex.withLock {
        val current = _state.value
        val structured = current.toStructured()
        runCatching {
            repository.saveStructuredOrder(structured)
            preferences.saveRecentSelections(
                technician = current.technician,
                client = current.clientName,
                unit = current.unitName
            )
        }.onSuccess {
            _state.update { it.copy(saveState = EditorSaveState.Saved) }
        }.onFailure { e ->
            _state.update { it.copy(saveState = EditorSaveState.Error(e.message ?: "Erro ao salvar")) }
        }
    }
}
