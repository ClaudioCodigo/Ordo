package dev.claudiocodigo.nexo.ui.screens.preview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.claudiocodigo.nexo.data.ical.IcsDocumentEditor
import dev.claudiocodigo.nexo.data.worker.PublicationScheduler
import dev.claudiocodigo.nexo.domain.model.ServiceOrderStatus
import dev.claudiocodigo.nexo.domain.publication.ConfirmedPreviewSnapshot
import dev.claudiocodigo.nexo.domain.publication.OutboxAction
import dev.claudiocodigo.nexo.domain.publication.PublicationRepository
import dev.claudiocodigo.nexo.domain.repository.ServiceOrderRepository
import dev.claudiocodigo.nexo.domain.serviceorder.DateDivergenceCheck
import dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderRenderer
import dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderValidation
import dev.claudiocodigo.nexo.domain.serviceorder.StructuredServiceOrder
import dev.claudiocodigo.nexo.domain.time.ClockProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

sealed interface PreviewUiState {
    data object Loading : PreviewUiState
    data class Ready(
        val order: StructuredServiceOrder,
        val action: OutboxAction,
        val renderedDescription: String,
        val renderedIcs: String,
        val dateDivergence: DateDivergenceCheck,
        val targetCalendarName: String
    ) : PreviewUiState
    data object Confirmed : PreviewUiState
    data class Error(val message: String) : PreviewUiState
}

@HiltViewModel
class PublicationPreviewViewModel @Inject constructor(
    private val serviceOrderRepository: ServiceOrderRepository,
    private val publicationRepository: PublicationRepository,
    private val scheduler: PublicationScheduler,
    private val clock: ClockProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow<PreviewUiState>(PreviewUiState.Loading)
    val uiState: StateFlow<PreviewUiState> = _uiState.asStateFlow()

    fun loadPreview(orderId: UUID) {
        viewModelScope.launch {
            val order = serviceOrderRepository.getStructuredOrderById(orderId)
            if (order == null) {
                _uiState.value = PreviewUiState.Error("Ordem de serviço não encontrada")
                return@launch
            }

            val now = clock.nowMillis()
            val action = when {
                order.status == ServiceOrderStatus.CONCLUIDA -> OutboxAction.FINALIZE
                order.occurrenceKey != null -> OutboxAction.UPDATE
                else -> OutboxAction.CREATE
            }

            val renderedDescription = if (action == OutboxAction.FINALIZE) {
                ServiceOrderRenderer.renderCompletion(order, now)
            } else {
                ServiceOrderRenderer.renderUpdate(order, now)
            }

            val baseIcs = order.baseSnapshot?.rawIcs
            val targetUid = order.occurrenceKey?.eventHref?.substringAfterLast('/')?.removeSuffix(".ics") ?: order.id.toString()

            val renderedIcs = if (baseIcs != null) {
                IcsDocumentEditor.updateVEvent(
                    rawIcs = baseIcs,
                    targetUid = targetUid,
                    targetRecurrenceId = order.occurrenceKey?.recurrenceId,
                    newDescription = renderedDescription,
                    nowMillis = now,
                    incrementSequence = true
                )
            } else {
                val start = order.scheduledStart ?: now
                val end = order.scheduledEnd ?: (start + 3600_000L)
                IcsDocumentEditor.createProvisionalIcs(
                    uid = targetUid,
                    summary = "${order.externalId?.let { "OS $it - " } ?: ""}${order.title}",
                    description = renderedDescription,
                    startMillis = start,
                    endMillis = end,
                    nowMillis = now
                )
            }

            val divergence = ServiceOrderValidation.checkDateDivergence(now, order.scheduledStart)

            _uiState.value = PreviewUiState.Ready(
                order = order,
                action = action,
                renderedDescription = renderedDescription,
                renderedIcs = renderedIcs,
                dateDivergence = divergence,
                targetCalendarName = "Agenda de Trabalho"
            )
        }
    }

    fun confirmPublication() {
        val ready = _uiState.value as? PreviewUiState.Ready ?: return

        viewModelScope.launch {
            val snapshot = ConfirmedPreviewSnapshot(
                orderId = ready.order.id,
                action = ready.action,
                formattedDescription = ready.renderedDescription,
                baseEtag = ready.order.baseSnapshot?.etag,
                rawIcsPayload = ready.renderedIcs,
                targetHref = ready.order.occurrenceKey?.eventHref.orEmpty(),
                uid = ready.order.occurrenceKey?.eventHref?.substringAfterLast('/')?.removeSuffix(".ics") ?: ready.order.id.toString()
            )

            publicationRepository.confirmPreview(snapshot)
            scheduler.scheduleDrain()
            _uiState.value = PreviewUiState.Confirmed
        }
    }
}
