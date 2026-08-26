package dev.claudiocodigo.nexo.ui.screens.hoje

import dev.claudiocodigo.nexo.domain.model.ServiceOrder
import dev.claudiocodigo.nexo.domain.model.ServiceOrderStatus
import dev.claudiocodigo.nexo.domain.repository.ServiceOrderRepository
import dev.claudiocodigo.nexo.domain.time.ClockProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class HojeViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

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
        val viewModel = HojeViewModel(FlowRepository(orders), FixedClock(now))

        val collection = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        val state = viewModel.uiState.value as HojeUiState.Success

        assertEquals(1, state.emAndamento.size)
        assertEquals(1, state.requerAtencao.size)
        assertEquals(2, state.pendencias.size)
        assertEquals(0, state.requerAtencao.count { it.scheduledDate == null })
        collection.cancel()
    }

    private fun order(status: ServiceOrderStatus, date: Long? = null) = ServiceOrder(
        id = UUID.randomUUID(), title = status.name, clientName = "Cliente", unitName = "Local",
        status = status, scheduledDate = date
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
