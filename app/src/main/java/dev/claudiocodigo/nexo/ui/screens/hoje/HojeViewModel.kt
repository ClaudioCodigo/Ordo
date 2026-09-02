package dev.claudiocodigo.nexo.ui.screens.hoje

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.claudiocodigo.nexo.domain.caldav.CalendarSyncCoordinator
import dev.claudiocodigo.nexo.domain.caldav.CalendarSyncState
import dev.claudiocodigo.nexo.domain.caldav.EventColor
import dev.claudiocodigo.nexo.domain.caldav.RemoteEvent
import dev.claudiocodigo.nexo.domain.model.ServiceOrder
import dev.claudiocodigo.nexo.domain.model.ServiceOrderStatus
import dev.claudiocodigo.nexo.domain.publication.OutboxOperation
import dev.claudiocodigo.nexo.domain.publication.PublicationRepository
import dev.claudiocodigo.nexo.domain.repository.CalendarRepository
import dev.claudiocodigo.nexo.domain.repository.ServiceOrderRepository
import dev.claudiocodigo.nexo.domain.serviceorder.OperationalOrderCard
import dev.claudiocodigo.nexo.domain.serviceorder.OperationalOrderProjection
import dev.claudiocodigo.nexo.domain.serviceorder.OperationalStatus
import dev.claudiocodigo.nexo.domain.serviceorder.PublicationState
import dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderUpdate
import dev.claudiocodigo.nexo.domain.serviceorder.StructuredServiceOrder
import dev.claudiocodigo.nexo.domain.time.ClockProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HojeViewModel @Inject constructor(
    private val repository: ServiceOrderRepository,
    private val calendarRepository: CalendarRepository,
    private val clock: ClockProvider,
    private val publicationRepository: PublicationRepository? = null,
    private val syncCoordinator: CalendarSyncCoordinator? = null
) : ViewModel() {

    init {
        syncNow()
    }

    val isSyncing: StateFlow<Boolean> = syncCoordinator?.isSyncing ?: MutableStateFlow(false)

    fun syncNow() {
        viewModelScope.launch {
            syncCoordinator?.syncNow()
        }
    }

    private data class BaseHojeData(
        val legacyOrders: List<ServiceOrder>,
        val structuredOrders: List<StructuredServiceOrder>,
        val remoteEvents: List<RemoteEvent>,
        val syncState: CalendarSyncState?
    )

    private val baseDataFlow = combine(
        repository.getServiceOrders(),
        runCatching { repository.observeStructuredOrders() }.getOrNull() ?: flowOf(emptyList()),
        calendarRepository.observeEvents(),
        calendarRepository.observeSyncState()
    ) { legacy, structured, remote, sync ->
        BaseHojeData(legacy, structured, remote, sync)
    }

    val uiState: StateFlow<HojeUiState> = combine(
        baseDataFlow,
        publicationRepository?.observeOperations() ?: flowOf(emptyList<OutboxOperation>()),
        isSyncing
    ) { base, outboxOps, syncing ->
        val now = clock.nowMillis()
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val historicalCutoff = now - Duration.ofDays(30).toMillis()

        // 1. Unified Operational Projection
        val allCards = OperationalOrderProjection.project(
            remoteEvents = base.remoteEvents,
            structuredOrders = base.structuredOrders,
            outboxOperations = outboxOps
        )

        // 2. Partition cards
        val attentionCards = allCards.filter {
            it.status == OperationalStatus.REQUER_ATENCAO &&
                (it.startMillis ?: it.endMillis ?: Long.MIN_VALUE) > historicalCutoff
        }

        val todayCards = allCards.filter { card ->
            val start = card.startMillis
            val end = card.endMillis ?: start?.plus(1) ?: start
            start != null && start < dayEnd && (end == null || end > dayStart) && card !in attentionCards
        }

        val inProgressCards = allCards.filter { it.status == OperationalStatus.EM_ANDAMENTO && it !in attentionCards }

        // Legacy compatibility collections
        val newestFirst = compareByDescending<RemoteEvent> { it.start ?: Long.MIN_VALUE }
            .thenByDescending { it.end ?: Long.MIN_VALUE }
            .thenBy { it.href }

        val todayEvents = base.remoteEvents.filter { event ->
            val start = event.start
            val end = event.end ?: start?.plus(1) ?: start
            start != null && start < dayEnd && (end == null || end > dayStart)
        }.sortedWith(newestFirst)

        val attentionEvents = base.remoteEvents.filter {
            it.color == EventColor.REQUER_ATENCAO &&
                (it.start ?: it.end ?: Long.MIN_VALUE) > historicalCutoff
        }.sortedWith(newestFirst)

        val overdueEvents = base.remoteEvents.filter {
            it.end != null && it.end < dayStart &&
                (it.start ?: it.end ?: Long.MIN_VALUE) > historicalCutoff &&
                it.color != EventColor.VALIDADO &&
                it.color != EventColor.REQUER_ATENCAO
        }.sortedWith(newestFirst)

        val separated = (attentionEvents + overdueEvents).toSet()
        val todayOpenCards = todayCards.filter { !OperationalOrderProjection.isCardConcluded(it) }
        val todayConcludedCards = todayCards.filter { OperationalOrderProjection.isCardConcluded(it) }
        val provisionalDraftsCount = base.structuredOrders.count {
            it.occurrenceKey == null && it.publicationState == PublicationState.LOCAL_DRAFT
        }

        HojeUiState.Success(
            emAndamento = base.legacyOrders.filter { it.status == ServiceOrderStatus.EM_ANDAMENTO },
            requerAtencao = base.legacyOrders.filter {
                it.status == ServiceOrderStatus.PENDENTE && it.scheduledDate?.let { date -> date < clock.nowMillis() } == true
            },
            pendencias = base.legacyOrders.filter {
                it.status == ServiceOrderStatus.PENDENTE && (it.scheduledDate == null || it.scheduledDate >= clock.nowMillis())
            },
            remoteEvents = todayEvents.filterNot { it in separated },
            remoteEventsRequerAtencao = attentionEvents,
            remoteEventsAtrasados = overdueEvents.filterNot { it in attentionEvents },
            cards = allCards,
            emAndamentoCards = inProgressCards,
            requerAtencaoCards = attentionCards,
            hojeCards = todayCards,
            hojeOpenCards = todayOpenCards,
            hojeConcludedCards = todayConcludedCards,
            provisionalDraftsCount = provisionalDraftsCount,
            syncState = base.syncState,
            isSyncing = syncing
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HojeUiState.Loading
    )

    fun reopenOrder(orderId: UUID, onCompleted: () -> Unit = {}) {
        viewModelScope.launch {
            val order = repository.getStructuredOrderById(orderId) ?: return@launch
            val nextSeq = (order.updates.maxOfOrNull { it.sequenceOrder } ?: 0) + 1
            val updated = order.copy(
                status = ServiceOrderStatus.EM_ANDAMENTO,
                publicationState = PublicationState.LOCAL_DRAFT,
                updates = order.updates + ServiceOrderUpdate(
                    id = UUID.randomUUID(),
                    sequenceOrder = nextSeq,
                    text = "Reabertura de atendimento após retorno técnico com pendência.",
                    executionDate = clock.nowMillis()
                )
            )
            repository.saveStructuredOrder(updated)
            onCompleted()
        }
    }
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
        val cards: List<OperationalOrderCard> = emptyList(),
        val emAndamentoCards: List<OperationalOrderCard> = emptyList(),
        val requerAtencaoCards: List<OperationalOrderCard> = emptyList(),
        val hojeCards: List<OperationalOrderCard> = emptyList(),
        val hojeOpenCards: List<OperationalOrderCard> = emptyList(),
        val hojeConcludedCards: List<OperationalOrderCard> = emptyList(),
        val provisionalDraftsCount: Int = 0,
        val syncState: CalendarSyncState?,
        val isSyncing: Boolean = false
    ) : HojeUiState
}
