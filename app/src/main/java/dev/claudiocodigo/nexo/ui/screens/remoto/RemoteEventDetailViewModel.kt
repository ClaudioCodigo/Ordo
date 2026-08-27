package dev.claudiocodigo.nexo.ui.screens.remoto

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.claudiocodigo.nexo.domain.caldav.RemoteEvent
import dev.claudiocodigo.nexo.domain.repository.CalendarRepository
import dev.claudiocodigo.nexo.domain.repository.ServiceOrderRepository
import dev.claudiocodigo.nexo.domain.serviceorder.RemoteOccurrenceKey
import dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderExtractor
import dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderPreset
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
        if (_uiState.value is RemoteEventDetailUiState.Success) return
        viewModelScope.launch {
            _uiState.value = calendarRepository.getEvent(accountId, calendarHref, href)
                ?.let { RemoteEventDetailUiState.Success(it) }
                ?: RemoteEventDetailUiState.Error("Evento não encontrado no cache local")
        }
    }

    fun startAttendance(onStarted: (UUID) -> Unit) {
        val success = _uiState.value as? RemoteEventDetailUiState.Success ?: return
        val event = success.event

        viewModelScope.launch {
            val summaryInfo = ServiceOrderExtractor.extractSummary(event.summary)
            val descInfo = ServiceOrderExtractor.extractDescription(event.description)

            val key = RemoteOccurrenceKey(
                accountId = event.accountId,
                calendarHref = event.calendarHref,
                eventHref = event.href,
                recurrenceId = null
            )

            val order = serviceOrderRepository.createOrGetAttendance(
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

            onStarted(order.id)
        }
    }
}

sealed interface RemoteEventDetailUiState {
    data object Loading : RemoteEventDetailUiState
    data class Success(val event: RemoteEvent) : RemoteEventDetailUiState
    data class Error(val message: String) : RemoteEventDetailUiState
}
