package dev.claudiocodigo.nexo.ui.screens.conflito

import dev.claudiocodigo.nexo.domain.caldav.AccountIdentity
import dev.claudiocodigo.nexo.domain.caldav.CalendarInfo
import dev.claudiocodigo.nexo.domain.caldav.CalendarSyncState
import dev.claudiocodigo.nexo.domain.caldav.EventColor
import dev.claudiocodigo.nexo.domain.caldav.RemoteEvent
import dev.claudiocodigo.nexo.domain.model.ServiceOrder
import dev.claudiocodigo.nexo.domain.repository.CalendarRepository
import dev.claudiocodigo.nexo.domain.repository.ServiceOrderRepository
import dev.claudiocodigo.nexo.domain.serviceorder.ConflictField
import dev.claudiocodigo.nexo.domain.serviceorder.FieldChoice
import dev.claudiocodigo.nexo.domain.serviceorder.PublicationState
import dev.claudiocodigo.nexo.domain.serviceorder.RemoteBaseSnapshot
import dev.claudiocodigo.nexo.domain.serviceorder.RemoteOccurrenceKey
import dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderPreset
import dev.claudiocodigo.nexo.domain.serviceorder.StructuredServiceOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
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
class ConflictReviewViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var serviceOrderRepository: FakeServiceOrderRepository
    private lateinit var calendarRepository: FakeCalendarRepository
    private lateinit var viewModel: ConflictReviewViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        serviceOrderRepository = FakeServiceOrderRepository()
        calendarRepository = FakeCalendarRepository()
        viewModel = ConflictReviewViewModel(serviceOrderRepository, calendarRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun load_detectsDifferencesBetweenLocalDraftAndRemoteEvent() = runTest(testDispatcher) {
        val orderId = UUID.randomUUID()
        val key = RemoteOccurrenceKey("acct-1", "/cal/", "/cal/e1.ics", null)
        val order = StructuredServiceOrder(
            id = orderId,
            occurrenceKey = key,
            title = "Manutenção Nobreak",
            clientName = "Cliente",
            unitName = "Unidade",
            originalDemand = "Nobreak desligando sozinho",
            baseSnapshot = RemoteBaseSnapshot(etag = "\"etag-1\"", rawIcs = "", rawSummary = "Manutenção Nobreak", rawDescription = "Demanda antiga")
        )
        serviceOrderRepository.orders[orderId] = order

        val remoteEvent = RemoteEvent(
            accountId = "acct-1",
            calendarHref = "/cal/",
            href = "/cal/e1.ics",
            uid = "e1",
            etag = "\"etag-2\"",
            sequence = 2,
            rawIcs = "",
            summary = "Manutenção Nobreak - Urgente",
            description = "Demanda: Nobreak desligando e soltando fumaça",
            location = null,
            start = null,
            end = null,
            allDay = false,
            color = EventColor.NAO_CLASSIFICADO,
            rawEventColor = null,
            timeZone = null,
            recurrenceText = null,
            lastModified = null,
            lastSyncMillis = 1000L
        )
        calendarRepository.events["/cal/e1.ics"] = remoteEvent

        viewModel.load(orderId)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is ConflictUiState.Ready)
        val ready = state as ConflictUiState.Ready
        assertEquals(2, ready.differences.size)
        assertEquals("\"etag-2\"", ready.remoteEtag)
        assertTrue(ready.choices.values.all { it == FieldChoice.KEEP_LOCAL })

        viewModel.onChoiceSelected(ConflictField.TITLE, FieldChoice.USE_REMOTE)
        val updatedReady = viewModel.uiState.value as ConflictUiState.Ready
        assertEquals(FieldChoice.USE_REMOTE, updatedReady.choices[ConflictField.TITLE])

        var resolvedCalled = false
        viewModel.applyResolution { resolvedCalled = true }
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(resolvedCalled)
        val saved = serviceOrderRepository.orders[orderId]
        assertEquals("Manutenção Nobreak - Urgente", saved?.title)
        assertEquals("Nobreak desligando sozinho", saved?.originalDemand) // kept local
        assertEquals("\"etag-2\"", saved?.baseSnapshot?.etag) // renewed ETag
        assertEquals(PublicationState.LOCAL_DRAFT, saved?.publicationState)
    }

    private class FakeServiceOrderRepository : ServiceOrderRepository {
        val orders = mutableMapOf<UUID, StructuredServiceOrder>()
        override fun getServiceOrders() = flowOf(orders.values.map { it.toLegacy() })
        override suspend fun getServiceOrderById(id: UUID) = orders[id]?.toLegacy()
        override suspend fun saveServiceOrder(serviceOrder: ServiceOrder) = Unit
        override suspend fun deleteServiceOrder(id: UUID) = Unit
        override fun observeStructuredOrders() = flowOf(orders.values.toList())
        override suspend fun getStructuredOrderById(id: UUID) = orders[id]
        override suspend fun saveStructuredOrder(order: StructuredServiceOrder) { orders[order.id] = order }
        override suspend fun createOrGetAttendance(
            key: RemoteOccurrenceKey, initialPreset: ServiceOrderPreset, title: String,
            clientName: String, unitName: String, rawSummary: String?, rawDescription: String?,
            rawIcs: String?, etag: String?, startMillis: Long?, endMillis: Long?
        ) = orders.values.first()
        override suspend fun getLinkedOrder(key: RemoteOccurrenceKey) = orders.values.firstOrNull { it.occurrenceKey == key }
    }

    private class FakeCalendarRepository : CalendarRepository {
        val events = mutableMapOf<String, RemoteEvent>()
        override fun observeEvents() = flowOf(events.values.toList())
        override fun observeEventsForDay(dayStartMillis: Long, dayEndMillis: Long) = flowOf(events.values.toList())
        override fun searchEvents(query: String) = emptyFlow<List<RemoteEvent>>()
        override fun observeOverdue(nowMillis: Long) = emptyFlow<List<RemoteEvent>>()
        override suspend fun getEvent(accountId: String, calendarHref: String, href: String) = events[href]
        override fun observeAccount() = emptyFlow<AccountIdentity?>()
        override fun observeSelectedCalendar() = emptyFlow<CalendarInfo?>()
        override fun observeSyncState() = emptyFlow<CalendarSyncState?>()
        override fun classifyColor(raw: String?) = EventColor.NAO_CLASSIFICADO
    }
}
