package dev.claudiocodigo.nexo.ui.screens.remoto

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.claudiocodigo.nexo.domain.caldav.RemoteEvent
import dev.claudiocodigo.nexo.domain.repository.CalendarRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RemoteEventDetailViewModel @Inject constructor(
    private val calendarRepository: CalendarRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<RemoteEventDetailUiState>(RemoteEventDetailUiState.Loading)
    val uiState: StateFlow<RemoteEventDetailUiState> = _uiState.asStateFlow()

    fun load(accountId: String, calendarHref: String, href: String) {
        if (_uiState.value is RemoteEventDetailUiState.Success) return
        viewModelScope.launch {
            _uiState.value = calendarRepository.getEvent(accountId, calendarHref, href)
                ?.let { RemoteEventDetailUiState.Success(it) }
                ?: RemoteEventDetailUiState.Error("Evento não encontrado no cache local")
        }
    }
}

sealed interface RemoteEventDetailUiState {
    data object Loading : RemoteEventDetailUiState
    data class Success(val event: RemoteEvent) : RemoteEventDetailUiState
    data class Error(val message: String) : RemoteEventDetailUiState
}
