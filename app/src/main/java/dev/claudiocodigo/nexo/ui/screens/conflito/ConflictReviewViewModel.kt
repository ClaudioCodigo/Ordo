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
        val remoteExternalId: String?,
        val remoteTitle: String,
        val remoteDemand: String,
        val remoteCause: String?,
        val remoteSolution: String?,
        val remotePending: String?,
        val remoteRawSummary: String?,
        val remoteRawDescription: String?,
        val remoteRawIcs: String?,
        val remoteClientName: String? = null,
        val remoteUnitName: String? = null,
        val remoteTechnician: String? = null,
        val remoteCategory: String? = null
    ) : ConflictUiState
    data class DeletedRemotely(val orderId: UUID, val isDraft: Boolean) : ConflictUiState
    data class Error(val message: String) : ConflictUiState
}

@HiltViewModel
class ConflictReviewViewModel @Inject constructor(
    private val serviceOrderRepository: ServiceOrderRepository,
    private val calendarRepository: CalendarRepository,
    private val publicationRepository: dev.claudiocodigo.nexo.domain.publication.PublicationRepository
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
            if (remoteEvent == null) {
                _uiState.value = ConflictUiState.DeletedRemotely(
                    orderId = orderId,
                    isDraft = local.publicationState == dev.claudiocodigo.nexo.domain.serviceorder.PublicationState.LOCAL_DRAFT
                )
                return@launch
            }

            val remoteSummaryExtracted = ServiceOrderExtractor.extractSummary(remoteEvent?.summary)
            val remoteTitle = remoteSummaryExtracted.title
            val remoteDescExtracted = ServiceOrderExtractor.extractDescription(remoteEvent?.description)
            val remoteExternalId = remoteSummaryExtracted.externalId ?: remoteDescExtracted.externalId

            val changeAnalysis = ServiceOrderDiff.analyzeRemoteChange(
                localOrder = local,
                remoteExternalId = remoteExternalId,
                remoteDemand = remoteDescExtracted.originalDemand,
                remoteTitle = remoteTitle,
                remoteCause = remoteDescExtracted.closureCause,
                remoteSolution = remoteDescExtracted.closureSolution,
                remotePending = remoteDescExtracted.closurePending,
                remoteRawSummary = remoteEvent.summary,
                remoteRawDescription = remoteEvent.description
            )

            if (changeAnalysis.hasUnmappedRemoteTextChange) {
                _uiState.value = ConflictUiState.Error(
                    "O calendário mudou em um texto que não pode ser comparado com segurança. " +
                        "O rascunho local e a base anterior foram preservados."
                )
                return@launch
            }

            val diffs = changeAnalysis.differences

            val initialChoices = diffs.associate { it.field to FieldChoice.KEEP_LOCAL }

            _uiState.value = ConflictUiState.Ready(
                order = local,
                differences = diffs,
                choices = initialChoices,
                remoteEtag = remoteEvent?.etag,
                remoteExternalId = remoteExternalId,
                remoteTitle = remoteTitle,
                remoteDemand = remoteDescExtracted.originalDemand,
                remoteCause = remoteDescExtracted.closureCause,
                remoteSolution = remoteDescExtracted.closureSolution,
                remotePending = remoteDescExtracted.closurePending,
                remoteRawSummary = remoteEvent?.summary,
                remoteRawDescription = remoteEvent?.description,
                remoteRawIcs = remoteEvent?.rawIcs,
                remoteClientName = remoteSummaryExtracted.clientName,
                remoteUnitName = remoteSummaryExtracted.unitName,
                remoteTechnician = remoteSummaryExtracted.technician,
                remoteCategory = remoteSummaryExtracted.category
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
                remoteExternalId = ready.remoteExternalId,
                remoteDemand = ready.remoteDemand,
                remoteTitle = ready.remoteTitle,
                remoteCause = ready.remoteCause,
                remoteSolution = ready.remoteSolution,
                remotePending = ready.remotePending,
                newEtag = ready.remoteEtag,
                remoteRawSummary = ready.remoteRawSummary,
                remoteRawDescription = ready.remoteRawDescription,
                remoteRawIcs = ready.remoteRawIcs,
                remoteClientName = ready.remoteClientName,
                remoteUnitName = ready.remoteUnitName,
                remoteTechnician = ready.remoteTechnician,
                remoteCategory = ready.remoteCategory
            )

            serviceOrderRepository.saveStructuredOrder(resolved)
            onResolved()
        }
    }

    fun recreateLocally(onResolved: () -> Unit) {
        val deleted = _uiState.value as? ConflictUiState.DeletedRemotely ?: return
        viewModelScope.launch {
            val order = serviceOrderRepository.getStructuredOrderById(deleted.orderId) ?: return@launch
            // Clear base snapshot and etag so it behaves like a new unsynced order
            val recreated = order.copy(
                baseSnapshot = null,
                occurrenceKey = null,
                publicationState = dev.claudiocodigo.nexo.domain.serviceorder.PublicationState.LOCAL_DRAFT
            )
            serviceOrderRepository.saveStructuredOrder(recreated)
            onResolved()
        }
    }

    fun discardLocally(onResolved: () -> Unit) {
        val deleted = _uiState.value as? ConflictUiState.DeletedRemotely ?: return
        viewModelScope.launch {
            publicationRepository.cancelAllForOrder(deleted.orderId)
            serviceOrderRepository.deleteServiceOrder(deleted.orderId)
            onResolved()
        }
    }
}
