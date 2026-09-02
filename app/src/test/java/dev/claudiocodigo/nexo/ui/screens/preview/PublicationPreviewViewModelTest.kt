package dev.claudiocodigo.nexo.ui.screens.preview

import android.content.Context
import dev.claudiocodigo.nexo.data.worker.PublicationScheduler
import dev.claudiocodigo.nexo.domain.model.ServiceOrder
import dev.claudiocodigo.nexo.domain.publication.ConfirmedPreviewSnapshot
import dev.claudiocodigo.nexo.domain.publication.OutboxOperation
import dev.claudiocodigo.nexo.domain.publication.OutboxStatus
import dev.claudiocodigo.nexo.domain.publication.PublicationRepository
import dev.claudiocodigo.nexo.domain.repository.ServiceOrderRepository
import dev.claudiocodigo.nexo.domain.repository.CalendarSetupRepository
import dev.claudiocodigo.nexo.domain.caldav.CalendarInfo
import dev.claudiocodigo.nexo.domain.serviceorder.RemoteBaseSnapshot
import dev.claudiocodigo.nexo.domain.serviceorder.RemoteOccurrenceKey
import dev.claudiocodigo.nexo.domain.serviceorder.ConclusionState
import dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderPreset
import dev.claudiocodigo.nexo.domain.serviceorder.StructuredServiceOrder
import dev.claudiocodigo.nexo.domain.time.ClockProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class PublicationPreviewViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val orderId = UUID.randomUUID()
    private lateinit var publicationRepository: FakePublicationRepository
    private lateinit var scheduler: FakeScheduler
    private lateinit var viewModel: PublicationPreviewViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        publicationRepository = FakePublicationRepository()
        scheduler = FakeScheduler()
        viewModel = PublicationPreviewViewModel(
            serviceOrderRepository = FakeServiceOrderRepository(provisionalOrder()),
            publicationRepository = publicationRepository,
            calendarSetupRepository = FakeCalendarSetupRepository(),
            scheduler = scheduler,
            clock = object : ClockProvider { override fun nowMillis() = 1_700_000_000_000L }
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `confirmation is enqueued once and schedules publication`() = runTest(dispatcher) {
        viewModel.loadPreview(orderId)
        advanceUntilIdle()

        viewModel.confirmPublication()
        viewModel.confirmPublication()
        advanceUntilIdle()

        assertEquals(1, publicationRepository.confirmCalls)
        assertTrue(scheduler.drainScheduled)
        assertTrue(viewModel.uiState.value is PreviewUiState.Confirmed)
    }

    @Test
    fun `queue failure is visible and can be retried`() = runTest(dispatcher) {
        publicationRepository.failConfirmation = true
        viewModel.loadPreview(orderId)
        advanceUntilIdle()

        viewModel.confirmPublication()
        advanceUntilIdle()

        val state = viewModel.uiState.value as PreviewUiState.Ready
        assertFalse(state.isConfirming)
        assertTrue(state.confirmationError?.isNotBlank() == true)
        assertFalse(scheduler.drainScheduled)
    }

    @Test
    fun `remote preview targets UID from ICS instead of event filename`() = runTest(dispatcher) {
        val remoteOrder = StructuredServiceOrder(
            id = orderId,
            title = "OS remota",
            clientName = "Cliente",
            unitName = "Unidade",
            originalDemand = "Demanda",
            occurrenceKey = RemoteOccurrenceKey(
                accountId = "acct-1",
                calendarHref = "https://cloud.example.com/calendars/work/",
                eventHref = "https://cloud.example.com/calendars/work/nome-arquivo.ics"
            ),
            baseSnapshot = RemoteBaseSnapshot(
                etag = "\"etag-1\"",
                rawIcs = "BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nUID:uid-interno-real\r\nDESCRIPTION:Antiga\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n",
                rawSummary = null,
                rawDescription = "Antiga"
            )
        )
        viewModel = PublicationPreviewViewModel(
            serviceOrderRepository = FakeServiceOrderRepository(remoteOrder),
            publicationRepository = publicationRepository,
            calendarSetupRepository = FakeCalendarSetupRepository(),
            scheduler = scheduler,
            clock = object : ClockProvider { override fun nowMillis() = 1_700_000_000_000L }
        )

        viewModel.loadPreview(orderId)
        advanceUntilIdle()

        val state = viewModel.uiState.value as PreviewUiState.Ready
        assertEquals("uid-interno-real", state.targetUid)
        assertFalse(state.renderedIcs.contains("DESCRIPTION:Antiga"))
        assertTrue(state.renderedIcs.contains("UID:uid-interno-real"))
        assertEquals("Agenda real", state.targetCalendarName)
    }

    @Test
    fun `update flow linked order creates update preview with history`() = runTest(dispatcher) {
        val remoteOrder = StructuredServiceOrder(
            id = orderId,
            title = "OS remota",
            clientName = "Cliente",
            unitName = "Unidade",
            technician = "Claudio",
            flow = dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderFlow.UPDATE,
            updateDraft = "Visita técnica realizada.",
            occurrenceKey = RemoteOccurrenceKey(
                accountId = "acct-1",
                calendarHref = "https://cloud.example.com/calendars/work/",
                eventHref = "https://cloud.example.com/calendars/work/evento.ics"
            ),
            baseSnapshot = RemoteBaseSnapshot(
                etag = "\"etag-1\"",
                rawIcs = "BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nUID:evento-real\r\nDESCRIPTION:Antiga\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n",
                rawSummary = null,
                rawDescription = "Antiga"
            )
        )
        viewModel = PublicationPreviewViewModel(
            serviceOrderRepository = FakeServiceOrderRepository(remoteOrder),
            publicationRepository = publicationRepository,
            calendarSetupRepository = FakeCalendarSetupRepository(),
            scheduler = scheduler,
            clock = object : ClockProvider { override fun nowMillis() = 1_700_000_000_000L }
        )

        viewModel.loadPreview(orderId)
        advanceUntilIdle()

        val state = viewModel.uiState.value as PreviewUiState.Ready
        assertEquals(dev.claudiocodigo.nexo.domain.publication.OutboxAction.UPDATE, state.action)
        assertTrue(state.renderedDescription.contains("Atualização:"))
        assertTrue(state.renderedDescription.contains("----- Histórico Remoto Preservado -----"))
    }

    private class FakePublicationRepository : PublicationRepository {
        var confirmCalls = 0
        var failConfirmation = false
        override fun observeOperations(): Flow<List<OutboxOperation>> = flowOf(emptyList())
        override suspend fun getOperationById(id: UUID) = null
        override suspend fun getLatestForOrder(orderId: UUID) = null
        override suspend fun confirmPreview(snapshot: ConfirmedPreviewSnapshot, forceOverwrite: Boolean): OutboxOperation {
            confirmCalls++
            if (failConfirmation) error("simulated local database failure")
            return OutboxOperation(
                orderId = snapshot.orderId,
                action = snapshot.action,
                payloadIcs = snapshot.rawIcsPayload,
                ifMatchEtag = snapshot.baseEtag,
                status = OutboxStatus.PENDING
            )
        }
        override suspend fun claimNextEligible(nowMillis: Long, leaseDurationMillis: Long) = null
        override suspend fun markSent(operationId: UUID, newEtag: String?, nowMillis: Long) = Unit
        override suspend fun markConflict(operationId: UUID, reason: String, nowMillis: Long) = Unit
        override suspend fun markFailed(operationId: UUID, reason: String, permanent: Boolean, nowMillis: Long) = Unit
        override suspend fun cancelPending(operationId: UUID) = false
        override suspend fun cancelOperation(operationId: UUID) = false
        override suspend fun cancelAllForOrder(orderId: UUID) = Unit
    }

    private fun provisionalOrder() = StructuredServiceOrder(
            id = orderId,
            title = "OS de teste",
            clientName = "Cliente",
            unitName = "Unidade",
            originalDemand = "Demanda de teste",
            scheduledStart = 1_700_000_000_000L,
            scheduledEnd = 1_700_003_600_000L
        )

    private class FakeServiceOrderRepository(private val order: StructuredServiceOrder) : ServiceOrderRepository {
        override fun getServiceOrders() = flowOf(emptyList<ServiceOrder>())
        override suspend fun getServiceOrderById(id: UUID) = null
        override suspend fun saveServiceOrder(serviceOrder: ServiceOrder) = Unit
        override suspend fun deleteServiceOrder(id: UUID) = Unit
        override fun observeStructuredOrders() = flowOf(listOf(order))
        override suspend fun getStructuredOrderById(id: UUID) = order.takeIf { it.id == id }
        override suspend fun saveStructuredOrder(order: StructuredServiceOrder) = Unit
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
        ) = order
        override suspend fun getLinkedOrder(key: RemoteOccurrenceKey) = null
    }

    private class FakeCalendarSetupRepository : CalendarSetupRepository {
        private val selected = CalendarInfo(
            href = "https://cloud.example.com/calendars/work/",
            displayName = "Agenda real",
            description = null,
            color = null,
            supportsVeEvent = true,
            hasWritePrivilege = true,
            syncToken = null
        )
        override suspend fun ensureAccount(server: String, user: String) = "acct-1"
        override suspend fun getActiveAccountId() = "acct-1"
        override suspend fun saveCalendars(accountId: String, calendars: List<CalendarInfo>) = Unit
        override suspend fun selectWorkingCalendar(accountId: String, href: String) = Unit
        override fun observeCalendars(accountId: String) = flowOf(listOf(selected))
        override fun observeSelectedCalendar() = flowOf(selected)
        override suspend fun disconnectLocal() = Unit
    }

    private class FakeScheduler : PublicationScheduler(mock(Context::class.java)) {
        var drainScheduled = false
        override fun scheduleDrain() {
            drainScheduled = true
        }
    }
}
