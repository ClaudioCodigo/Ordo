package dev.claudiocodigo.nexo.ui.screens.detalhes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.claudiocodigo.nexo.domain.model.ServiceOrder
import dev.claudiocodigo.nexo.domain.model.ServiceOrderStatus
import dev.claudiocodigo.nexo.domain.repository.ServiceOrderRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class DetalhesViewModel @Inject constructor(
    private val repository: ServiceOrderRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _uiState = MutableStateFlow<DetalhesUiState>(DetalhesUiState.Loading)
    val uiState: StateFlow<DetalhesUiState> = _uiState.asStateFlow()

    private val saveMutex = Mutex()
    private var debounceJob: Job? = null
    private val saveJobs = mutableListOf<Job>()
    private var loadedId: UUID? = null
    private var revision = 0L
    private var persistedRevision = 0L

    private fun key(id: UUID, field: String) = "draft_${id}_$field"

    fun loadServiceOrder(id: String) {
        val parsedId = runCatching { UUID.fromString(id) }.getOrNull()
        if (parsedId == null) {
            _uiState.value = DetalhesUiState.Error("ID inválido")
            return
        }
        if (loadedId == parsedId && _uiState.value is DetalhesUiState.Success) return

        viewModelScope.launch {
            val os = runCatching { repository.getServiceOrderById(parsedId) }.getOrNull()
            if (os == null) {
                _uiState.value = DetalhesUiState.Error("OS não encontrada")
                return@launch
            }

            val restored = os.copy(
                externalId = restoredOptional(parsedId, "externalId", os.externalId),
                title = restoredString(parsedId, "title", os.title),
                clientName = restoredString(parsedId, "clientName", os.clientName),
                unitName = restoredString(parsedId, "unitName", os.unitName),
                description = restoredString(parsedId, "description", os.description)
            )
            val differs = editablePart(restored) != editablePart(os)
            loadedId = parsedId
            revision = if (differs) 1L else 0L
            persistedRevision = 0L
            _uiState.value = DetalhesUiState.Success(
                os = restored,
                saveState = if (differs) DetalhesSaveState.Saving else DetalhesSaveState.SavedLocally
            )
            if (differs) scheduleAutosave()
        }
    }

    fun updateExternalId(value: String) = updateDraft { it.copy(externalId = value.trim().ifBlank { null }) }
    fun updateTitle(value: String) = updateDraft { it.copy(title = value) }
    fun updateClientName(value: String) = updateDraft { it.copy(clientName = value) }
    fun updateUnitName(value: String) = updateDraft { it.copy(unitName = value) }
    fun updateDescription(value: String) = updateDraft { it.copy(description = value) }

    private fun updateDraft(transform: (ServiceOrder) -> ServiceOrder) {
        val state = _uiState.value as? DetalhesUiState.Success ?: return
        val next = transform(state.os)
        revision++
        savedStateHandle[key(next.id, "externalId")] = next.externalId ?: ""
        savedStateHandle[key(next.id, "title")] = next.title
        savedStateHandle[key(next.id, "clientName")] = next.clientName
        savedStateHandle[key(next.id, "unitName")] = next.unitName
        savedStateHandle[key(next.id, "description")] = next.description
        _uiState.value = state.copy(
            os = next,
            saveState = DetalhesSaveState.Saving,
            validationError = null
        )
        scheduleAutosave()
    }

    private fun scheduleAutosave() {
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(AUTOSAVE_DEBOUNCE_MS)
            requestSave()
        }
    }

    private fun requestSave() {
        val job = viewModelScope.launch { persistCurrent() }
        saveJobs += job
        job.invokeOnCompletion { saveJobs.remove(job) }
    }

    fun saveDraft() {
        debounceJob?.cancel()
        requestSave()
    }

    fun flushNow() {
        debounceJob?.cancel()
        if (revision > persistedRevision) requestSave()
    }

    /** Awaits all saves already queued and one final snapshot before leaving the screen. */
    suspend fun flushAndAwait(): Boolean {
        debounceJob?.cancel()
        saveJobs.toList().joinAll()
        if (revision > persistedRevision) persistCurrent()
        return revision == persistedRevision
    }

    fun saveBeforeExit(onSaved: () -> Unit, onFailed: () -> Unit = {}) {
        viewModelScope.launch {
            if (flushAndAwait()) onSaved() else onFailed()
        }
    }

    fun requestFinish() {
        val state = _uiState.value as? DetalhesUiState.Success ?: return
        val missing = requiredMissing(state.os)
        if (missing != null) {
            _uiState.value = state.copy(validationError = "Preencha antes de finalizar: $missing")
            return
        }
        setPendingAction(DetalhesAction.FINALIZAR)
    }

    fun requestReopen() = setPendingAction(DetalhesAction.REABRIR)

    fun cancelPendingAction() {
        val state = _uiState.value as? DetalhesUiState.Success ?: return
        _uiState.value = state.copy(pendingAction = null)
    }

    fun confirmPendingAction() {
        val state = _uiState.value as? DetalhesUiState.Success ?: return
        val action = state.pendingAction ?: return
        val newStatus = when (action) {
            DetalhesAction.FINALIZAR -> ServiceOrderStatus.CONCLUIDA
            DetalhesAction.REABRIR -> ServiceOrderStatus.EM_ANDAMENTO
        }
        revision++
        _uiState.value = state.copy(
            os = state.os.copy(status = newStatus),
            pendingAction = null,
            saveState = DetalhesSaveState.Saving,
            validationError = null
        )
        requestSave()
    }

    private suspend fun persistCurrent(): Boolean = saveMutex.withLock {
        val state = _uiState.value as? DetalhesUiState.Success ?: return@withLock false
        val snapshotRevision = revision
        val snapshot = state.os.copy(updatedAt = System.currentTimeMillis())
        _uiState.value = state.copy(saveState = DetalhesSaveState.Saving)
        val result = runCatching { repository.saveServiceOrder(snapshot) }
        val latest = _uiState.value as? DetalhesUiState.Success ?: return@withLock false

        if (result.isSuccess) {
            if (revision == snapshotRevision) {
                persistedRevision = snapshotRevision
                _uiState.value = latest.copy(
                    os = latest.os.copy(updatedAt = snapshot.updatedAt),
                    saveState = DetalhesSaveState.SavedLocally
                )
                true
            } else {
                // A newer edit exists: never replace it with the old snapshot.
                _uiState.value = latest.copy(saveState = DetalhesSaveState.Saving)
                scheduleAutosave()
                false
            }
        } else {
            _uiState.value = latest.copy(
                saveState = if (revision == snapshotRevision) {
                    DetalhesSaveState.Error(result.exceptionOrNull()?.message ?: "Não foi possível salvar localmente")
                } else DetalhesSaveState.Saving
            )
            if (revision != snapshotRevision) scheduleAutosave()
            false
        }
    }

    private fun setPendingAction(action: DetalhesAction) {
        val state = _uiState.value as? DetalhesUiState.Success ?: return
        _uiState.value = state.copy(pendingAction = action)
    }

    private fun restoredString(id: UUID, field: String, fallback: String): String =
        if (savedStateHandle.contains(key(id, field))) savedStateHandle.get<String>(key(id, field)) ?: "" else fallback

    private fun restoredOptional(id: UUID, field: String, fallback: String?): String? =
        if (savedStateHandle.contains(key(id, field))) savedStateHandle.get<String>(key(id, field)).orEmpty().ifBlank { null } else fallback

    private fun editablePart(os: ServiceOrder) = listOf(os.externalId, os.title, os.clientName, os.unitName, os.description)

    private fun requiredMissing(os: ServiceOrder): String? = listOfNotNull(
        if (os.title.isBlank()) "título" else null,
        if (os.clientName.isBlank()) "empresa/cliente" else null,
        if (os.unitName.isBlank()) "unidade/local" else null,
        if (os.description.isBlank()) "demanda/descrição" else null
    ).joinToString(", ").ifBlank { null }

    companion object {
        private const val AUTOSAVE_DEBOUNCE_MS = 500L
    }
}

enum class DetalhesAction { FINALIZAR, REABRIR }

sealed interface DetalhesSaveState {
    data object Idle : DetalhesSaveState
    data object Saving : DetalhesSaveState
    data object SavedLocally : DetalhesSaveState
    data class Error(val message: String) : DetalhesSaveState
}

sealed interface DetalhesUiState {
    data object Loading : DetalhesUiState
    data class Success(
        val os: ServiceOrder,
        val saveState: DetalhesSaveState = DetalhesSaveState.Idle,
        val pendingAction: DetalhesAction? = null,
        val validationError: String? = null
    ) : DetalhesUiState
    data class Error(val message: String) : DetalhesUiState
}
