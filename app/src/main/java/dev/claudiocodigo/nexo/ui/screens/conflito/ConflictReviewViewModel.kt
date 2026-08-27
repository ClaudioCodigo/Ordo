package dev.claudiocodigo.nexo.ui.screens.conflito

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.claudiocodigo.nexo.domain.repository.CalendarRepository
import dev.claudiocodigo.nexo.domain.repository.ServiceOrderRepository
import dev.claudiocodigo.nexo.domain.serviceorder.ConflictField
import dev.claudiocodigo.nexo.domain.serviceorder.FieldChoice
import dev.claudiocodigo.nexo.domain.serviceorder.FieldDifference
import dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderDiff
import dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderExtractor
import dev.claudiocodigo.nexo.domain.serviceorder.StructuredServiceOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

sealed interface ConflictUiState {
    data object Loading : ConflictUiState
    data class Ready(
        val order: StructuredServiceOrder,
        val differences: List<FieldDifference>,
        val choices: Map<ConflictField, FieldChoice>,
        val remoteEtag: String?,
        val remoteTitle: String,
        val remoteDemand: String,
        val remoteCause: String?,
        val remoteSolution: String?,
        val remotePending: String?
    ) : ConflictUiState
    data class Error(val message: String) : ConflictUiState
}

@HiltViewModel
class ConflictReviewViewModel @Inject constructor(
    private val serviceOrderRepository: ServiceOrderRepository,
    private val calendarRepository: CalendarRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ConflictUiState>(ConflictUiState.Loading)
    val uiState: StateFlow<ConflictUiState> = _uiState.asStateFlow()

    fun load(orderId: UUID) {
        viewModelScope.launch {
            val local = serviceOrderRepository.getStructuredOrderById(orderId)
            if (local == null) {
                _uiState.value = ConflictUiState.Error("OS não encontrada")
                return@launch
            }

            val key = local.occurrenceKey
            val remoteEvent = if (key != null) {
                calendarRepository.getEvent(key.accountId, key.calendarHref, key.eventHref)
            } else null

            val remoteTitle = remoteEvent?.summary ?: local.title
            val remoteDescExtracted = ServiceOrderExtractor.extractDescription(remoteEvent?.description)

            val diffs = ServiceOrderDiff.computeDifferences(
                localOrder = local,
                remoteDemand = remoteDescExtracted.originalDemand,
                remoteTitle = remoteTitle,
                remoteCause = remoteDescExtracted.closureCause,
                remoteSolution = remoteDescExtracted.closureSolution,
                remotePending = remoteDescExtracted.closurePending
            )

            val initialChoices = diffs.associate { it.field to FieldChoice.KEEP_LOCAL }

            _uiState.value = ConflictUiState.Ready(
                order = local,
                differences = diffs,
                choices = initialChoices,
                remoteEtag = remoteEvent?.etag,
                remoteTitle = remoteTitle,
                remoteDemand = remoteDescExtracted.originalDemand,
                remoteCause = remoteDescExtracted.closureCause,
                remoteSolution = remoteDescExtracted.closureSolution,
                remotePending = remoteDescExtracted.closurePending
            )
        }
    }

    fun onChoiceSelected(field: ConflictField, choice: FieldChoice) {
        val ready = _uiState.value as? ConflictUiState.Ready ?: return
        val newChoices = ready.choices.toMutableMap()
        newChoices[field] = choice
        _uiState.value = ready.copy(choices = newChoices)
    }

    fun applyResolution(onResolved: () -> Unit) {
        val ready = _uiState.value as? ConflictUiState.Ready ?: return

        viewModelScope.launch {
            val resolved = ServiceOrderDiff.applyChoices(
                localOrder = ready.order,
                choices = ready.choices,
                remoteDemand = ready.remoteDemand,
                remoteTitle = ready.remoteTitle,
                remoteCause = ready.remoteCause,
                remoteSolution = ready.remoteSolution,
                remotePending = ready.remotePending,
                newEtag = ready.remoteEtag
            )

            serviceOrderRepository.saveStructuredOrder(resolved)
            onResolved()
        }
    }
}
