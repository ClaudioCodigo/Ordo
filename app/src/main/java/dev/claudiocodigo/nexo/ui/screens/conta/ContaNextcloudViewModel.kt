package dev.claudiocodigo.nexo.ui.screens.conta

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.claudiocodigo.nexo.data.worker.SyncScheduler
import dev.claudiocodigo.nexo.domain.caldav.CalDavCredentials
import dev.claudiocodigo.nexo.domain.caldav.CalDavDiscoveryClient
import dev.claudiocodigo.nexo.domain.caldav.CredentialStore
import dev.claudiocodigo.nexo.domain.caldav.DiscoveryResult
import dev.claudiocodigo.nexo.domain.caldav.NextcloudQrParser
import dev.claudiocodigo.nexo.domain.caldav.ServerUrlNormalizer
import dev.claudiocodigo.nexo.domain.repository.CalendarSetupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Local credential flow for the Nextcloud account (AUT-01, AUT-02). */
@HiltViewModel
class ContaNextcloudViewModel @Inject constructor(
    private val credentialStore: CredentialStore,
    private val setupRepository: CalendarSetupRepository,
    private val discoveryClient: CalDavDiscoveryClient? = null,
    private val syncScheduler: SyncScheduler? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow<ContaUiState>(ContaUiState.Loading)
    val uiState: StateFlow<ContaUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val account = credentialStore.observeAccount().first()
            _uiState.value = account?.let { ContaUiState.Connected(it.server, it.user) }
                ?: ContaUiState.Disconnected()
        }
    }

    /** Submits manual server/user/app-password. */
    fun connectManual(server: String, user: String, password: String) {
        validateAndStore(server.trim(), user.trim(), password.toCharArray())
    }

    /** Submits a pasted/scanned login QR payload. */
    fun connectQr(rawQr: String) {
        val parsed = runCatching { NextcloudQrParser.parse(rawQr) }.getOrElse {
            _uiState.value = ContaUiState.Disconnected(error = it.message ?: "QR inválido")
            return
        }

        val server = ServerUrlNormalizer.normalize(parsed.server)
        if (server is ServerUrlNormalizer.Result.Error) {
            parsed.wipe()
            _uiState.value = ContaUiState.Disconnected(error = server.reason)
            return
        }
        val normalized = (server as ServerUrlNormalizer.Result.Ok).server
        val password = parsed.password()
        parsed.wipe()
        store(normalized, parsed.user, password)
    }

    private fun validateAndStore(server: String, user: String, password: CharArray) {
        if (user.isEmpty()) {
            password.fill('\u0000')
            _uiState.value = ContaUiState.Disconnected(error = "Usuário não informado")
            return
        }
        val normalized = ServerUrlNormalizer.normalize(server)
        if (normalized is ServerUrlNormalizer.Result.Error) {
            password.fill('\u0000')
            _uiState.value = ContaUiState.Disconnected(error = normalized.reason)
            return
        }
        store((normalized as ServerUrlNormalizer.Result.Ok).server, user, password)
    }

    private fun store(normalizedServer: String, user: String, password: CharArray) {
        _uiState.value = ContaUiState.Validating(normalizedServer, user)
        val passwordCopy = password.copyOf()
        viewModelScope.launch {
            runCatching {
                credentialStore.saveAccount(normalizedServer, user)
                credentialStore.saveAppPassword(password)
            }.onSuccess {
                _uiState.value = ContaUiState.Connected(normalizedServer, user)
                if (discoveryClient != null) {
                    val accountId = setupRepository.ensureAccount(normalizedServer, user)
                    val credentials = CalDavCredentials(normalizedServer, user, passwordCopy)
                    val discovery = runCatching { discoveryClient.discover(credentials) }.getOrNull()
                    if (discovery is DiscoveryResult.Success && discovery.calendars.isNotEmpty()) {
                        setupRepository.saveCalendars(accountId, discovery.calendars)
                        syncScheduler?.schedulePeriodic()
                        syncScheduler?.syncNow()
                    }
                }
            }.onFailure {
                _uiState.value = ContaUiState.Disconnected(
                    error = it.message ?: "Não foi possível salvar a conta"
                )
            }
            password.fill('\u0000')
            passwordCopy.fill('\u0000')
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            runCatching {
                // Clear the secret + account identity and all remote cache, but
                // never touch independent local drafts (AUT-06).
                credentialStore.clear()
                setupRepository.disconnectLocal()
            }
            _uiState.value = ContaUiState.Disconnected()
        }
    }
}

sealed interface ContaUiState {
    data object Loading : ContaUiState
    data class Disconnected(val error: String? = null) : ContaUiState
    data class Validating(val server: String, val user: String) : ContaUiState
    data class Connected(val server: String, val user: String) : ContaUiState
}
