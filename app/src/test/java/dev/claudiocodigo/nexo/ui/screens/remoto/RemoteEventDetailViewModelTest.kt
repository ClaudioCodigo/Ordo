package dev.claudiocodigo.nexo.ui.screens.remoto

import dev.claudiocodigo.nexo.domain.caldav.AccountIdentity
import dev.claudiocodigo.nexo.domain.caldav.CalendarInfo
import dev.claudiocodigo.nexo.domain.caldav.CalendarSyncState
import dev.claudiocodigo.nexo.domain.caldav.EventColor
import dev.claudiocodigo.nexo.domain.caldav.RemoteEvent
import dev.claudiocodigo.nexo.domain.model.ServiceOrder
import dev.claudiocodigo.nexo.domain.repository.CalendarRepository
import dev.claudiocodigo.nexo.domain.repository.ServiceOrderRepository
import dev.claudiocodigo.nexo.domain.serviceorder.RemoteBaseSnapshot
import dev.claudiocodigo.nexo.domain.serviceorder.RemoteOccurrenceKey
import dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderPreset
import dev.claudiocodigo.nexo.domain.serviceorder.StructuredServiceOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteEventDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var orders: FakeServiceOrderRepository
    private lateinit var calendar: FakeCalendarRepository
    private lateinit var viewModel: RemoteEventDetailViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        orders = FakeServiceOrderRepository()
        calendar = FakeCalendarRepository()
        viewModel = RemoteEventDetailViewModel(calendar, orders)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `new attendance extracts official number from summary`() = runTest(dispatcher) {
        calendar.event = remoteEvent(summary = "PIER - 15455 - CLAUDIO - REDE - TESTE", etag = "\"e1\"")
        viewModel.load("acct", "/cal/", "/cal/event.ics")
        advanceUntilIdle()

        var requiresReview = true
        viewModel.startAttendance { _, review -> requiresReview = review }
        advanceUntilIdle()

        assertEquals("15455", orders.orders.values.single().externalId)
        assertFalse(requiresReview)
    }

    @Test
    fun `official number added remotely requires review without overwriting local value`() = runTest(dispatcher) {
        val key = RemoteOccurrenceKey("acct", "/cal/", "/cal/event.ics")
        val existing = StructuredServiceOrder(
            id = UUID.randomUUID(),
            occurrenceKey = key,
            externalId = null,
            title = "PIER - CLAUDIO - REDE - TESTE",
            clientName = "PIER",
            unitName = "Unidade",
            baseSnapshot = RemoteBaseSnapshot(
                etag = "\"e1\"",
                rawIcs = "old",
                rawSummary = "PIER - ???? - CLAUDIO - REDE - TESTE",
                rawDescription = "Demanda"
            )
        )
        orders.orders[existing.id] = existing
        calendar.event = remoteEvent(summary = "PIER - 15455 - CLAUDIO - REDE - TESTE", etag = "\"e2\"")
        viewModel.load("acct", "/cal/", "/cal/event.ics")
        advanceUntilIdle()

        var requiresReview = false
        viewModel.startAttendance { _, review -> requiresReview = review }
        advanceUntilIdle()

        assertTrue(requiresReview)
        assertNull(orders.orders[existing.id]?.externalId)
        assertEquals("\"e1\"", orders.orders[existing.id]?.baseSnapshot?.etag)
    }

    @Test
    fun `empty actionable diff refreshes remote base without opening review`() = runTest(dispatcher) {
        val key = RemoteOccurrenceKey("acct", "/cal/", "/cal/event.ics")
        val existing = StructuredServiceOrder(
            id = UUID.randomUUID(),
            occurrenceKey = key,
            title = "Título já conciliado",
            clientName = "Cliente",
            unitName = "Unidade",
            originalDemand = "Demanda já conciliada",
            baseSnapshot = RemoteBaseSnapshot(
                etag = "\"e1\"",
                rawIcs = "old",
                rawSummary = "Título antigo",
                rawDescription = "Demanda antiga"
            )
        )
        orders.orders[existing.id] = existing
        calendar.event = remoteEvent(
            summary = "Título já conciliado",
            description = "Demanda já conciliada",
            etag = "\"e2\"",
            rawIcs = "new"
        )
        viewModel.load("acct", "/cal/", "/cal/event.ics")
        advanceUntilIdle()

        var requiresReview = true
        viewModel.startAttendance { _, review -> requiresReview = review }
        advanceUntilIdle()

        val saved = orders.orders.getValue(existing.id)
        assertFalse(requiresReview)
        assertEquals("Título já conciliado", saved.title)
        assertEquals("Demanda já conciliada", saved.originalDemand)
        assertEquals("\"e2\"", saved.baseSnapshot?.etag)
        assertEquals("new", saved.baseSnapshot?.rawIcs)
        assertEquals("Título já conciliado", saved.baseSnapshot?.rawSummary)
        assertEquals("Demanda já conciliada", saved.baseSnapshot?.rawDescription)
    }

    @Test
    fun `meaningful unstructured remote description remains actionable`() = runTest(dispatcher) {
        val key = RemoteOccurrenceKey("acct", "/cal/", "/cal/event.ics")
        val existing = StructuredServiceOrder(
            id = UUID.randomUUID(),
            occurrenceKey = key,
            title = "Atendimento",
            clientName = "Cliente",
            unitName = "Unidade",
            originalDemand = "Minha demanda local",
            baseSnapshot = RemoteBaseSnapshot(
                etag = "\"e1\"",
                rawIcs = "old",
                rawSummary = "Atendimento",
                rawDescription = "Solicitação antiga sem rótulos"
            )
        )
        orders.orders[existing.id] = existing
        calendar.event = remoteEvent(
            summary = "Atendimento",
            description = "Nova solicitação remota em texto corrido",
            etag = "\"e2\"",
            rawIcs = "new"
        )
        viewModel.load("acct", "/cal/", "/cal/event.ics")
        advanceUntilIdle()

        var requiresReview = false
        viewModel.startAttendance { _, review -> requiresReview = review }
        advanceUntilIdle()

        val saved = orders.orders.getValue(existing.id)
        assertTrue(requiresReview)
        assertEquals("Minha demanda local", saved.originalDemand)
        assertEquals("\"e1\"", saved.baseSnapshot?.etag)
        assertEquals("old", saved.baseSnapshot?.rawIcs)
    }

    @Test
    fun `metadata only remote change refreshes base and preserves local edits`() = runTest(dispatcher) {
        val key = RemoteOccurrenceKey("acct", "/cal/", "/cal/event.ics")
        val existing = StructuredServiceOrder(
            id = UUID.randomUUID(),
            occurrenceKey = key,
            title = "Meu título local",
            clientName = "Cliente",
            unitName = "Unidade",
            originalDemand = "Minha demanda local",
            baseSnapshot = RemoteBaseSnapshot(
                etag = "\"e1\"",
                rawIcs = "old",
                rawSummary = "Atendimento remoto",
                rawDescription = "Demanda remota"
            )
        )
        orders.orders[existing.id] = existing
        calendar.event = remoteEvent(
            summary = "Atendimento remoto",
            description = "Demanda remota",
            etag = "\"e2\"",
            rawIcs = "new-metadata"
        )
        viewModel.load("acct", "/cal/", "/cal/event.ics")
        advanceUntilIdle()

        var requiresReview = true
        viewModel.startAttendance { _, review -> requiresReview = review }
        advanceUntilIdle()

        val saved = orders.orders.getValue(existing.id)
        assertFalse(requiresReview)
        assertEquals("Meu título local", saved.title)
        assertEquals("Minha demanda local", saved.originalDemand)
        assertEquals("\"e2\"", saved.baseSnapshot?.etag)
        assertEquals("new-metadata", saved.baseSnapshot?.rawIcs)
    }

    @Test
    fun `unmapped remote text change fails safely without advancing base`() = runTest(dispatcher) {
        val key = RemoteOccurrenceKey("acct", "/cal/", "/cal/event.ics")
        val existing = StructuredServiceOrder(
            id = UUID.randomUUID(),
            occurrenceKey = key,
            title = "Atendimento",
            clientName = "Cliente",
            unitName = "Unidade",
            originalDemand = "Falha intermitente",
            baseSnapshot = RemoteBaseSnapshot(
                etag = "\"e1\"",
                rawIcs = "old",
                rawSummary = "Atendimento",
                rawDescription = "Demanda: Falha intermitente\nAtualização: Visita anterior"
            )
        )
        orders.orders[existing.id] = existing
        calendar.event = remoteEvent(
            summary = "Atendimento",
            description = "Demanda: Falha intermitente\nAtualização: Nova visita remota",
            etag = "\"e2\"",
            rawIcs = "new"
        )
        viewModel.load("acct", "/cal/", "/cal/event.ics")
        advanceUntilIdle()

        var started = false
        viewModel.startAttendance { _, _ -> started = true }
        advanceUntilIdle()

        assertFalse(started)
        assertTrue(viewModel.uiState.value is RemoteEventDetailUiState.Error)
        val saved = orders.orders.getValue(existing.id)
        assertEquals("\"e1\"", saved.baseSnapshot?.etag)
        assertEquals("old", saved.baseSnapshot?.rawIcs)
        assertEquals("Falha intermitente", saved.originalDemand)
    }

    private fun remoteEvent(
        summary: String,
        etag: String,
        description: String = "Demanda",
        rawIcs: String = "BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nUID:uid-real\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n"
    ) = RemoteEvent(
        accountId = "acct",
        calendarHref = "/cal/",
        href = "/cal/event.ics",
        uid = "uid-real",
        etag = etag,
        sequence = 1,
        rawIcs = rawIcs,
        summary = summary,
        description = description,
        location = null,
        start = null,
        end = null,
        allDay = false,
        color = EventColor.NAO_CLASSIFICADO,
        rawEventColor = null,
        timeZone = null,
        recurrenceText = null,
        lastModified = null,
        lastSyncMillis = 1L
    )

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
            key: RemoteOccurrenceKey,
            initialPreset: ServiceOrderPreset,
            title: String,
            clientName: String,
            unitName: String,
            rawSummary: String?,
            rawDescription: String?,
            rawIcs: String?,
            etag: String?,
            startMillis: Long?,
            endMillis: Long?
        ): StructuredServiceOrder {
            return getLinkedOrder(key) ?: StructuredServiceOrder(
                id = UUID.randomUUID(),
                occurrenceKey = key,
                title = title,
                clientName = clientName,
                unitName = unitName,
                originalDemand = rawDescription.orEmpty(),
                baseSnapshot = RemoteBaseSnapshot(etag = etag, rawIcs = rawIcs.orEmpty(), rawSummary = rawSummary, rawDescription = rawDescription)
            ).also { orders[it.id] = it }
        }
        override suspend fun getLinkedOrder(key: RemoteOccurrenceKey) = orders.values.firstOrNull { it.occurrenceKey == key }
    }

    private class FakeCalendarRepository : CalendarRepository {
        lateinit var event: RemoteEvent
        override fun observeEvents() = flowOf(listOf(event))
        override fun observeEventsForDay(dayStartMillis: Long, dayEndMillis: Long) = flowOf(listOf(event))
        override fun searchEvents(query: String) = emptyFlow<List<RemoteEvent>>()
        override fun observeOverdue(nowMillis: Long) = emptyFlow<List<RemoteEvent>>()
        override suspend fun getEvent(accountId: String, calendarHref: String, href: String) = event
        override fun observeAccount() = emptyFlow<AccountIdentity?>()
        override fun observeSelectedCalendar() = emptyFlow<CalendarInfo?>()
        override fun observeSyncState() = emptyFlow<CalendarSyncState?>()
        override fun classifyColor(raw: String?) = EventColor.NAO_CLASSIFICADO
    }
}
