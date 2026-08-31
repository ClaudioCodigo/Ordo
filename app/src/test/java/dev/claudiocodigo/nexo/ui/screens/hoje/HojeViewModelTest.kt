package dev.claudiocodigo.nexo.ui.screens.hoje

import dev.claudiocodigo.nexo.domain.model.ServiceOrder
import dev.claudiocodigo.nexo.domain.model.ServiceOrderStatus
import dev.claudiocodigo.nexo.domain.caldav.EventColor
import dev.claudiocodigo.nexo.domain.caldav.RemoteEvent
import dev.claudiocodigo.nexo.domain.repository.CalendarRepository
import dev.claudiocodigo.nexo.domain.repository.ServiceOrderRepository
import dev.claudiocodigo.nexo.domain.time.ClockProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import java.util.UUID
import java.time.Instant
import java.time.Duration
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class HojeViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Mock
    private lateinit var calendarRepository: CalendarRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        MockitoAnnotations.openMocks(this)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `classifies active overdue future and undated orders while excluding completed`() = runTest {
        val now = 1_000_000L
        val orders = listOf(
            order(ServiceOrderStatus.EM_ANDAMENTO),
            order(ServiceOrderStatus.PENDENTE, now - 1),
            order(ServiceOrderStatus.PENDENTE, now + 1),
            order(ServiceOrderStatus.PENDENTE, null),
            order(ServiceOrderStatus.CONCLUIDA, now - 1),
            order(ServiceOrderStatus.CANCELADA, now + 1)
        )
        `when`(calendarRepository.observeEvents()).thenReturn(flowOf(emptyList()))
        `when`(calendarRepository.observeSyncState()).thenReturn(flowOf(null))

        val viewModel = HojeViewModel(FlowRepository(orders), calendarRepository, FixedClock(now))

        val collection = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        val state = viewModel.uiState.value as HojeUiState.Success

        assertEquals(1, state.emAndamento.size)
        assertEquals(1, state.requerAtencao.size)
        assertEquals(2, state.pendencias.size)
        assertEquals(0, state.requerAtencao.count { it.scheduledDate == null })
        assertEquals(0, state.remoteEvents.size)
        collection.cancel()
    }

    @Test
    fun `shows only today's remote events and separates attention and overdue without duplicates`() = runTest {
        val now = Instant.parse("2025-01-01T12:00:00Z").toEpochMilli()
        val zone = ZoneId.systemDefault()
        val dayStart = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val normal = remote("normal", now + 60_000, now + 120_000, EventColor.NAO_CLASSIFICADO)
        val attention = remote("attention", now + 180_000, now + 240_000, EventColor.REQUER_ATENCAO)
        val redPast = remote("red-past", dayStart - 240_000, dayStart - 180_000, EventColor.REQUER_ATENCAO)
        val overdue = remote("overdue", dayStart - 120_000, dayStart - 60_000, EventColor.NAO_CLASSIFICADO)
        val greenPast = remote("green-past", dayStart - 360_000, dayStart - 300_000, EventColor.VALIDADO)
        val tomorrow = remote("tomorrow", dayEnd + 60_000, dayEnd + 120_000, EventColor.NAO_CLASSIFICADO)
        `when`(calendarRepository.observeEvents()).thenReturn(flowOf(listOf(normal, attention, redPast, overdue, greenPast, tomorrow)))
        `when`(calendarRepository.observeSyncState()).thenReturn(flowOf(null))

        val viewModel = HojeViewModel(FlowRepository(emptyList()), calendarRepository, FixedClock(now))
        val collection = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        val state = viewModel.uiState.value as HojeUiState.Success

        assertEquals(listOf("normal"), state.remoteEvents.map { it.uid })
        assertEquals(listOf("attention", "red-past"), state.remoteEventsRequerAtencao.map { it.uid })
        assertEquals(listOf("overdue"), state.remoteEventsAtrasados.map { it.uid })
        assertEquals(4, (state.remoteEvents + state.remoteEventsRequerAtencao + state.remoteEventsAtrasados).map { it.uid }.toSet().size)
        collection.cancel()
    }

    @Test
    fun `sorts every remote event section from newest to oldest`() = runTest {
        val now = Instant.parse("2025-01-01T12:00:00Z").toEpochMilli()
        val zone = ZoneId.systemDefault()
        val dayStart = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        val events = listOf(
            remote("today-old", dayStart + 60_000, dayStart + 120_000, EventColor.NAO_CLASSIFICADO),
            remote("attention-old", dayStart - 600_000, dayStart - 540_000, EventColor.REQUER_ATENCAO),
            remote("overdue-new", dayStart - 180_000, dayStart - 120_000, EventColor.NAO_CLASSIFICADO),
            remote("today-new", dayStart + 600_000, dayStart + 660_000, EventColor.NAO_CLASSIFICADO),
            remote("overdue-old", dayStart - 420_000, dayStart - 360_000, EventColor.NAO_CLASSIFICADO),
            remote("attention-new", dayStart - 240_000, dayStart - 180_000, EventColor.REQUER_ATENCAO)
        )
        `when`(calendarRepository.observeEvents()).thenReturn(flowOf(events))
        `when`(calendarRepository.observeSyncState()).thenReturn(flowOf(null))

        val viewModel = HojeViewModel(FlowRepository(emptyList()), calendarRepository, FixedClock(now))
        val collection = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        val state = viewModel.uiState.value as HojeUiState.Success

        assertEquals(listOf("today-new", "today-old"), state.remoteEvents.map { it.uid })
        assertEquals(listOf("attention-new", "attention-old"), state.remoteEventsRequerAtencao.map { it.uid })
        assertEquals(listOf("overdue-new", "overdue-old"), state.remoteEventsAtrasados.map { it.uid })
        collection.cancel()
    }

    @Test
    fun `ignores historical attention and overdue events that are 30 days old or older`() = runTest {
        val now = Instant.parse("2025-02-15T12:00:00Z").toEpochMilli()
        val insideWindow = now - Duration.ofDays(29).toMillis()
        val recentOverdue = now - Duration.ofDays(28).toMillis()
        val exactCutoff = now - Duration.ofDays(30).toMillis()
        val olderThanCutoff = now - Duration.ofDays(31).toMillis()
        val events = listOf(
            remote("attention-recent", insideWindow, insideWindow + 60_000, EventColor.REQUER_ATENCAO),
            remote("attention-cutoff", exactCutoff, exactCutoff + 60_000, EventColor.REQUER_ATENCAO),
            remote("overdue-recent", recentOverdue, recentOverdue + 60_000, EventColor.NAO_CLASSIFICADO),
            remote("overdue-old", olderThanCutoff, olderThanCutoff + 60_000, EventColor.NAO_CLASSIFICADO)
        )
        `when`(calendarRepository.observeEvents()).thenReturn(flowOf(events))
        `when`(calendarRepository.observeSyncState()).thenReturn(flowOf(null))

        val viewModel = HojeViewModel(FlowRepository(emptyList()), calendarRepository, FixedClock(now))
        val collection = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        val state = viewModel.uiState.value as HojeUiState.Success

        assertEquals(listOf("attention-recent"), state.remoteEventsRequerAtencao.map { it.uid })
        assertEquals(listOf("overdue-recent"), state.remoteEventsAtrasados.map { it.uid })
        collection.cancel()
    }

    @Test
    fun `syncNow triggers coordinator and reflects isSyncing state`() = runTest {
        val now = 1_000_000L
        val syncingFlow = MutableStateFlow(false)
        var syncTriggeredCount = 0
        val fakeCoordinator = object : dev.claudiocodigo.nexo.domain.caldav.CalendarSyncCoordinator {
            override val isSyncing: kotlinx.coroutines.flow.StateFlow<Boolean> = syncingFlow
            override suspend fun syncNow(): dev.claudiocodigo.nexo.domain.caldav.SyncOutcome {
                syncTriggeredCount++
                return dev.claudiocodigo.nexo.domain.caldav.SyncOutcome.Success(0, 0, 0, null)
            }
        }
        `when`(calendarRepository.observeEvents()).thenReturn(flowOf(emptyList()))
        `when`(calendarRepository.observeSyncState()).thenReturn(flowOf(null))

        val viewModel = HojeViewModel(
            repository = FlowRepository(emptyList()),
            calendarRepository = calendarRepository,
            clock = FixedClock(now),
            publicationRepository = null,
            syncCoordinator = fakeCoordinator
        )

        val collection = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(1, syncTriggeredCount) // triggered on init

        syncingFlow.value = true
        advanceUntilIdle()
        var state = viewModel.uiState.value as HojeUiState.Success
        assertEquals(true, state.isSyncing)

        viewModel.syncNow()
        advanceUntilIdle()
        assertEquals(2, syncTriggeredCount)

        syncingFlow.value = false
        advanceUntilIdle()
        state = viewModel.uiState.value as HojeUiState.Success
        assertEquals(false, state.isSyncing)

        collection.cancel()
    }

    private fun order(status: ServiceOrderStatus, date: Long? = null) = ServiceOrder(
        id = UUID.randomUUID(), title = status.name, clientName = "Cliente", unitName = "Local",
        status = status, scheduledDate = date
    )

    private fun remote(uid: String, start: Long?, end: Long?, color: EventColor) = RemoteEvent(
        accountId = "account", calendarHref = "calendar", href = uid, uid = uid, etag = null,
        sequence = null, rawIcs = "", summary = uid, description = null, location = null,
        start = start, end = end, allDay = false, color = color, rawEventColor = null,
        timeZone = null, recurrenceText = null, lastModified = null, lastSyncMillis = 0
    )

    private class FixedClock(private val value: Long) : ClockProvider {
        override fun nowMillis(): Long = value
    }

    private class FlowRepository(initial: List<ServiceOrder>) : ServiceOrderRepository {
        private val flow = MutableStateFlow(initial)
        override fun getServiceOrders(): Flow<List<ServiceOrder>> = flow
        override suspend fun getServiceOrderById(id: UUID): ServiceOrder? = flow.value.find { it.id == id }
        override suspend fun saveServiceOrder(serviceOrder: ServiceOrder) = Unit
        override suspend fun deleteServiceOrder(id: UUID) = Unit
    }
}
