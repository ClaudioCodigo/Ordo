package dev.claudiocodigo.nexo.data.publication

import dev.claudiocodigo.nexo.domain.caldav.AccountIdentity
import dev.claudiocodigo.nexo.domain.caldav.CalDavCredentials
import dev.claudiocodigo.nexo.domain.caldav.CalDavWriteClient
import dev.claudiocodigo.nexo.domain.caldav.ConditionalCreate
import dev.claudiocodigo.nexo.domain.caldav.ConditionalUpdate
import dev.claudiocodigo.nexo.domain.caldav.CredentialStore
import dev.claudiocodigo.nexo.domain.caldav.WriteOutcome
import dev.claudiocodigo.nexo.domain.publication.ConfirmedPreviewSnapshot
import dev.claudiocodigo.nexo.domain.publication.DrainOutcome
import dev.claudiocodigo.nexo.domain.publication.OutboxAction
import dev.claudiocodigo.nexo.domain.publication.OutboxOperation
import dev.claudiocodigo.nexo.domain.publication.OutboxStatus
import dev.claudiocodigo.nexo.domain.publication.PublicationRepository
import dev.claudiocodigo.nexo.domain.repository.ServiceOrderRepository
import dev.claudiocodigo.nexo.domain.repository.CalendarSetupRepository
import dev.claudiocodigo.nexo.domain.caldav.CalendarInfo
import dev.claudiocodigo.nexo.domain.serviceorder.PublicationState
import dev.claudiocodigo.nexo.domain.serviceorder.RemoteBaseSnapshot
import dev.claudiocodigo.nexo.domain.serviceorder.RemoteOccurrenceKey
import dev.claudiocodigo.nexo.domain.serviceorder.StructuredServiceOrder
import dev.claudiocodigo.nexo.domain.time.ClockProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class PublicationCoordinatorTest {

    private lateinit var publicationRepository: FakePublicationRepository
    private lateinit var serviceOrderRepository: FakeServiceOrderRepository
    private lateinit var writeClient: FakeWriteClient
    private lateinit var credentialStore: FakeCredentialStore
    private lateinit var coordinator: RoomPublicationCoordinator

    @Before
    fun setUp() {
        publicationRepository = FakePublicationRepository()
        serviceOrderRepository = FakeServiceOrderRepository()
        writeClient = FakeWriteClient()
        credentialStore = FakeCredentialStore()
        coordinator = RoomPublicationCoordinator(
            publicationRepository = publicationRepository,
            serviceOrderRepository = serviceOrderRepository,
            calendarSetupRepository = FakeCalendarSetupRepository(),
            writeClient = writeClient,
            credentialStore = credentialStore,
            clock = object : ClockProvider { override fun nowMillis() = 1000L }
        )
    }

    @Test
    fun `drainNext returns QueueEmpty when no eligible operations exist`() = runBlocking {
        val outcome = coordinator.drainNext()
        assertTrue(outcome is DrainOutcome.QueueEmpty)
    }

    @Test
    fun `drainNext executes conditional create and marks operation sent`() = runBlocking {
        val orderId = UUID.randomUUID()
        val order = StructuredServiceOrder(
            id = orderId,
            title = "Nova OS",
            clientName = "Cliente",
            unitName = "Unidade",
            occurrenceKey = null
        )
        serviceOrderRepository.orders[orderId] = order

        val opId = UUID.randomUUID()
        val op = OutboxOperation(
            id = opId,
            orderId = orderId,
            action = OutboxAction.CREATE,
            payloadIcs = "BEGIN:VCALENDAR\nEND:VCALENDAR",
            ifMatchEtag = null,
            status = OutboxStatus.PENDING
        )
        publicationRepository.operations[opId] = op

        writeClient.nextCreateOutcome = WriteOutcome.Created(
            "https://cloud.example.com/remote.php/dav/calendars/maria/agenda-real/$orderId.ics",
            "\"etag-1\""
        )

        val outcome = coordinator.drainNext()

        assertTrue(outcome is DrainOutcome.Success)
        assertEquals(opId, (outcome as DrainOutcome.Success).operationId)
        assertEquals(OutboxStatus.SENT, publicationRepository.operations[opId]?.status)
        assertEquals(PublicationState.PUBLISHED, serviceOrderRepository.orders[orderId]?.publicationState)
        assertEquals(
            "https://cloud.example.com/remote.php/dav/calendars/maria/agenda-real/$orderId.ics",
            writeClient.lastCreateRequest?.targetHref
        )
        assertEquals(
            "https://cloud.example.com/remote.php/dav/calendars/maria/agenda-real/$orderId.ics",
            serviceOrderRepository.orders[orderId]?.occurrenceKey?.eventHref
        )
        assertEquals("\"etag-1\"", serviceOrderRepository.orders[orderId]?.baseSnapshot?.etag)
    }

    @Test
    fun `drainNext handles 412 Conflict and marks order in conflict state`() = runBlocking {
        val orderId = UUID.randomUUID()
        val order = StructuredServiceOrder(
            id = orderId,
            title = "OS Existente",
            clientName = "Cliente",
            unitName = "Unidade",
            occurrenceKey = RemoteOccurrenceKey("acct-1", "/cal/", "/cal/e1.ics", null),
            baseSnapshot = RemoteBaseSnapshot(etag = "\"stale-etag\"", rawIcs = "", rawSummary = "", rawDescription = "")
        )
        serviceOrderRepository.orders[orderId] = order

        val opId = UUID.randomUUID()
        val op = OutboxOperation(
            id = opId,
            orderId = orderId,
            action = OutboxAction.UPDATE,
            payloadIcs = "BEGIN:VCALENDAR\nEND:VCALENDAR",
            ifMatchEtag = "\"stale-etag\"",
            status = OutboxStatus.PENDING
        )
        publicationRepository.operations[opId] = op

        writeClient.nextUpdateOutcome = WriteOutcome.Conflict("/cal/e1.ics", 412, "Precondição falhou")

        val outcome = coordinator.drainNext()

        assertTrue(outcome is DrainOutcome.Conflict)
        assertEquals(OutboxStatus.CONFLICT, publicationRepository.operations[opId]?.status)
        assertEquals(PublicationState.CONFLICT, serviceOrderRepository.orders[orderId]?.publicationState)
    }

    @Test
    fun `successful update refreshes base snapshot for the next conditional publication`() = runBlocking {
        val orderId = UUID.randomUUID()
        serviceOrderRepository.orders[orderId] = StructuredServiceOrder(
            id = orderId,
            title = "OS Existente",
            clientName = "Cliente",
            unitName = "Unidade",
            occurrenceKey = RemoteOccurrenceKey("acct-1", "/cal/", "https://cloud.example.com/cal/e1.ics", null),
            baseSnapshot = RemoteBaseSnapshot(
                etag = "\"old-etag\"",
                rawIcs = "BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nUID:e1\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n",
                rawSummary = null,
                rawDescription = null
            )
        )
        val opId = UUID.randomUUID()
        val newIcs = "BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nUID:e1\r\nDESCRIPTION:Atualizada\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n"
        publicationRepository.operations[opId] = OutboxOperation(
            id = opId,
            orderId = orderId,
            action = OutboxAction.UPDATE,
            payloadIcs = newIcs,
            ifMatchEtag = "\"old-etag\"",
            status = OutboxStatus.PENDING
        )
        writeClient.nextUpdateOutcome = WriteOutcome.Updated(
            "https://cloud.example.com/cal/e1.ics",
            "\"new-etag\""
        )

        val outcome = coordinator.drainNext()

        assertTrue(outcome is DrainOutcome.Success)
        val saved = serviceOrderRepository.orders[orderId]
        assertEquals("\"new-etag\"", saved?.baseSnapshot?.etag)
        assertEquals(newIcs, saved?.baseSnapshot?.rawIcs)
        assertEquals("Atualizada", saved?.baseSnapshot?.rawDescription)
    }

    private class FakePublicationRepository : PublicationRepository {
        val operations = mutableMapOf<UUID, OutboxOperation>()

        override fun observeOperations(): Flow<List<OutboxOperation>> = flowOf(operations.values.toList())
        override suspend fun getOperationById(id: UUID) = operations[id]
        override suspend fun getLatestForOrder(orderId: UUID) = operations.values.lastOrNull { it.orderId == orderId }
        override suspend fun confirmPreview(snapshot: ConfirmedPreviewSnapshot): OutboxOperation {
            val op = OutboxOperation(
                orderId = snapshot.orderId,
                action = snapshot.action,
                payloadIcs = snapshot.rawIcsPayload,
                ifMatchEtag = snapshot.baseEtag,
                status = OutboxStatus.PENDING
            )
            operations[op.id] = op
            return op
        }
        override suspend fun claimNextEligible(nowMillis: Long, leaseDurationMillis: Long): OutboxOperation? {
            val op = operations.values.firstOrNull { it.status == OutboxStatus.PENDING } ?: return null
            val claimed = op.copy(status = OutboxStatus.SENDING)
            operations[op.id] = claimed
            return claimed
        }
        override suspend fun markSent(operationId: UUID, newEtag: String?, nowMillis: Long) {
            operations[operationId]?.let { operations[operationId] = it.copy(status = OutboxStatus.SENT) }
        }
        override suspend fun markConflict(operationId: UUID, reason: String, nowMillis: Long) {
            operations[operationId]?.let { operations[operationId] = it.copy(status = OutboxStatus.CONFLICT, lastError = reason) }
        }
        override suspend fun markFailed(operationId: UUID, reason: String, permanent: Boolean, nowMillis: Long) {
            val status = if (permanent) OutboxStatus.PERMANENT_FAILURE else OutboxStatus.PENDING
            operations[operationId]?.let { operations[operationId] = it.copy(status = status, lastError = reason) }
        }
        override suspend fun cancelPending(operationId: UUID) = operations.remove(operationId) != null
    }

    private class FakeServiceOrderRepository : ServiceOrderRepository {
        val orders = mutableMapOf<UUID, StructuredServiceOrder>()
        override fun getServiceOrders() = flowOf(orders.values.map { it.toLegacy() })
        override suspend fun getServiceOrderById(id: UUID) = orders[id]?.toLegacy()
        override suspend fun saveServiceOrder(serviceOrder: dev.claudiocodigo.nexo.domain.model.ServiceOrder) = Unit
        override suspend fun deleteServiceOrder(id: UUID) = Unit
        override fun observeStructuredOrders() = flowOf(orders.values.toList())
        override suspend fun getStructuredOrderById(id: UUID) = orders[id]
        override suspend fun saveStructuredOrder(order: StructuredServiceOrder) { orders[order.id] = order }
        override suspend fun createOrGetAttendance(
            key: RemoteOccurrenceKey, initialPreset: dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderPreset,
            title: String, clientName: String, unitName: String, rawSummary: String?, rawDescription: String?,
            rawIcs: String?, etag: String?, startMillis: Long?, endMillis: Long?
        ) = orders.values.first()
        override suspend fun getLinkedOrder(key: RemoteOccurrenceKey) = orders.values.firstOrNull { it.occurrenceKey == key }
    }

    private class FakeWriteClient : CalDavWriteClient {
        var lastCreateRequest: ConditionalCreate? = null
        var nextCreateOutcome: WriteOutcome = WriteOutcome.Created("/cal/new.ics", "\"etag\"")
        var nextUpdateOutcome: WriteOutcome = WriteOutcome.Updated("/cal/e1.ics", "\"etag\"")
        override suspend fun create(request: ConditionalCreate, credentials: CalDavCredentials): WriteOutcome {
            lastCreateRequest = request
            return nextCreateOutcome
        }
        override suspend fun update(request: ConditionalUpdate, credentials: CalDavCredentials) = nextUpdateOutcome
    }

    private class FakeCalendarSetupRepository : CalendarSetupRepository {
        private val selected = CalendarInfo(
            href = "/remote.php/dav/calendars/maria/agenda-real/",
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

    private class FakeCredentialStore : CredentialStore {
        override suspend fun saveAccount(server: String, user: String) = Unit
        override suspend fun readAccount() = AccountIdentity("https://cloud.example.com", "maria")
        override suspend fun saveAppPassword(password: CharArray) = Unit
        override suspend fun readAppPassword() = "secret".toCharArray()
        override suspend fun clear() = Unit
        override fun hasAccount() = flowOf(true)
        override fun observeAccount() = flowOf(AccountIdentity("https://cloud.example.com", "maria"))
    }
}
