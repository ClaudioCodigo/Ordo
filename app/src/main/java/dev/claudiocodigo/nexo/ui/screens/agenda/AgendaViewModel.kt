package dev.claudiocodigo.nexo.ui.screens.agenda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.claudiocodigo.nexo.domain.model.ServiceOrder
import dev.claudiocodigo.nexo.domain.caldav.AccountIdentity
import dev.claudiocodigo.nexo.domain.caldav.CalendarInfo
import dev.claudiocodigo.nexo.domain.caldav.CalendarSyncState
import dev.claudiocodigo.nexo.domain.caldav.EventColor
import dev.claudiocodigo.nexo.domain.caldav.RemoteEvent
import dev.claudiocodigo.nexo.domain.repository.CalendarRepository
import dev.claudiocodigo.nexo.domain.repository.ServiceOrderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

@HiltViewModel
class AgendaViewModel @Inject constructor(
    private val repository: ServiceOrderRepository,
    private val calendarRepository: CalendarRepository
) : ViewModel() {

    /** Compatibility constructor for non-Hilt unit tests that only exercise local OS. */
    constructor(repository: ServiceOrderRepository) : this(repository, EmptyCalendarRepository)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _deleteError = MutableStateFlow<String?>(null)
    val deleteError: StateFlow<String?> = _deleteError

    val uiState: StateFlow<AgendaUiState> = combine(
        repository.getServiceOrders(),
        calendarRepository.observeEvents(),
        _searchQuery
    ) { orders, remoteEvents, query ->
            val filtered = if (query.isBlank()) {
                orders
            } else {
                orders.filter {
                    it.title.contains(query, ignoreCase = true) ||
                    it.description.contains(query, ignoreCase = true) ||
                    it.clientName.contains(query, ignoreCase = true) ||
                    it.unitName.contains(query, ignoreCase = true) ||
                    (it.externalId?.contains(query, ignoreCase = true) ?: false)
                }
            }

            val filteredRemote = if (query.isBlank()) remoteEvents else remoteEvents.filter {
                it.summary.orEmpty().contains(query, ignoreCase = true) ||
                    it.description.orEmpty().contains(query, ignoreCase = true) ||
                    it.location.orEmpty().contains(query, ignoreCase = true)
            }

            val grouped = linkedMapOf<String, MutableList<ServiceOrder>>()
            val groupedRemote = linkedMapOf<String, MutableList<RemoteEvent>>()
            val dateKeys = (filtered.mapNotNull { it.scheduledDate } + filteredRemote.mapNotNull { it.start })
                .groupBy(::dateLabel)
                .mapValues { (_, values) -> values.minOrNull() ?: Long.MAX_VALUE }
                .entries.sortedByDescending { it.value }
            dateKeys.forEach { (label, _) ->
                grouped[label] = mutableListOf()
                groupedRemote[label] = mutableListOf()
            }
            filtered.sortedWith(compareBy<ServiceOrder> { it.scheduledDate == null }.thenByDescending { it.scheduledDate ?: Long.MIN_VALUE }).forEach { order ->
                val label = order.scheduledDate?.let(::dateLabel) ?: "Sem data definida"
                grouped.getOrPut(label) { mutableListOf() }.add(order)
            }
            filteredRemote.sortedWith(compareBy<RemoteEvent> { it.start == null }.thenByDescending { it.start ?: Long.MIN_VALUE }).forEach { event ->
                val label = event.start?.let(::dateLabel) ?: "Sem data definida"
                groupedRemote.getOrPut(label) { mutableListOf() }.add(event)
            }

            AgendaUiState.Success(grouped, groupedRemote)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AgendaUiState.Loading
        )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun deleteLocalDraft(id: UUID) {
        viewModelScope.launch {
            runCatching { repository.deleteServiceOrder(id) }
                .onSuccess { _deleteError.value = null }
                .onFailure {
                    _deleteError.value = it.message ?: "Não foi possível excluir o rascunho local."
                }
        }
    }

    fun clearDeleteError() {
        _deleteError.value = null
    }
}

private fun dateLabel(millis: Long): String =
    SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale.forLanguageTag("pt-BR")).format(Date(millis))

sealed interface AgendaUiState {
    data object Loading : AgendaUiState
    data class Success(
        val groupedOrders: Map<String, List<ServiceOrder>>,
        val groupedRemoteEvents: Map<String, List<RemoteEvent>> = emptyMap()
    ) : AgendaUiState
}

private object EmptyCalendarRepository : CalendarRepository {
    override fun observeEvents() = flowOf(emptyList<RemoteEvent>())
    override fun observeEventsForDay(dayStartMillis: Long, dayEndMillis: Long) = flowOf(emptyList<RemoteEvent>())
    override fun searchEvents(query: String) = flowOf(emptyList<RemoteEvent>())
    override fun observeOverdue(nowMillis: Long) = flowOf(emptyList<RemoteEvent>())
    override suspend fun getEvent(accountId: String, calendarHref: String, href: String): RemoteEvent? = null
    override fun observeAccount() = flowOf<AccountIdentity?>(null)
    override fun observeSelectedCalendar() = flowOf<CalendarInfo?>(null)
    override fun observeSyncState() = flowOf<CalendarSyncState?>(null)
    override fun classifyColor(raw: String?): EventColor = EventColor.NAO_CLASSIFICADO
}
