package dev.claudiocodigo.nexo.ui.screens.descoberta

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.claudiocodigo.nexo.domain.caldav.CalDavCredentials
import dev.claudiocodigo.nexo.domain.caldav.CalDavDiscoveryClient
import dev.claudiocodigo.nexo.domain.caldav.CalendarInfo
import dev.claudiocodigo.nexo.domain.caldav.CredentialStore
import dev.claudiocodigo.nexo.domain.caldav.DiscoveryResult
import dev.claudiocodigo.nexo.domain.repository.CalendarSetupRepository
import dev.claudiocodigo.nexo.data.worker.SyncScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Discovers and selects the work calendar (AUT-05). */
@HiltViewModel
class DescobertaAgendaViewModel @Inject constructor(
    private val credentialStore: CredentialStore,
    private val discoveryClient: CalDavDiscoveryClient,
    private val setupRepository: CalendarSetupRepository,
    private val syncScheduler: SyncScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow<DescobertaUiState>(DescobertaUiState.Loading)
    val uiState: StateFlow<DescobertaUiState> = _uiState.asStateFlow()

    private var accountId: String? = null

    init { discover() }

    fun discover() {
        _uiState.value = DescobertaUiState.Loading
        viewModelScope.launch {
            val identity = credentialStore.readAccount()
            val password = credentialStore.readAppPassword()
            if (identity == null || password == null) {
                _uiState.value = DescobertaUiState.Error("Conta ainda não configurada.")
                return@launch
            }

            val credentials = CalDavCredentials(identity.server, identity.user, password)
            password.fill('\u0000')

            val result = try {
                discoveryClient.discover(credentials)
            } catch (e: Exception) {
                _uiState.value = DescobertaUiState.Error(e.message ?: "Falha na descoberta")
                return@launch
            }

            when (result) {
                is DiscoveryResult.Success -> {
                    val id = setupRepository.ensureAccount(identity.server, identity.user)
                    accountId = id
                    setupRepository.saveCalendars(id, result.calendars)
                    val selectedHref = setupRepository.observeSelectedCalendar().first()?.href
                    _uiState.value = DescobertaUiState.Success(
                        calendars = result.calendars,
                        selectedHref = selectedHref
                    )
                }
                is DiscoveryResult.Failure ->
                    _uiState.value = DescobertaUiState.Error(failureMessage(result.kind, result.message))
            }
        }
    }

    fun choose(href: String) {
        val current = _uiState.value as? DescobertaUiState.Success ?: return
        _uiState.value = current.copy(selectedHref = href, selectionError = null)
    }

    fun confirmSelection(onSelected: () -> Unit) {
        val id = accountId ?: return
        val current = _uiState.value as? DescobertaUiState.Success ?: return
        val href = current.selectedHref ?: return
        if (current.isSaving) return

        _uiState.value = current.copy(isSaving = true, selectionError = null)
        viewModelScope.launch {
            try {
                setupRepository.selectWorkingCalendar(id, href)
                syncScheduler.schedulePeriodic()
                syncScheduler.syncNow()
                onSelected()
            } catch (e: Exception) {
                val latest = _uiState.value as? DescobertaUiState.Success ?: return@launch
                _uiState.value = latest.copy(
                    isSaving = false,
                    selectionError = e.message ?: "Não foi possível selecionar a agenda."
                )
            }
        }
    }

    private fun failureMessage(kind: dev.claudiocodigo.nexo.domain.caldav.FailureKind, message: String): String =
        when (kind) {
            dev.claudiocodigo.nexo.domain.caldav.FailureKind.UNAUTHORIZED -> "Autenticação falhou. Verifique usuário e senha de aplicativo."
            dev.claudiocodigo.nexo.domain.caldav.FailureKind.FORBIDDEN -> "Acesso negado pelo servidor."
            dev.claudiocodigo.nexo.domain.caldav.FailureKind.NOT_FOUND -> "Servidor ou agenda não encontrados."
            dev.claudiocodigo.nexo.domain.caldav.FailureKind.TLS_INVALID -> "Certificado TLS inválido. Verifique o servidor."
            dev.claudiocodigo.nexo.domain.caldav.FailureKind.REDIRECT_INSECURE -> "Redirecionamento inseguro detectado."
            else -> message.ifBlank { "Não foi possível descobrir as agendas." }
        }
}

sealed interface DescobertaUiState {
    data object Loading : DescobertaUiState
    data class Success(
        val calendars: List<CalendarInfo>,
        val selectedHref: String? = null,
        val isSaving: Boolean = false,
        val selectionError: String? = null
    ) : DescobertaUiState
    data class Error(val message: String) : DescobertaUiState
}
