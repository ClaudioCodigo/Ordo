package dev.claudiocodigo.nexo.ui.screens.remoto

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.claudiocodigo.nexo.domain.caldav.RemoteEvent
import dev.claudiocodigo.nexo.domain.repository.CalendarRepository
import dev.claudiocodigo.nexo.domain.repository.ServiceOrderRepository
import dev.claudiocodigo.nexo.domain.serviceorder.RemoteOccurrenceKey
import dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderDiff
import dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderExtractor
import dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderPreset
import dev.claudiocodigo.nexo.domain.serviceorder.RemoteBaseSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class RemoteEventDetailViewModel @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val serviceOrderRepository: ServiceOrderRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<RemoteEventDetailUiState>(RemoteEventDetailUiState.Loading)
    val uiState: StateFlow<RemoteEventDetailUiState> = _uiState.asStateFlow()

    fun load(accountId: String, calendarHref: String, href: String) {
        viewModelScope.launch {
            val event = calendarRepository.getEvent(accountId, calendarHref, href)
            if (event == null) {
                _uiState.value = RemoteEventDetailUiState.Error("Evento não encontrado no cache local")
                return@launch
            }
            val key = RemoteOccurrenceKey(event.accountId, event.calendarHref, event.href, null)
            val linked = serviceOrderRepository.getLinkedOrder(key)
            _uiState.value = RemoteEventDetailUiState.Success(event, linked?.id)
        }
    }

    fun startAttendance(onStarted: (UUID, Boolean) -> Unit) {
        val success = _uiState.value as? RemoteEventDetailUiState.Success ?: return
        val displayedEvent = success.event

        viewModelScope.launch {
            val event = calendarRepository.getEvent(
                displayedEvent.accountId,
                displayedEvent.calendarHref,
                displayedEvent.href
            ) ?: displayedEvent
            val summaryInfo = ServiceOrderExtractor.extractSummary(event.summary)
            val descInfo = ServiceOrderExtractor.extractDescription(event.description)
            val remoteExternalId = summaryInfo.externalId ?: descInfo.externalId

            val key = RemoteOccurrenceKey(
                accountId = event.accountId,
                calendarHref = event.calendarHref,
                eventHref = event.href,
                recurrenceId = null
            )

            val existing = serviceOrderRepository.getLinkedOrder(key)
            if (existing != null) {
                val changeAnalysis = ServiceOrderDiff.analyzeRemoteChange(
                    localOrder = existing,
                    remoteExternalId = remoteExternalId,
                    remoteDemand = descInfo.originalDemand,
                    remoteTitle = summaryInfo.title,
                    remoteCause = descInfo.closureCause,
                    remoteSolution = descInfo.closureSolution,
                    remotePending = descInfo.closurePending,
                    remoteRawSummary = event.summary,
                    remoteRawDescription = event.description
                )
                val legacyMissingOfficialId = existing.externalId == null && remoteExternalId != null
                val remoteVersionChanged = existing.baseSnapshot?.etag != event.etag

                if (remoteVersionChanged && changeAnalysis.hasUnmappedRemoteTextChange) {
                    _uiState.value = RemoteEventDetailUiState.Error(
                        "O calendário mudou em um texto que não pode ser comparado com segurança. " +
                            "Atualize os eventos e tente novamente sem descartar o rascunho local."
                    )
                    return@launch
                }

                if ((remoteVersionChanged || legacyMissingOfficialId) && changeAnalysis.differences.isNotEmpty()) {
                    onStarted(existing.id, true)
                    return@launch
                }

                if (remoteVersionChanged) {
                    serviceOrderRepository.saveStructuredOrder(
                        existing.copy(
                            baseSnapshot = (existing.baseSnapshot ?: RemoteBaseSnapshot(
                                etag = event.etag,
                                rawIcs = event.rawIcs,
                                rawSummary = event.summary,
                                rawDescription = event.description
                            )).copy(
                                etag = event.etag,
                                rawIcs = event.rawIcs,
                                rawSummary = event.summary,
                                rawDescription = event.description,
                                capturedAt = System.currentTimeMillis()
                            )
                        )
                    )
                }
                onStarted(existing.id, false)
                return@launch
            }

            val created = serviceOrderRepository.createOrGetAttendance(
                key = key,
                initialPreset = descInfo.preset,
                title = summaryInfo.title,
                clientName = summaryInfo.title.substringBefore(" - "),
                unitName = event.location.orEmpty().ifBlank { "Unidade" },
                rawSummary = event.summary,
                rawDescription = event.description,
                rawIcs = event.rawIcs,
                etag = event.etag,
                startMillis = event.start,
                endMillis = event.end
            )
            val order = created.copy(
                externalId = remoteExternalId,
                technician = summaryInfo.technician,
                category = summaryInfo.category,
                preset = descInfo.preset,
                originalDemand = descInfo.originalDemand,
                updates = descInfo.updates,
                closureCause = descInfo.closureCause,
                closureSolution = descInfo.closureSolution,
                closurePending = descInfo.closurePending
            )
            serviceOrderRepository.saveStructuredOrder(order)
            onStarted(order.id, false)
        }
    }
}

sealed interface RemoteEventDetailUiState {
    data object Loading : RemoteEventDetailUiState
    data class Success(val event: RemoteEvent, val linkedOrderId: UUID? = null) : RemoteEventDetailUiState
    data class Error(val message: String) : RemoteEventDetailUiState
}
