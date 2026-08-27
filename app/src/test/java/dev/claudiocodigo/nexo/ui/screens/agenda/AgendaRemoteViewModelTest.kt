package dev.claudiocodigo.nexo.ui.screens.agenda

import dev.claudiocodigo.nexo.domain.caldav.AccountIdentity
import dev.claudiocodigo.nexo.domain.caldav.CalendarInfo
import dev.claudiocodigo.nexo.domain.caldav.CalendarSyncState
import dev.claudiocodigo.nexo.domain.caldav.EventColor
import dev.claudiocodigo.nexo.domain.caldav.RemoteEvent
import dev.claudiocodigo.nexo.domain.model.ServiceOrder
import dev.claudiocodigo.nexo.domain.repository.CalendarRepository
import dev.claudiocodigo.nexo.domain.repository.ServiceOrderRepository
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
import java.time.Instant
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class AgendaRemoteViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var orders: MutableStateFlow<List<ServiceOrder>>
    private lateinit var events: MutableStateFlow<List<RemoteEvent>>

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        orders = MutableStateFlow(emptyList())
        events = MutableStateFlow(emptyList())
    }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `search covers remote summary description and location`() = runTest {
        events.value = listOf(remote("summary", "needle", null, null), remote("description", "x", "needle", null), remote("location", "x", null, "needle"))
        val vm = AgendaViewModel(FakeOrders(orders), FakeCalendar(events))
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.onSearchQueryChange("needle")
        advanceUntilIdle()
        val state = vm.uiState.value as AgendaUiState.Success
        assertEquals(3, state.groupedRemoteEvents.values.flatten().size)
        job.cancel()
    }

    @Test fun `joins local and remote items for one date but separates different years`() = runTest {
        val oneDay = Instant.parse("2024-01-02T10:00:00Z").toEpochMilli()
        val anotherYear = Instant.parse("2025-01-02T10:00:00Z").toEpochMilli()
        orders.value = listOf(order("local", anotherYear))
        events.value = listOf(remote("remote", "remote", null, null, anotherYear), remote("old", "old", null, null, oneDay))
        val vm = AgendaViewModel(FakeOrders(orders), FakeCalendar(events))
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val state = vm.uiState.value as AgendaUiState.Success
        assertEquals(2, state.groupedOrders.keys.size)
        assertTrue(state.groupedOrders.keys.any { "2024" in it })
        assertTrue(state.groupedOrders.keys.any { "2025" in it })
        assertEquals(1, state.groupedOrders.values.flatten().size)
        assertEquals(2, state.groupedRemoteEvents.values.flatten().size)
        job.cancel()
    }

    @Test fun `orders entries from newest to oldest and puts undated last`() = runTest {
        val late = Instant.parse("2025-01-02T12:00:00Z").toEpochMilli()
        val early = Instant.parse("2025-01-02T08:00:00Z").toEpochMilli()
        orders.value = listOf(order("late", late), order("early", early), order("undated", null))
        val vm = AgendaViewModel(FakeOrders(orders), FakeCalendar(events))
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val state = vm.uiState.value as AgendaUiState.Success
        assertEquals(listOf("late", "early"), state.groupedOrders.values.first { it.size == 2 }.map { it.title })
        assertEquals("Sem data definida", state.groupedOrders.keys.last())
        job.cancel()
    }

    @Test fun `orders remote event days and entries from newest to oldest`() = runTest {
        val oldDay = Instant.parse("2025-01-01T10:00:00Z").toEpochMilli()
        val newMorning = Instant.parse("2025-01-02T08:00:00Z").toEpochMilli()
        val newAfternoon = Instant.parse("2025-01-02T15:00:00Z").toEpochMilli()
        events.value = listOf(
            remote("old", "old", null, null, oldDay),
            remote("morning", "morning", null, null, newMorning),
            remote("afternoon", "afternoon", null, null, newAfternoon)
        )
        val vm = AgendaViewModel(FakeOrders(orders), FakeCalendar(events))
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val state = vm.uiState.value as AgendaUiState.Success

        assertTrue("2025" in state.groupedRemoteEvents.keys.first())
        assertEquals(
            listOf("afternoon", "morning"),
            state.groupedRemoteEvents.values.first { it.size == 2 }.map { it.uid }
        )
        assertEquals("old", state.groupedRemoteEvents.values.last().single().uid)
        job.cancel()
    }

    private fun order(title: String, date: Long?) = ServiceOrder(UUID.randomUUID(), null, title, "", dev.claudiocodigo.nexo.domain.model.ServiceOrderStatus.PENDENTE, "Client", "Unit", date, 1, 1)
    private fun remote(uid: String, summary: String, description: String?, location: String?, start: Long? = Instant.parse("2025-01-02T10:00:00Z").toEpochMilli()) = RemoteEvent("a", "c", uid, uid, null, null, "", summary, description, location, start, start?.plus(60_000), false, EventColor.NAO_CLASSIFICADO, null, null, null, null, 0)
}

private class FakeOrders(private val flow: Flow<List<ServiceOrder>>) : ServiceOrderRepository {
    override fun getServiceOrders() = flow
    override suspend fun getServiceOrderById(id: UUID) = null
    override suspend fun saveServiceOrder(serviceOrder: ServiceOrder) = Unit
    override suspend fun deleteServiceOrder(id: UUID) = Unit
}

private class FakeCalendar(private val flow: Flow<List<RemoteEvent>>) : CalendarRepository {
    override fun observeEvents() = flow
    override fun observeEventsForDay(dayStartMillis: Long, dayEndMillis: Long) = flowOf(emptyList<RemoteEvent>())
    override fun searchEvents(query: String) = flowOf(emptyList<RemoteEvent>())
    override fun observeOverdue(nowMillis: Long) = flowOf(emptyList<RemoteEvent>())
    override suspend fun getEvent(accountId: String, calendarHref: String, href: String) = null
    override fun observeAccount() = flowOf<AccountIdentity?>(null)
    override fun observeSelectedCalendar() = flowOf<CalendarInfo?>(null)
    override fun observeSyncState() = flowOf<CalendarSyncState?>(null)
    override fun classifyColor(raw: String?) = EventColor.NAO_CLASSIFICADO
}
