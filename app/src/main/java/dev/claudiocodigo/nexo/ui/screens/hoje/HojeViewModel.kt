package dev.claudiocodigo.nexo.ui.screens.hoje

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.claudiocodigo.nexo.domain.caldav.CalendarSyncState
import dev.claudiocodigo.nexo.domain.caldav.RemoteEvent
import dev.claudiocodigo.nexo.domain.model.ServiceOrder
import dev.claudiocodigo.nexo.domain.model.ServiceOrderStatus
import dev.claudiocodigo.nexo.domain.repository.CalendarRepository
import dev.claudiocodigo.nexo.domain.repository.ServiceOrderRepository
import dev.claudiocodigo.nexo.domain.time.ClockProvider
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.Duration
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class HojeViewModel @Inject constructor(
    private val repository: ServiceOrderRepository,
    private val calendarRepository: CalendarRepository,
    private val clock: ClockProvider
) : ViewModel() {

    val uiState: StateFlow<HojeUiState> = combine(
        repository.getServiceOrders(),
        calendarRepository.observeEvents(),
        calendarRepository.observeSyncState()
    ) { orders, remoteEvents, syncState ->
        val now = clock.nowMillis()
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val historicalCutoff = now - Duration.ofDays(30).toMillis()
        val newestFirst = compareByDescending<RemoteEvent> { it.start ?: Long.MIN_VALUE }
            .thenByDescending { it.end ?: Long.MIN_VALUE }
            .thenBy { it.href }
        val todayEvents = remoteEvents.filter { event ->
            val start = event.start
            val end = event.end ?: start?.plus(1) ?: start
            start != null && start < dayEnd && (end == null || end > dayStart)
        }.sortedWith(newestFirst)
        val attentionEvents = remoteEvents.filter {
            it.color == dev.claudiocodigo.nexo.domain.caldav.EventColor.REQUER_ATENCAO &&
                (it.start ?: it.end ?: Long.MIN_VALUE) > historicalCutoff
        }.sortedWith(newestFirst)
        val overdueEvents = remoteEvents.filter {
            it.end != null && it.end < dayStart &&
                (it.start ?: it.end ?: Long.MIN_VALUE) > historicalCutoff &&
                it.color != dev.claudiocodigo.nexo.domain.caldav.EventColor.VALIDADO &&
                it.color != dev.claudiocodigo.nexo.domain.caldav.EventColor.REQUER_ATENCAO
        }.sortedWith(newestFirst)
        val separated = (attentionEvents + overdueEvents).toSet()
        HojeUiState.Success(
            emAndamento = orders.filter { it.status == ServiceOrderStatus.EM_ANDAMENTO },
            requerAtencao = orders.filter {
                it.status == ServiceOrderStatus.PENDENTE && it.scheduledDate?.let { date -> date < clock.nowMillis() } == true
            },
            pendencias = orders.filter {
                it.status == ServiceOrderStatus.PENDENTE && (it.scheduledDate == null || it.scheduledDate >= clock.nowMillis())
            },
            remoteEvents = todayEvents.filterNot { it in separated },
            remoteEventsRequerAtencao = attentionEvents,
            remoteEventsAtrasados = overdueEvents.filterNot { it in attentionEvents },
            syncState = syncState
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HojeUiState.Loading
    )
}

sealed interface HojeUiState {
    data object Loading : HojeUiState
    data class Success(
        val emAndamento: List<ServiceOrder>,
        val requerAtencao: List<ServiceOrder>,
        val pendencias: List<ServiceOrder>,
        val remoteEvents: List<RemoteEvent>,
        val remoteEventsRequerAtencao: List<RemoteEvent> = emptyList(),
        val remoteEventsAtrasados: List<RemoteEvent> = emptyList(),
        val syncState: CalendarSyncState?
    ) : HojeUiState
}
