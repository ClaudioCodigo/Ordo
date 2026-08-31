package dev.claudiocodigo.nexo.ui.screens.agenda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.claudiocodigo.nexo.domain.caldav.AccountIdentity
import dev.claudiocodigo.nexo.domain.caldav.CalendarInfo
import dev.claudiocodigo.nexo.domain.caldav.CalendarSyncCoordinator
import dev.claudiocodigo.nexo.domain.caldav.CalendarSyncState
import dev.claudiocodigo.nexo.domain.caldav.EventColor
import dev.claudiocodigo.nexo.domain.caldav.RemoteEvent
import dev.claudiocodigo.nexo.domain.model.ServiceOrder
import dev.claudiocodigo.nexo.domain.publication.OutboxOperation
import dev.claudiocodigo.nexo.domain.publication.PublicationRepository
import dev.claudiocodigo.nexo.domain.repository.CalendarRepository
import dev.claudiocodigo.nexo.domain.repository.ServiceOrderRepository
import dev.claudiocodigo.nexo.domain.serviceorder.OperationalOrderCard
import dev.claudiocodigo.nexo.domain.serviceorder.OperationalOrderProjection
import dev.claudiocodigo.nexo.domain.serviceorder.StructuredServiceOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AgendaViewModel @Inject constructor(
    private val repository: ServiceOrderRepository,
    private val calendarRepository: CalendarRepository,
    private val publicationRepository: PublicationRepository? = null,
    private val syncCoordinator: CalendarSyncCoordinator? = null
) : ViewModel() {

    /** Compatibility constructor for non-Hilt unit tests that only exercise local OS. */
    constructor(repository: ServiceOrderRepository) : this(repository, EmptyCalendarRepository, null, null)

    constructor(
        repository: ServiceOrderRepository,
        calendarRepository: CalendarRepository
    ) : this(repository, calendarRepository, null, null)

    init {
        syncNow()
    }

    val isSyncing: StateFlow<Boolean> = syncCoordinator?.isSyncing ?: MutableStateFlow(false)

    fun syncNow() {
        viewModelScope.launch {
            syncCoordinator?.syncNow()
        }
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _deleteError = MutableStateFlow<String?>(null)
    val deleteError: StateFlow<String?> = _deleteError

    private data class BaseAgendaData(
        val legacyOrders: List<ServiceOrder>,
        val structuredOrders: List<StructuredServiceOrder>,
        val remoteEvents: List<RemoteEvent>,
        val outboxOps: List<OutboxOperation>
    )

    private val baseDataFlow = combine(
        repository.getServiceOrders(),
        runCatching { repository.observeStructuredOrders() }.getOrNull() ?: flowOf(emptyList()),
        calendarRepository.observeEvents(),
        publicationRepository?.observeOperations() ?: flowOf(emptyList<OutboxOperation>())
    ) { legacy, structured, remote, ops ->
        BaseAgendaData(legacy, structured, remote, ops)
    }

    val uiState: StateFlow<AgendaUiState> = combine(
        baseDataFlow,
        _searchQuery,
        isSyncing
    ) { base, query, syncing ->
        val allCards = OperationalOrderProjection.project(
            remoteEvents = base.remoteEvents,
            structuredOrders = base.structuredOrders,
            outboxOperations = base.outboxOps
        )

        val filteredCards = if (query.isBlank()) {
            allCards
        } else {
            allCards.filter { card ->
                card.title.contains(query, ignoreCase = true) ||
                    card.clientName.contains(query, ignoreCase = true) ||
                    (card.unitName?.contains(query, ignoreCase = true) ?: false) ||
                    (card.externalId?.contains(query, ignoreCase = true) ?: false)
            }
        }

        val groupedCards = linkedMapOf<String, MutableList<OperationalOrderCard>>()
        val dateKeysCards = filteredCards.mapNotNull { it.startMillis }
            .groupBy(::dateLabel)
            .mapValues { (_, values) -> values.minOrNull() ?: Long.MAX_VALUE }
            .entries.sortedByDescending { it.value }

        dateKeysCards.forEach { (label, _) ->
            groupedCards[label] = mutableListOf()
        }

        filteredCards.forEach { card ->
            val label = card.startMillis?.let(::dateLabel) ?: "Sem data definida"
            groupedCards.getOrPut(label) { mutableListOf() }.add(card)
        }

        // Legacy compatibility grouping
        val filtered = if (query.isBlank()) {
            base.legacyOrders
        } else {
            base.legacyOrders.filter { order ->
                order.title.contains(query, ignoreCase = true) ||
                    order.clientName.contains(query, ignoreCase = true) ||
                    order.unitName.contains(query, ignoreCase = true) ||
                    (order.externalId?.contains(query, ignoreCase = true) == true)
            }
        }

        val filteredRemote = if (query.isBlank()) {
            base.remoteEvents
        } else {
            base.remoteEvents.filter { event ->
                (event.summary?.contains(query, ignoreCase = true) == true) ||
                    (event.description?.contains(query, ignoreCase = true) == true) ||
                    (event.location?.contains(query, ignoreCase = true) == true)
            }
        }

        val allEpochs = (filtered.mapNotNull { it.scheduledDate } + filteredRemote.mapNotNull { it.start }).distinct()
        val dateKeys = allEpochs
            .groupBy(::dateLabel)
            .mapValues { (_, values) -> values.minOrNull() ?: Long.MAX_VALUE }
            .entries.sortedByDescending { it.value }

        val grouped = linkedMapOf<String, MutableList<ServiceOrder>>()
        val groupedRemote = linkedMapOf<String, MutableList<RemoteEvent>>()

        dateKeys.forEach { (label, _) ->
            grouped[label] = mutableListOf()
            groupedRemote[label] = mutableListOf()
        }

        filtered.forEach { order ->
            val label = order.scheduledDate?.let(::dateLabel) ?: "Sem data definida"
            grouped.getOrPut(label) { mutableListOf() }.add(order)
        }

        grouped.values.forEach { list ->
            list.sortByDescending { it.scheduledDate ?: Long.MIN_VALUE }
        }

        filteredRemote.forEach { event ->
            val label = event.start?.let(::dateLabel) ?: "Sem data definida"
            groupedRemote.getOrPut(label) { mutableListOf() }.add(event)
        }

        groupedRemote.values.forEach { list ->
            list.sortByDescending { it.start ?: Long.MIN_VALUE }
        }

        AgendaUiState.Success(
            groupedOrders = grouped,
            groupedRemoteEvents = groupedRemote,
            groupedCards = groupedCards,
            cards = filteredCards,
            isSyncing = syncing
        )
    }.stateIn(
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
        val groupedRemoteEvents: Map<String, List<RemoteEvent>> = emptyMap(),
        val groupedCards: Map<String, List<OperationalOrderCard>> = emptyMap(),
        val cards: List<OperationalOrderCard> = emptyList(),
        val isSyncing: Boolean = false
    ) : AgendaUiState
}

internal object EmptyCalendarRepository : CalendarRepository {
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
