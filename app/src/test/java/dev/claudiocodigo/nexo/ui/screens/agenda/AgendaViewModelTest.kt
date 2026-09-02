package dev.claudiocodigo.nexo.ui.screens.agenda

import dev.claudiocodigo.nexo.domain.model.ServiceOrder
import dev.claudiocodigo.nexo.domain.repository.ServiceOrderRepository
import dev.claudiocodigo.nexo.domain.serviceorder.RemoteOccurrenceKey
import dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderPreset
import dev.claudiocodigo.nexo.domain.serviceorder.StructuredServiceOrder
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class AgendaViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeAgendaServiceOrderRepository
    private lateinit var viewModel: AgendaViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeAgendaServiceOrderRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `search filters orders by title`() = runTest {
        val orders = listOf(
            ServiceOrder(id = UUID.randomUUID(), title = "Manutenção", clientName = "A", unitName = "U1"),
            ServiceOrder(id = UUID.randomUUID(), title = "Instalação", clientName = "B", unitName = "U2")
        )
        repository.ordersFlow.value = orders

        viewModel = AgendaViewModel(repository)

        // Start collecting the state to trigger the flow
        val job = launch { viewModel.uiState.collect {} }

        advanceUntilIdle()

        viewModel.onSearchQueryChange("Manut")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("State should be Success but was $state", state is AgendaUiState.Success)
        val filtered = (state as AgendaUiState.Success).groupedOrders.values.flatten()
        assertEquals(1, filtered.size)
        assertEquals("Manutenção", filtered[0].title)

        job.cancel()
    }

    @Test
    fun `search filters orders by client name`() = runTest {
        val orders = listOf(
            ServiceOrder(id = UUID.randomUUID(), title = "T1", clientName = "Hospital X", unitName = "U1"),
            ServiceOrder(id = UUID.randomUUID(), title = "T2", clientName = "Banco Y", unitName = "U2")
        )
        repository.ordersFlow.value = orders

        viewModel = AgendaViewModel(repository)
        val job = launch { viewModel.uiState.collect {} }

        advanceUntilIdle()

        viewModel.onSearchQueryChange("Hospital")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("State should be Success but was $state", state is AgendaUiState.Success)
        val filtered = (state as AgendaUiState.Success).groupedOrders.values.flatten()
        assertEquals(1, filtered.size)
        assertEquals("Hospital X", filtered[0].clientName)

        job.cancel()
    }

    @Test
    fun `search filters by external number and groups undated orders explicitly`() = runTest {
        val date = 1_735_689_600_000L
        val orders = listOf(
            ServiceOrder(id = UUID.randomUUID(), externalId = "15428", title = "Troca", clientName = "A", unitName = "U1", scheduledDate = date),
            ServiceOrder(id = UUID.randomUUID(), title = "Sem data", clientName = "B", unitName = "U2")
        )
        repository.ordersFlow.value = orders

        viewModel = AgendaViewModel(repository)
        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val initial = viewModel.uiState.value as AgendaUiState.Success
        assertTrue(initial.groupedOrders.containsKey("Sem data definida"))

        viewModel.onSearchQueryChange("15428")
        advanceUntilIdle()
        val filtered = viewModel.uiState.value as AgendaUiState.Success
        assertEquals(listOf("15428"), filtered.groupedOrders.values.flatten().map { it.externalId })
        job.cancel()
    }

    @Test
    fun `delete local draft delegates the exact internal uuid to repository`() = runTest {
        val id = UUID.randomUUID()
        viewModel = AgendaViewModel(repository)

        viewModel.deleteLocalDraft(id)
        advanceUntilIdle()

        assertEquals(listOf(id), repository.deletedIds)
        assertEquals(null, viewModel.deleteError.value)
    }

    @Test
    fun `syncNow triggers coordinator and reflects isSyncing state`() = runTest {
        val syncingFlow = MutableStateFlow(false)
        var syncTriggeredCount = 0
        val fakeCoordinator = object : dev.claudiocodigo.nexo.domain.caldav.CalendarSyncCoordinator {
            override val isSyncing: kotlinx.coroutines.flow.StateFlow<Boolean> = syncingFlow
            override suspend fun syncNow(): dev.claudiocodigo.nexo.domain.caldav.SyncOutcome {
                syncTriggeredCount++
                return dev.claudiocodigo.nexo.domain.caldav.SyncOutcome.Success(0, 0, 0, null)
            }
        }

        val vm = AgendaViewModel(
            repository = repository,
            calendarRepository = dev.claudiocodigo.nexo.ui.screens.agenda.EmptyCalendarRepository,
            publicationRepository = null,
            syncCoordinator = fakeCoordinator
        )

        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(1, syncTriggeredCount) // triggered on init

        syncingFlow.value = true
        advanceUntilIdle()
        var state = vm.uiState.value as AgendaUiState.Success
        assertEquals(true, state.isSyncing)

        vm.syncNow()
        advanceUntilIdle()
        assertEquals(2, syncTriggeredCount)

        syncingFlow.value = false
        advanceUntilIdle()
        state = vm.uiState.value as AgendaUiState.Success
        assertEquals(false, state.isSyncing)

        job.cancel()
    }

    private class FakeAgendaServiceOrderRepository : ServiceOrderRepository {
        val ordersFlow = MutableStateFlow<List<ServiceOrder>>(emptyList())
        val deletedIds = mutableListOf<UUID>()

        override fun getServiceOrders(): Flow<List<ServiceOrder>> = ordersFlow
        override suspend fun getServiceOrderById(id: UUID): ServiceOrder? = ordersFlow.value.find { it.id == id }
        override suspend fun saveServiceOrder(serviceOrder: ServiceOrder) = Unit
        override suspend fun deleteServiceOrder(id: UUID) { deletedIds.add(id) }
        override fun observeStructuredOrders(): Flow<List<StructuredServiceOrder>> = flowOf(emptyList())
        override suspend fun getStructuredOrderById(id: UUID): StructuredServiceOrder? = null
        override suspend fun saveStructuredOrder(order: StructuredServiceOrder) = Unit
        override suspend fun createOrGetAttendance(
            key: RemoteOccurrenceKey, initialPreset: ServiceOrderPreset, title: String,
            clientName: String, unitName: String, rawSummary: String?, rawDescription: String?,
            rawIcs: String?, etag: String?, startMillis: Long?, endMillis: Long?
        ): StructuredServiceOrder = throw UnsupportedOperationException()
        override suspend fun getLinkedOrder(key: RemoteOccurrenceKey): StructuredServiceOrder? = null
    }
}
