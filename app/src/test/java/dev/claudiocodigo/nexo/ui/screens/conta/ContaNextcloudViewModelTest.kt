package dev.claudiocodigo.nexo.ui.screens.conta

import dev.claudiocodigo.nexo.domain.caldav.AccountIdentity
import dev.claudiocodigo.nexo.domain.caldav.CredentialStore
import dev.claudiocodigo.nexo.domain.repository.CalendarSetupRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations

/**
 * Verifies the credential flow. The password hand-off is validated indirectly:
 * reaching the [ContaUiState.Connected] state proves [CredentialStore.saveAppPassword]
 * was invoked and completed, since `store()` only transits to Connected on success.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContaNextcloudViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Mock
    private lateinit var credentialStore: CredentialStore

    @Mock
    private lateinit var setupRepository: CalendarSetupRepository

    private lateinit var viewModel: ContaNextcloudViewModel

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
        `when`(credentialStore.observeAccount()).thenReturn(flowOf(null))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun manualConnectStoresTheAccountAndPassword() = runTest {
        viewModel = ContaNextcloudViewModel(credentialStore, setupRepository)
        advanceUntilIdle()

        viewModel.connectManual("https://cloud.example.com", "maria", "secret")
        advanceUntilIdle()

        verify(credentialStore).saveAccount("https://cloud.example.com", "maria")
        // Reaching Connected proves saveAppPassword also ran (store() gates it).
        assertEquals(ContaUiState.Connected("https://cloud.example.com", "maria"), viewModel.uiState.value)
    }

    @Test
    fun httpServerUrlIsRejectedBeforeSaving() = runTest {
        viewModel = ContaNextcloudViewModel(credentialStore, setupRepository)
        advanceUntilIdle()

        viewModel.connectManual("http://cloud.example.com", "maria", "secret")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is ContaUiState.Disconnected)
        verify(credentialStore, never()).saveAccount(anyString(), anyString())
    }

    @Test
    fun qrPayloadConnectsAndNeverShowsThePassword() = runTest {
        viewModel = ContaNextcloudViewModel(credentialStore, setupRepository)
        advanceUntilIdle()

        viewModel.connectQr("nc://login/user:maria&password:secret&server:https://cloud.example.com")
        advanceUntilIdle()

        verify(credentialStore).saveAccount("https://cloud.example.com", "maria")
        assertEquals(ContaUiState.Connected("https://cloud.example.com", "maria"), viewModel.uiState.value)
    }

    @Test
    fun malformedQrYieldsAnErrorAndDoesNotSave() = runTest {
        viewModel = ContaNextcloudViewModel(credentialStore, setupRepository)
        advanceUntilIdle()

        viewModel.connectQr("not-a-qr")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is ContaUiState.Disconnected)
        verify(credentialStore, never()).saveAccount(anyString(), anyString())
    }

    @Test
    fun existingAccountLoadsAsConnected() = runTest {
        `when`(credentialStore.observeAccount())
            .thenReturn(flowOf(AccountIdentity("https://cloud.example.com", "maria")))
        viewModel = ContaNextcloudViewModel(credentialStore, setupRepository)
        advanceUntilIdle()

        assertEquals(ContaUiState.Connected("https://cloud.example.com", "maria"), viewModel.uiState.value)
    }

    @Test
    fun disconnectClearsTheCredentialStore() = runTest {
        viewModel = ContaNextcloudViewModel(credentialStore, setupRepository)
        advanceUntilIdle()

        viewModel.disconnect()
        advanceUntilIdle()

        verify(credentialStore).clear()
        verify(setupRepository).disconnectLocal()
        assertEquals(ContaUiState.Disconnected(), viewModel.uiState.value)
    }
}
