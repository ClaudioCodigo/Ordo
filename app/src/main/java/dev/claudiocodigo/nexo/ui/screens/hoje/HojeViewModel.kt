package dev.claudiocodigo.nexo.ui.screens.hoje

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.claudiocodigo.nexo.domain.model.ServiceOrder
import dev.claudiocodigo.nexo.domain.model.ServiceOrderStatus
import dev.claudiocodigo.nexo.domain.repository.ServiceOrderRepository
import dev.claudiocodigo.nexo.domain.time.ClockProvider
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HojeViewModel @Inject constructor(
    private val repository: ServiceOrderRepository,
    private val clock: ClockProvider
) : ViewModel() {

    val uiState: StateFlow<HojeUiState> = repository.getServiceOrders()
        .map { orders ->
            HojeUiState.Success(
                emAndamento = orders.filter { it.status == ServiceOrderStatus.EM_ANDAMENTO },
                requerAtencao = orders.filter {
                    it.status == ServiceOrderStatus.PENDENTE && it.scheduledDate?.let { date -> date < clock.nowMillis() } == true
                },
                pendencias = orders.filter {
                    it.status == ServiceOrderStatus.PENDENTE && (it.scheduledDate == null || it.scheduledDate >= clock.nowMillis())
                }
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HojeUiState.Loading
        )
}

sealed interface HojeUiState {
    data object Loading : HojeUiState
    data class Success(
        val emAndamento: List<ServiceOrder>,
        val requerAtencao: List<ServiceOrder>,
        val pendencias: List<ServiceOrder>
    ) : HojeUiState
}
