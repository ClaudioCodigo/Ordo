package dev.claudiocodigo.nexo.ui.screens.detalhes

import androidx.lifecycle.SavedStateHandle
import dev.claudiocodigo.nexo.domain.model.ServiceOrder
import dev.claudiocodigo.nexo.domain.model.ServiceOrderStatus
import dev.claudiocodigo.nexo.domain.repository.ServiceOrderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class DetalhesViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `edits are autosaved from the same draft shown on screen`() = runTest {
        val original = order()
        val repository = RecordingRepository(original)
        val viewModel = DetalhesViewModel(repository, SavedStateHandle())

        viewModel.loadServiceOrder(original.id.toString())
        advanceUntilIdle()
        viewModel.updateTitle("Título editado")
        viewModel.updateDescription("Descrição preservada")
        advanceUntilIdle()

        assertEquals("Título editado", repository.saved.single().title)
        assertEquals("Descrição preservada", repository.saved.single().description)
        assertTrue((viewModel.uiState.value as DetalhesUiState.Success).saveState is DetalhesSaveState.SavedLocally)
    }

    @Test
    fun `finish and reopen are local status changes`() = runTest {
        val original = order(status = ServiceOrderStatus.EM_ANDAMENTO)
        val repository = RecordingRepository(original)
        val viewModel = DetalhesViewModel(repository, SavedStateHandle())
        viewModel.loadServiceOrder(original.id.toString())
        advanceUntilIdle()

        viewModel.requestFinish()
        viewModel.confirmPendingAction()
        advanceUntilIdle()
        assertEquals(ServiceOrderStatus.CONCLUIDA, repository.saved.last().status)

        viewModel.requestReopen()
        viewModel.confirmPendingAction()
        advanceUntilIdle()
        assertEquals(ServiceOrderStatus.EM_ANDAMENTO, repository.saved.last().status)
    }

    @Test
    fun `newer edit is not overwritten by an in-flight save`() = runTest {
        val original = order()
        val repository = BlockingRepository(original)
        val viewModel = DetalhesViewModel(repository, SavedStateHandle())
        viewModel.loadServiceOrder(original.id.toString())
        advanceUntilIdle()

        viewModel.updateTitle("A")
        viewModel.saveDraft()
        runCurrent()
        repository.started.await()

        viewModel.updateTitle("B")
        repository.release.complete(Unit)
        advanceUntilIdle()

        assertEquals("B", (viewModel.uiState.value as DetalhesUiState.Success).os.title)
        assertEquals("B", repository.saved.last().title)
    }

    @Test
    fun `saved state divergence is restored and then persisted`() = runTest {
        val original = order()
        val handle = SavedStateHandle(
            mapOf("draft_${original.id}_title" to "Título recuperado")
        )
        val repository = RecordingRepository(original)
        val viewModel = DetalhesViewModel(repository, handle)

        viewModel.loadServiceOrder(original.id.toString())
        advanceUntilIdle()

        val state = viewModel.uiState.value as DetalhesUiState.Success
        assertEquals("Título recuperado", state.os.title)
        assertTrue(state.saveState is DetalhesSaveState.SavedLocally)
        assertEquals("Título recuperado", repository.saved.last().title)
    }

    private fun order(status: ServiceOrderStatus = ServiceOrderStatus.PENDENTE) = ServiceOrder(
        id = UUID.randomUUID(),
        title = "Título original",
        description = "Descrição original",
        clientName = "Cliente",
        unitName = "Local",
        status = status
    )

    private open class RecordingRepository(initial: ServiceOrder) : ServiceOrderRepository {
        private val orders = MutableStateFlow(listOf(initial))
        val saved = mutableListOf<ServiceOrder>()

        override fun getServiceOrders(): Flow<List<ServiceOrder>> = orders
        override suspend fun getServiceOrderById(id: UUID): ServiceOrder? = orders.value.find { it.id == id }
        override suspend fun saveServiceOrder(serviceOrder: ServiceOrder) {
            saved += serviceOrder
            orders.value = listOf(serviceOrder)
        }
        override suspend fun deleteServiceOrder(id: UUID) = Unit
    }

    private class BlockingRepository(initial: ServiceOrder) : RecordingRepository(initial) {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        override suspend fun saveServiceOrder(serviceOrder: ServiceOrder) {
            started.complete(Unit)
            release.await()
            super.saveServiceOrder(serviceOrder)
        }
    }
}
