package dev.claudiocodigo.nexo.ui.screens.nova

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.claudiocodigo.nexo.domain.model.ServiceOrder
import dev.claudiocodigo.nexo.domain.repository.ServiceOrderRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject

data class NovaOSFormState(
    val internalId: UUID,
    val externalId: String = "",
    val title: String = "",
    val clientName: String = "",
    val unitName: String = "",
    val description: String = "",
    val scheduledDate: Long,
    val validationError: String? = null,
    val saveState: NovaDraftSaveState = NovaDraftSaveState.Idle
) {
    val isSaving: Boolean get() = saveState == NovaDraftSaveState.Saving
}

sealed interface NovaDraftSaveState {
    data object Idle : NovaDraftSaveState
    data object Saving : NovaDraftSaveState
    data object SavedLocally : NovaDraftSaveState
    data class Error(val message: String) : NovaDraftSaveState
}

@HiltViewModel
class NovaOSViewModel @Inject constructor(
    private val repository: ServiceOrderRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _form = MutableStateFlow(initialState())
    val form: StateFlow<NovaOSFormState> = _form.asStateFlow()

    private val _savedOrderIds = MutableSharedFlow<UUID>(extraBufferCapacity = 1)
    val savedOrderIds: SharedFlow<UUID> = _savedOrderIds.asSharedFlow()

    private val saveMutex = Mutex()
    private var debounceJob: Job? = null
    private val saveJobs = mutableListOf<Job>()
    private var revision = 0L
    private var persistedRevision = 0L

    init {
        if (hasMeaningfulInput(_form.value)) {
            revision = 1L
            _form.value = _form.value.copy(saveState = NovaDraftSaveState.Saving)
            scheduleAutosave()
        }
    }

    fun onExternalIdChange(value: String) = update { it.copy(externalId = value) }
    fun onTitleChange(value: String) = update { it.copy(title = value) }
    fun onClientChange(value: String) = update { it.copy(clientName = value) }
    fun onUnitChange(value: String) = update { it.copy(unitName = value) }
    fun onDescriptionChange(value: String) = update { it.copy(description = value) }

    fun save() {
        val current = _form.value
        val invalid = requiredMissing(current)
        if (invalid != null) {
            _form.value = current.copy(validationError = "Preencha antes de salvar: $invalid")
            return
        }
        debounceJob?.cancel()
        requestSave(finalSave = true)
    }

    private fun update(transform: (NovaOSFormState) -> NovaOSFormState) {
        val next = transform(_form.value).copy(validationError = null)
        revision++
        _form.value = next.copy(
            saveState = if (hasMeaningfulInput(next)) NovaDraftSaveState.Saving else NovaDraftSaveState.Idle
        )
        savedStateHandle[KEY_EXTERNAL_ID] = next.externalId
        savedStateHandle[KEY_TITLE] = next.title
        savedStateHandle[KEY_CLIENT] = next.clientName
        savedStateHandle[KEY_UNIT] = next.unitName
        savedStateHandle[KEY_DESCRIPTION] = next.description
        savedStateHandle[KEY_DATE] = next.scheduledDate
        if (hasMeaningfulInput(next)) scheduleAutosave()
    }

    private fun scheduleAutosave() {
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(AUTOSAVE_DEBOUNCE_MS)
            requestSave(finalSave = false)
        }
    }

    private fun requestSave(finalSave: Boolean) {
        val job = viewModelScope.launch { persistCurrent(finalSave) }
        saveJobs += job
        job.invokeOnCompletion { saveJobs.remove(job) }
    }

    fun flushNow() {
        debounceJob?.cancel()
        if (hasMeaningfulInput(_form.value) && revision > persistedRevision) requestSave(finalSave = false)
    }

    suspend fun flushAndAwait(): Boolean {
        debounceJob?.cancel()
        saveJobs.toList().joinAll()
        if (hasMeaningfulInput(_form.value) && revision > persistedRevision) persistCurrent(finalSave = false)
        return revision == persistedRevision || !hasMeaningfulInput(_form.value)
    }

    fun saveBeforeExit(onSaved: () -> Unit, onFailed: () -> Unit = {}) {
        viewModelScope.launch {
            if (flushAndAwait()) onSaved() else onFailed()
        }
    }

    private suspend fun persistCurrent(finalSave: Boolean): Boolean = saveMutex.withLock {
        val current = _form.value
        if (!hasMeaningfulInput(current)) return@withLock true
        val snapshotRevision = revision
        _form.value = current.copy(saveState = NovaDraftSaveState.Saving)
        val order = current.toServiceOrder()
        val result = runCatching { repository.saveServiceOrder(order) }
        val latest = _form.value
        if (result.isSuccess) {
            if (snapshotRevision == revision) {
                persistedRevision = snapshotRevision
                _form.value = latest.copy(saveState = NovaDraftSaveState.SavedLocally)
                if (finalSave) _savedOrderIds.emit(order.id)
                true
            } else {
                _form.value = latest.copy(saveState = NovaDraftSaveState.Saving)
                scheduleAutosave()
                false
            }
        } else {
            val message = result.exceptionOrNull()?.message ?: "Não foi possível salvar localmente."
            _form.value = latest.copy(
                saveState = NovaDraftSaveState.Error(message),
                validationError = message
            )
            if (snapshotRevision != revision) scheduleAutosave()
            false
        }
    }

    private fun initialState(): NovaOSFormState {
        val id = savedStateHandle.get<String>(KEY_ID)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: UUID.randomUUID().also { savedStateHandle[KEY_ID] = it.toString() }
        val date = savedStateHandle.get<Long>(KEY_DATE) ?: startOfToday().also { savedStateHandle[KEY_DATE] = it }
        return NovaOSFormState(
            internalId = id,
            externalId = savedStateHandle[KEY_EXTERNAL_ID] ?: "",
            title = savedStateHandle[KEY_TITLE] ?: "",
            clientName = savedStateHandle[KEY_CLIENT] ?: "",
            unitName = savedStateHandle[KEY_UNIT] ?: "",
            description = savedStateHandle[KEY_DESCRIPTION] ?: "",
            scheduledDate = date,
            saveState = NovaDraftSaveState.Idle
        )
    }

    private fun hasMeaningfulInput(form: NovaOSFormState) = listOf(
        form.externalId, form.title, form.clientName, form.unitName, form.description
    ).any { it.isNotBlank() }

    private fun requiredMissing(form: NovaOSFormState): String? = listOfNotNull(
        if (form.title.isBlank()) "título" else null,
        if (form.clientName.isBlank()) "empresa/cliente" else null,
        if (form.unitName.isBlank()) "unidade/local" else null,
        if (form.description.isBlank()) "demanda/descrição" else null
    ).joinToString(", ").ifBlank { null }

    private fun NovaOSFormState.toServiceOrder() = ServiceOrder(
        id = internalId,
        externalId = externalId.trim().ifBlank { null },
        title = title.trim(),
        description = description.trim(),
        clientName = clientName.trim(),
        unitName = unitName.trim(),
        scheduledDate = scheduledDate
    )

    companion object {
        private const val AUTOSAVE_DEBOUNCE_MS = 500L
        private const val KEY_ID = "nova_os_id"
        private const val KEY_EXTERNAL_ID = "nova_os_external_id"
        private const val KEY_TITLE = "nova_os_title"
        private const val KEY_CLIENT = "nova_os_client"
        private const val KEY_UNIT = "nova_os_unit"
        private const val KEY_DESCRIPTION = "nova_os_description"
        private const val KEY_DATE = "nova_os_date"

        private fun startOfToday(): Long = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
