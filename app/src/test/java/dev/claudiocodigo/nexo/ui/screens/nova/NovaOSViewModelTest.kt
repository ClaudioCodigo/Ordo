package dev.claudiocodigo.nexo.ui.screens.nova

import androidx.lifecycle.SavedStateHandle
import dev.claudiocodigo.nexo.domain.model.ServiceOrder
import dev.claudiocodigo.nexo.domain.repository.ServiceOrderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class NovaOSViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `empty form is not saved`() = runTest {
        val repository = RecordingRepository()
        val viewModel = NovaOSViewModel(repository, SavedStateHandle())

        viewModel.save()
        advanceUntilIdle()

        assertEquals(0, repository.saved.size)
        assertNotNull(viewModel.form.value.validationError)
    }

    @Test
    fun `first meaningful input is autosaved as a partial draft`() = runTest {
        val repository = RecordingRepository()
        val viewModel = NovaOSViewModel(repository, SavedStateHandle())

        viewModel.onTitleChange("Rascunho de atendimento")
        advanceUntilIdle()

        assertEquals(1, repository.saved.size)
        assertEquals("Rascunho de atendimento", repository.saved.single().title)
        assertEquals("", repository.saved.single().clientName)
        assertEquals(NovaDraftSaveState.SavedLocally, viewModel.form.value.saveState)
    }

    @Test
    fun `scheduled date is recovered from saved state`() = runTest {
        val date = 1_735_689_600_000L
        val viewModel = NovaOSViewModel(
            RecordingRepository(),
            SavedStateHandle(mapOf("nova_os_date" to date))
        )

        assertEquals(date, viewModel.form.value.scheduledDate)
    }

    @Test
    fun `meaningful restored state is autosaved without new typing`() = runTest {
        val repository = RecordingRepository()
        val viewModel = NovaOSViewModel(
            repository,
            SavedStateHandle(mapOf("nova_os_title" to "Rascunho restaurado"))
        )

        advanceUntilIdle()

        assertEquals(1, repository.saved.size)
        assertEquals("Rascunho restaurado", repository.saved.single().title)
        assertEquals(NovaDraftSaveState.SavedLocally, viewModel.form.value.saveState)
    }

    @Test
    fun `valid form creates local order with internal uuid and optional number`() = runTest {
        val repository = RecordingRepository()
        val viewModel = NovaOSViewModel(repository, SavedStateHandle())

        viewModel.onTitleChange("Troca de bateria")
        viewModel.onClientChange("Empresa")
        viewModel.onUnitChange("Sala de TI")
        viewModel.onDescriptionChange("Substituir bateria do nobreak")
        viewModel.save()
        advanceUntilIdle()

        val saved = repository.saved.single()
        assertEquals(viewModel.form.value.internalId, saved.id)
        assertEquals(null, saved.externalId)
        assertEquals("Sala de TI", saved.unitName)
    }

    private class RecordingRepository : ServiceOrderRepository {
        private val orders = MutableStateFlow<List<ServiceOrder>>(emptyList())
        val saved = mutableListOf<ServiceOrder>()

        override fun getServiceOrders(): Flow<List<ServiceOrder>> = orders
        override suspend fun getServiceOrderById(id: UUID): ServiceOrder? = orders.value.find { it.id == id }
        override suspend fun saveServiceOrder(serviceOrder: ServiceOrder) {
            saved += serviceOrder
            orders.value = orders.value.filterNot { it.id == serviceOrder.id } + serviceOrder
        }
        override suspend fun deleteServiceOrder(id: UUID) = Unit
    }
}
