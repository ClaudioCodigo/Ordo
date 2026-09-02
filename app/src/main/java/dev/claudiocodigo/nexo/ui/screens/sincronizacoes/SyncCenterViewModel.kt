package dev.claudiocodigo.nexo.ui.screens.sincronizacoes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.claudiocodigo.nexo.data.worker.PublicationScheduler
import dev.claudiocodigo.nexo.domain.publication.OutboxAction
import dev.claudiocodigo.nexo.domain.publication.OutboxOperation
import dev.claudiocodigo.nexo.domain.publication.OutboxStatus
import dev.claudiocodigo.nexo.domain.publication.PublicationRepository
import dev.claudiocodigo.nexo.domain.repository.ServiceOrderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class SyncCenterItem(
    val operation: OutboxOperation,
    val orderTitle: String,
    val clientName: String,
    val externalId: String?
)

sealed interface SyncCenterUiState {
    data object Loading : SyncCenterUiState
    data class Success(
        val pending: List<SyncCenterItem>,
        val sending: List<SyncCenterItem>,
        val conflicts: List<SyncCenterItem>,
        val failed: List<SyncCenterItem>,
        val recentSent: List<SyncCenterItem>
    ) : SyncCenterUiState
}

@HiltViewModel
class SyncCenterViewModel @Inject constructor(
    private val publicationRepository: PublicationRepository,
    private val serviceOrderRepository: ServiceOrderRepository,
    private val scheduler: PublicationScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow<SyncCenterUiState>(SyncCenterUiState.Loading)
    val uiState: StateFlow<SyncCenterUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                publicationRepository.observeOperations(),
                serviceOrderRepository.observeStructuredOrders()
            ) { operations, orders ->
                val ordersMap = orders.associateBy { it.id }

                val items = operations.map { op ->
                    val order = ordersMap[op.orderId]
                    SyncCenterItem(
                        operation = op,
                        orderTitle = order?.title ?: "Ordem de Serviço",
                        clientName = order?.clientName ?: "Cliente",
                        externalId = order?.externalId
                    )
                }

                SyncCenterUiState.Success(
                    pending = items.filter { it.operation.status == OutboxStatus.PENDING },
                    sending = items.filter { it.operation.status == OutboxStatus.SENDING },
                    conflicts = items.filter { it.operation.status == OutboxStatus.CONFLICT },
                    failed = items.filter { it.operation.status == OutboxStatus.PERMANENT_FAILURE },
                    recentSent = items.filter { it.operation.status == OutboxStatus.SENT }.take(20)
                )
            }.collect {
                _uiState.value = it
            }
        }
    }

    fun cancelOperation(operationId: UUID) {
        viewModelScope.launch {
            publicationRepository.cancelOperation(operationId)
        }
    }

    fun retryOperation(operationId: UUID) {
        viewModelScope.launch {
            publicationRepository.markFailed(operationId, "", permanent = false, System.currentTimeMillis())
            scheduler.scheduleDrain()
        }
    }
}
