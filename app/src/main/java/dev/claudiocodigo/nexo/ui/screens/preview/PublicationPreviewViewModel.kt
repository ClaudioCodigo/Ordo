package dev.claudiocodigo.nexo.ui.screens.preview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.claudiocodigo.nexo.data.ical.IcsDocumentEditor
import dev.claudiocodigo.nexo.data.ical.IcsParser
import dev.claudiocodigo.nexo.data.worker.PublicationScheduler
import dev.claudiocodigo.nexo.domain.model.ServiceOrderStatus
import dev.claudiocodigo.nexo.domain.publication.ConfirmedPreviewSnapshot
import dev.claudiocodigo.nexo.domain.publication.OutboxAction
import dev.claudiocodigo.nexo.domain.publication.PublicationRepository
import dev.claudiocodigo.nexo.domain.repository.ServiceOrderRepository
import dev.claudiocodigo.nexo.domain.repository.CalendarSetupRepository
import dev.claudiocodigo.nexo.domain.serviceorder.DateDivergenceCheck
import dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderRenderer
import dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderValidation
import dev.claudiocodigo.nexo.domain.serviceorder.StructuredServiceOrder
import dev.claudiocodigo.nexo.domain.time.ClockProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
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
        val targetUid: String,
        val dateDivergence: DateDivergenceCheck,
        val targetCalendarName: String,
        val isConfirming: Boolean = false,
        val confirmationError: String? = null
    ) : PreviewUiState
    data object Confirmed : PreviewUiState
    data class Error(val message: String) : PreviewUiState
}

@HiltViewModel
class PublicationPreviewViewModel @Inject constructor(
    private val serviceOrderRepository: ServiceOrderRepository,
    private val publicationRepository: PublicationRepository,
    private val calendarSetupRepository: CalendarSetupRepository,
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
            val targetUid = if (baseIcs != null) {
                val parsed = runCatching { IcsParser.parse(baseIcs) }.getOrElse {
                    _uiState.value = PreviewUiState.Error(
                        "Não foi possível interpretar o evento remoto. Nada foi colocado na fila."
                    )
                    return@launch
                }
                val targetRecurrenceId = order.occurrenceKey?.recurrenceId
                val targetEvent = if (targetRecurrenceId == null) {
                    parsed.events.firstOrNull { it.recurrenceId == null } ?: parsed.events.firstOrNull()
                } else {
                    parsed.events.firstOrNull { it.recurrenceId?.raw == targetRecurrenceId }
                }
                targetEvent?.uid?.takeIf { it.isNotBlank() }
                    ?: run {
                        _uiState.value = PreviewUiState.Error(
                            "O evento remoto não possui um UID identificável. Nada foi colocado na fila."
                        )
                        return@launch
                    }
            } else {
                order.id.toString()
            }

            val renderedIcs = runCatching {
                if (baseIcs != null) {
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
            }.getOrElse {
                _uiState.value = PreviewUiState.Error(
                    "Não foi possível aplicar a atualização ao evento remoto. Nada foi colocado na fila."
                )
                return@launch
            }

            if (baseIcs != null && renderedIcs == baseIcs) {
                _uiState.value = PreviewUiState.Error(
                    "A atualização não alterou o evento remoto. Nada foi colocado na fila."
                )
                return@launch
            }

            val divergence = ServiceOrderValidation.checkDateDivergence(now, order.scheduledStart)
            val selectedCalendar = calendarSetupRepository.observeSelectedCalendar().first()
            val targetCalendarName = when {
                order.occurrenceKey == null -> selectedCalendar?.displayName
                selectedCalendar?.href == order.occurrenceKey.calendarHref -> selectedCalendar?.displayName
                else -> "Agenda vinculada ao evento"
            }?.takeIf { it.isNotBlank() } ?: "Agenda de trabalho selecionada"

            _uiState.value = PreviewUiState.Ready(
                order = order,
                action = action,
                renderedDescription = renderedDescription,
                renderedIcs = renderedIcs,
                targetUid = targetUid,
                dateDivergence = divergence,
                targetCalendarName = targetCalendarName
            )
        }
    }

    fun confirmPublication() {
        val ready = _uiState.value as? PreviewUiState.Ready ?: return
        if (ready.isConfirming) return
        _uiState.value = ready.copy(isConfirming = true, confirmationError = null)

        viewModelScope.launch {
            try {
                val snapshot = ConfirmedPreviewSnapshot(
                    orderId = ready.order.id,
                    action = ready.action,
                    formattedDescription = ready.renderedDescription,
                    baseEtag = ready.order.baseSnapshot?.etag,
                    rawIcsPayload = ready.renderedIcs,
                    targetHref = ready.order.occurrenceKey?.eventHref.orEmpty(),
                    uid = ready.targetUid
                )

                publicationRepository.confirmPreview(snapshot)
                scheduler.scheduleDrain()
                _uiState.value = PreviewUiState.Confirmed
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.value = ready.copy(
                    isConfirming = false,
                    confirmationError = "Não foi possível colocar a publicação na fila. Tente novamente."
                )
            }
        }
    }
}
