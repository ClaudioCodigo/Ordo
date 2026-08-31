package dev.claudiocodigo.nexo.ui.screens.resumo

import dev.claudiocodigo.nexo.domain.model.ServiceOrder
import dev.claudiocodigo.nexo.domain.repository.ServiceOrderRepository
import dev.claudiocodigo.nexo.domain.serviceorder.RemoteBaseSnapshot
import dev.claudiocodigo.nexo.domain.serviceorder.RemoteOccurrenceKey
import dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderPreset
import dev.claudiocodigo.nexo.domain.serviceorder.StructuredServiceOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class SummaryExtractionViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeServiceOrderRepository
    private lateinit var viewModel: SummaryExtractionViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeServiceOrderRepository()
        viewModel = SummaryExtractionViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun load_prefillsExistingAndExtractedFields() = runTest(testDispatcher) {
        val orderId = UUID.randomUUID()
        val rawSummary = "OS 15428 - Hospital Santa Casa - UTI 1 - Troca de Placa"
        val order = StructuredServiceOrder(
            id = orderId,
            title = "",
            clientName = "",
            unitName = "",
            baseSnapshot = RemoteBaseSnapshot(
                etag = "\"etag\"",
                rawIcs = "",
                rawSummary = rawSummary,
                rawDescription = ""
            )
        )
        repository.orders[orderId] = order

        viewModel.load(orderId)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("15428", state.externalId)
        assertEquals("Hospital Santa Casa", state.clientName)
        assertEquals("UTI 1", state.unitName)
        assertEquals("Troca de Placa", state.title)
    }

    @Test
    fun reExtractFromRaw_updatesFieldsWithNewInput() = runTest(testDispatcher) {
        val orderId = UUID.randomUUID()
        val order = StructuredServiceOrder(
            id = orderId,
            title = "Antigo",
            clientName = "Antigo",
            unitName = "Antigo"
        )
        repository.orders[orderId] = order

        viewModel.load(orderId)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onRawSummaryChange("OS: 9988 | Cli: Laboratório Alpha | Unid: Sala 10 | Tit: Manutenção")
        viewModel.reExtractFromRaw()

        val state = viewModel.uiState.value
        assertEquals("9988", state.externalId)
        assertEquals("Laboratório Alpha", state.clientName)
        assertEquals("Sala 10", state.unitName)
        assertEquals("Manutenção", state.title)
    }

    @Test
    fun applyChanges_savesUpdatedFieldsInRepository() = runTest(testDispatcher) {
        val orderId = UUID.randomUUID()
        val order = StructuredServiceOrder(
            id = orderId,
            title = "Título Inicial",
            clientName = "Cliente Inicial",
            unitName = "Unidade Inicial"
        )
        repository.orders[orderId] = order

        viewModel.load(orderId)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onExternalIdChange("12345")
        viewModel.onClientNameChange("Cliente Modificado")
        viewModel.onUnitNameChange("Setor TI")
        viewModel.onTitleChange("Título Final")

        var callbackCalled = false
        viewModel.applyChanges {
            callbackCalled = true
        }
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(callbackCalled)
        val saved = repository.orders[orderId]
        assertEquals("12345", saved?.externalId)
        assertEquals("Cliente Modificado", saved?.clientName)
        assertEquals("Setor TI", saved?.unitName)
        assertEquals("Título Final", saved?.title)
    }

    private class FakeServiceOrderRepository : ServiceOrderRepository {
        val orders = mutableMapOf<UUID, StructuredServiceOrder>()

        override fun getServiceOrders() = flowOf(orders.values.map { it.toLegacy() })
        override suspend fun getServiceOrderById(id: UUID) = orders[id]?.toLegacy()
        override suspend fun saveServiceOrder(serviceOrder: ServiceOrder) = Unit
        override suspend fun deleteServiceOrder(id: UUID) = Unit
        override fun observeStructuredOrders() = flowOf(orders.values.toList())
        override suspend fun getStructuredOrderById(id: UUID) = orders[id]
        override suspend fun saveStructuredOrder(order: StructuredServiceOrder) {
            orders[order.id] = order
        }
        override suspend fun createOrGetAttendance(
            key: RemoteOccurrenceKey, initialPreset: ServiceOrderPreset, title: String,
            clientName: String, unitName: String, rawSummary: String?, rawDescription: String?,
            rawIcs: String?, etag: String?, startMillis: Long?, endMillis: Long?
        ) = orders.values.first()
        override suspend fun getLinkedOrder(key: RemoteOccurrenceKey) = orders.values.firstOrNull { it.occurrenceKey == key }
    }
}
