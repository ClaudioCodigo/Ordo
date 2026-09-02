package dev.claudiocodigo.nexo.ui.screens.sincronizacoes

import android.content.Context
import dev.claudiocodigo.nexo.data.worker.PublicationScheduler
import dev.claudiocodigo.nexo.domain.model.ServiceOrder
import dev.claudiocodigo.nexo.domain.publication.ConfirmedPreviewSnapshot
import dev.claudiocodigo.nexo.domain.publication.OutboxAction
import dev.claudiocodigo.nexo.domain.publication.OutboxOperation
import dev.claudiocodigo.nexo.domain.publication.OutboxStatus
import dev.claudiocodigo.nexo.domain.publication.PublicationRepository
import dev.claudiocodigo.nexo.domain.repository.ServiceOrderRepository
import dev.claudiocodigo.nexo.domain.serviceorder.RemoteOccurrenceKey
import dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderPreset
import dev.claudiocodigo.nexo.domain.serviceorder.StructuredServiceOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.mockito.Mockito.mock
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class SyncCenterViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var publicationRepository: FakePublicationRepository
    private lateinit var serviceOrderRepository: FakeServiceOrderRepository
    private lateinit var scheduler: FakeScheduler
    private lateinit var viewModel: SyncCenterViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        publicationRepository = FakePublicationRepository()
        serviceOrderRepository = FakeServiceOrderRepository()
        scheduler = FakeScheduler()
        viewModel = SyncCenterViewModel(publicationRepository, serviceOrderRepository, scheduler)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun separatesOperationsIntoStatusBuckets() = runTest(testDispatcher) {
        val opPending = OutboxOperation(orderId = UUID.randomUUID(), action = OutboxAction.CREATE, payloadIcs = "", ifMatchEtag = null, status = OutboxStatus.PENDING)
        val opConflict = OutboxOperation(orderId = UUID.randomUUID(), action = OutboxAction.UPDATE, payloadIcs = "", ifMatchEtag = "\"etag\"", status = OutboxStatus.CONFLICT)
        val opSent = OutboxOperation(orderId = UUID.randomUUID(), action = OutboxAction.UPDATE, payloadIcs = "", ifMatchEtag = "\"etag\"", status = OutboxStatus.SENT)

        publicationRepository.opsFlow.value = listOf(opPending, opConflict, opSent)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is SyncCenterUiState.Success)
        val success = state as SyncCenterUiState.Success
        assertEquals(1, success.pending.size)
        assertEquals(1, success.conflicts.size)
        assertEquals(1, success.recentSent.size)
    }

    @Test
    fun cancelOperation_removesPendingOperation() = runTest(testDispatcher) {
        val opId = UUID.randomUUID()
        val op = OutboxOperation(id = opId, orderId = UUID.randomUUID(), action = OutboxAction.CREATE, payloadIcs = "", ifMatchEtag = null, status = OutboxStatus.PENDING)
        publicationRepository.opsFlow.value = listOf(op)

        viewModel.cancelOperation(opId)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(publicationRepository.canceledIds.contains(opId))
    }

    private class FakePublicationRepository : PublicationRepository {
        val opsFlow = MutableStateFlow<List<OutboxOperation>>(emptyList())
        val canceledIds = mutableListOf<UUID>()

        override fun observeOperations(): Flow<List<OutboxOperation>> = opsFlow
        override suspend fun getOperationById(id: UUID) = opsFlow.value.firstOrNull { it.id == id }
        override suspend fun getLatestForOrder(orderId: UUID) = opsFlow.value.lastOrNull { it.orderId == orderId }
        override suspend fun confirmPreview(snapshot: ConfirmedPreviewSnapshot, forceOverwrite: Boolean) = opsFlow.value.first()
        override suspend fun claimNextEligible(nowMillis: Long, leaseDurationMillis: Long) = null
        override suspend fun markSent(operationId: UUID, newEtag: String?, nowMillis: Long) = Unit
        override suspend fun markConflict(operationId: UUID, reason: String, nowMillis: Long) = Unit
        override suspend fun markFailed(operationId: UUID, reason: String, permanent: Boolean, nowMillis: Long) = Unit
        override suspend fun cancelPending(operationId: UUID): Boolean {
            canceledIds.add(operationId)
            opsFlow.value = opsFlow.value.filterNot { it.id == operationId }
            return true
        }
        override suspend fun cancelOperation(operationId: UUID): Boolean {
            canceledIds.add(operationId)
            opsFlow.value = opsFlow.value.filterNot { it.id == operationId }
            return true
        }
        override suspend fun cancelAllForOrder(orderId: UUID) {
            opsFlow.value = opsFlow.value.filterNot { it.orderId == orderId }
        }
    }

    private class FakeServiceOrderRepository : ServiceOrderRepository {
        override fun getServiceOrders() = flowOf(emptyList<ServiceOrder>())
        override suspend fun getServiceOrderById(id: UUID) = null
        override suspend fun saveServiceOrder(serviceOrder: ServiceOrder) = Unit
        override suspend fun deleteServiceOrder(id: UUID) = Unit
        override fun observeStructuredOrders() = flowOf(emptyList<StructuredServiceOrder>())
        override suspend fun getStructuredOrderById(id: UUID) = null
        override suspend fun saveStructuredOrder(order: StructuredServiceOrder) = Unit
        override suspend fun createOrGetAttendance(
            key: RemoteOccurrenceKey, initialPreset: ServiceOrderPreset, title: String,
            clientName: String, unitName: String, rawSummary: String?, rawDescription: String?,
            rawIcs: String?, etag: String?, startMillis: Long?, endMillis: Long?
        ) = StructuredServiceOrder(id = UUID.randomUUID(), title = "", clientName = "", unitName = "")
        override suspend fun getLinkedOrder(key: RemoteOccurrenceKey) = null
    }

    private class FakeScheduler : PublicationScheduler(mock(Context::class.java)) {
        var drainScheduled = false
        override fun scheduleDrain() {
            drainScheduled = true
        }
    }
}
