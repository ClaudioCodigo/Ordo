package dev.claudiocodigo.nexo.ui.screens.agenda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.claudiocodigo.nexo.domain.model.ServiceOrder
import dev.claudiocodigo.nexo.domain.repository.ServiceOrderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AgendaViewModel @Inject constructor(
    private val repository: ServiceOrderRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _deleteError = MutableStateFlow<String?>(null)
    val deleteError: StateFlow<String?> = _deleteError

    val uiState: StateFlow<AgendaUiState> = repository.getServiceOrders()
        .combine(_searchQuery) { orders, query ->
            val filtered = if (query.isBlank()) {
                orders
            } else {
                orders.filter {
                    it.title.contains(query, ignoreCase = true) ||
                    it.clientName.contains(query, ignoreCase = true) ||
                    (it.externalId?.contains(query, ignoreCase = true) ?: false)
                }
            }

            val grouped = filtered.groupBy { os ->
                os.scheduledDate?.let {
                    SimpleDateFormat("dd 'de' MMMM", Locale.forLanguageTag("pt-BR")).format(Date(it))
                } ?: "Sem data definida"
            }

            AgendaUiState.Success(grouped)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AgendaUiState.Loading
        )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun deleteLocalDraft(id: UUID) {
        viewModelScope.launch {
            runCatching { repository.deleteServiceOrder(id) }
                .onSuccess { _deleteError.value = null }
                .onFailure {
                    _deleteError.value = it.message ?: "Não foi possível excluir o rascunho local."
                }
        }
    }

    fun clearDeleteError() {
        _deleteError.value = null
    }
}

sealed interface AgendaUiState {
    data object Loading : AgendaUiState
    data class Success(val groupedOrders: Map<String, List<ServiceOrder>>) : AgendaUiState
}
