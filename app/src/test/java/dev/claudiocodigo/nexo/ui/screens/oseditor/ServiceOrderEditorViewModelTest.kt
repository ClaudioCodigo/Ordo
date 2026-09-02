package dev.claudiocodigo.nexo.ui.screens.oseditor

import dev.claudiocodigo.nexo.data.preferences.RecentServiceOrderPreferences
import dev.claudiocodigo.nexo.domain.model.ServiceOrder
import dev.claudiocodigo.nexo.domain.repository.ServiceOrderRepository
import dev.claudiocodigo.nexo.domain.serviceorder.RemoteOccurrenceKey
import dev.claudiocodigo.nexo.domain.serviceorder.ConclusionState
import dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderPreset
import dev.claudiocodigo.nexo.domain.serviceorder.StructuredServiceOrder
import dev.claudiocodigo.nexo.domain.time.ClockProvider
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class ServiceOrderEditorViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeServiceOrderRepository
    private lateinit var preferences: FakeRecentPreferences
    private lateinit var viewModel: ServiceOrderEditorViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        preferences = FakeRecentPreferences()
        repository = FakeServiceOrderRepository()
        viewModel = ServiceOrderEditorViewModel(
            repository = repository,
            preferences = preferences,
            clock = object : ClockProvider { override fun nowMillis() = 1000L }
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadOrder_loadsExistingStructuredOrderIntoState() = runTest(testDispatcher) {
        val orderId = UUID.randomUUID()
        val order = StructuredServiceOrder(
            id = orderId,
            externalId = "15428",
            title = "Manutenção",
            clientName = "Hospital X",
            unitName = "Centro Cirúrgico",
            originalDemand = "Nobreak desligando",
            preset = ServiceOrderPreset.DIAGNOSTICO_CORRECAO
        )
        repository.orders[orderId] = order

        viewModel.loadOrder(orderId)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("15428", state.externalId)
        assertEquals("Hospital X", state.clientName)
        assertEquals("Nobreak desligando", state.originalDemand)
        assertEquals(ServiceOrderPreset.DIAGNOSTICO_CORRECAO, state.preset)
        assertEquals(EditorStep.IDENTIFICACAO, state.currentStep)
    }

    @Test
    fun addUpdate_appendsUpdateWithChronologicalOrdering() = runTest(testDispatcher) {
        viewModel.addUpdate("Primeira observação")
        viewModel.addUpdate("Segunda observação")

        val state = viewModel.state.value
        assertEquals(2, state.updates.size)
        assertEquals("Primeira observação", state.updates[0].text)
        assertEquals("Segunda observação", state.updates[1].text)
        assertEquals(1, state.updates[0].sequenceOrder)
        assertEquals(2, state.updates[1].sequenceOrder)
    }

    @Test
    fun validateForPublication_blocksWhenMandatoryFieldsAreBlank() = runTest(testDispatcher) {
        val validInitially = viewModel.validateForPublication()
        assertFalse(validInitially)
        assertNotNull(viewModel.state.value.validationError)

        viewModel.onTitleChange("Título válido")
        viewModel.onClientNameChange("Cliente A")
        viewModel.onUnitNameChange("Unidade B")
        viewModel.onTechnicianChange("Claudio")
        viewModel.onCategoryChange("CFTV")
        viewModel.onDemandChange("Demanda explicada")

        val validAfter = viewModel.validateForPublication()
        assertTrue(validAfter)
    }

    @Test
    fun saveBeforePublication_persistsLatestEditBeforeNavigating() = runTest(testDispatcher) {
        val orderId = UUID.randomUUID()
        repository.orders[orderId] = StructuredServiceOrder(
            id = orderId,
            title = "Título antigo",
            clientName = "Cliente",
            unitName = "Unidade",
            originalDemand = "Demanda"
        )
        viewModel.loadOrder(orderId)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onTitleChange("Título final ainda não salvo")
        var titleSeenAfterNavigation: String? = null

        viewModel.saveBeforePublication {
            titleSeenAfterNavigation = repository.orders[it]?.title
        }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Título final ainda não salvo", titleSeenAfterNavigation)
    }

    @Test
    fun stepNavigation_provisionalOrderHasTwoSteps() = runTest(testDispatcher) {
        assertEquals(listOf(EditorStep.IDENTIFICACAO, EditorStep.DEMANDA), viewModel.state.value.activeSteps)
        assertEquals(EditorStep.IDENTIFICACAO, viewModel.state.value.currentStep)

        viewModel.nextStep()
        assertEquals(EditorStep.IDENTIFICACAO, viewModel.state.value.currentStep)
        assertEquals("Informe o título do atendimento para continuar.", viewModel.state.value.validationError)

        viewModel.onTitleChange("Título")
        viewModel.onClientNameChange("Cliente")
        viewModel.onUnitNameChange("Local")
        viewModel.nextStep()
        assertEquals(EditorStep.DEMANDA, viewModel.state.value.currentStep)

        // At last step (DEMANDA for provisional), nextStep stays at DEMANDA
        viewModel.nextStep()
        assertEquals(EditorStep.DEMANDA, viewModel.state.value.currentStep)

        viewModel.previousStep()
        assertEquals(EditorStep.IDENTIFICACAO, viewModel.state.value.currentStep)
    }

    @Test
    fun stepNavigation_linkedResolutionFlowHasThreeSteps() = runTest(testDispatcher) {
        val orderId = UUID.randomUUID()
        repository.orders[orderId] = StructuredServiceOrder(
            id = orderId,
            occurrenceKey = RemoteOccurrenceKey("acct", "/calendar/", "/calendar/event.ics"),
            title = "Atendimento",
            clientName = "Cliente",
            unitName = "Local",
            originalDemand = "Demanda",
            flow = dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderFlow.RESOLUTION
        )
        viewModel.loadOrder(orderId)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(EditorStep.IDENTIFICACAO, EditorStep.DEMANDA, EditorStep.CONCLUSAO), viewModel.state.value.activeSteps)
        assertEquals(EditorStep.IDENTIFICACAO, viewModel.state.value.currentStep)

        viewModel.nextStep()
        assertEquals(EditorStep.DEMANDA, viewModel.state.value.currentStep)

        viewModel.nextStep()
        assertEquals(EditorStep.CONCLUSAO, viewModel.state.value.currentStep)

        viewModel.nextStep()
        assertEquals(EditorStep.CONCLUSAO, viewModel.state.value.currentStep)

        viewModel.previousStep()
        assertEquals(EditorStep.DEMANDA, viewModel.state.value.currentStep)

        viewModel.previousStep()
        assertEquals(EditorStep.IDENTIFICACAO, viewModel.state.value.currentStep)
    }

    @Test
    fun stepNavigation_linkedUpdateFlowHasTwoSteps() = runTest(testDispatcher) {
        val orderId = UUID.randomUUID()
        repository.orders[orderId] = StructuredServiceOrder(
            id = orderId,
            occurrenceKey = RemoteOccurrenceKey("acct", "/calendar/", "/calendar/event.ics"),
            title = "Atendimento",
            clientName = "Cliente",
            unitName = "Local",
            originalDemand = "Demanda",
            flow = dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderFlow.UPDATE
        )
        viewModel.loadOrder(orderId)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(EditorStep.IDENTIFICACAO, EditorStep.ATUALIZACAO), viewModel.state.value.activeSteps)
        assertEquals(EditorStep.IDENTIFICACAO, viewModel.state.value.currentStep)

        viewModel.nextStep()
        assertEquals(EditorStep.ATUALIZACAO, viewModel.state.value.currentStep)

        viewModel.previousStep()
        assertEquals(EditorStep.IDENTIFICACAO, viewModel.state.value.currentStep)
    }

    @Test
    fun onConclusionStateChange_updatesConclusionStateAndSetsCompletedStatus() = runTest(testDispatcher) {
        viewModel.onConclusionStateChange(ConclusionState.CONCLUIDO)
        assertEquals(ConclusionState.CONCLUIDO, viewModel.state.value.conclusionState)
        assertEquals(dev.claudiocodigo.nexo.domain.model.ServiceOrderStatus.CONCLUIDA, viewModel.state.value.status)
    }

    @Test
    fun nonCompletedConclusionUsesUpdateForLinkedOrderAndPublicationValidation() = runTest(testDispatcher) {
        val orderId = UUID.randomUUID()
        repository.orders[orderId] = StructuredServiceOrder(
            id = orderId,
            occurrenceKey = RemoteOccurrenceKey("acct", "/calendar/", "/calendar/event.ics"),
            title = "Atendimento",
            clientName = "Cliente",
            unitName = "Local",
            originalDemand = "Demanda",
            status = dev.claudiocodigo.nexo.domain.model.ServiceOrderStatus.CONCLUIDA,
            conclusionState = ConclusionState.CONCLUIDO
        )
        viewModel.loadOrder(orderId)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onConclusionStateChange(ConclusionState.NAO_CONCLUIDO)

        assertEquals(dev.claudiocodigo.nexo.domain.model.ServiceOrderStatus.EM_ANDAMENTO, viewModel.state.value.status)
        assertEquals(dev.claudiocodigo.nexo.domain.publication.OutboxAction.UPDATE, viewModel.state.value.applicableRemoteAction)
        assertTrue(viewModel.validateForPublication())
    }

    @Test
    fun nonCompletedConclusionUsesCreateForProvisionalOrder() = runTest(testDispatcher) {
        viewModel.onConclusionStateChange(ConclusionState.NAO_CONCLUIDO)

        assertEquals(dev.claudiocodigo.nexo.domain.publication.OutboxAction.CREATE, viewModel.state.value.applicableRemoteAction)
        assertEquals(dev.claudiocodigo.nexo.domain.model.ServiceOrderStatus.EM_ANDAMENTO, viewModel.state.value.status)
    }

    @Test
    fun directStepClickCannotBypassRequiredEarlierSteps() = runTest(testDispatcher) {
        val orderId = UUID.randomUUID()
        repository.orders[orderId] = StructuredServiceOrder(
            id = orderId,
            occurrenceKey = RemoteOccurrenceKey("acct", "/calendar/", "/calendar/event.ics"),
            title = "",
            clientName = "",
            unitName = "",
            originalDemand = "",
            flow = dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderFlow.RESOLUTION
        )
        viewModel.loadOrder(orderId)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.goToStep(EditorStep.CONCLUSAO)
        assertEquals(EditorStep.IDENTIFICACAO, viewModel.state.value.currentStep)

        viewModel.onTitleChange("Título")
        viewModel.onClientNameChange("Cliente")
        viewModel.onUnitNameChange("Local")
        viewModel.goToStep(EditorStep.CONCLUSAO)
        assertEquals(EditorStep.DEMANDA, viewModel.state.value.currentStep)

        viewModel.onDemandChange("Demanda")
        viewModel.goToStep(EditorStep.CONCLUSAO)
        assertEquals(EditorStep.CONCLUSAO, viewModel.state.value.currentStep)
    }

    private class FakeRecentPreferences : RecentServiceOrderPreferences {
        val techFlow = MutableStateFlow<String?>(null)
        val clientFlow = MutableStateFlow<String?>(null)
        val unitFlow = MutableStateFlow<String?>(null)
        val catFlow = MutableStateFlow<String?>(null)

        override val recentTechnician: Flow<String?> = techFlow
        override val recentClient: Flow<String?> = clientFlow
        override val recentUnit: Flow<String?> = unitFlow
        override val recentCategory: Flow<String?> = catFlow

        override suspend fun saveRecentSelections(technician: String?, client: String?, unit: String?, category: String?) {
            techFlow.value = technician
            clientFlow.value = client
            unitFlow.value = unit
            catFlow.value = category
        }
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
}
