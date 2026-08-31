package dev.claudiocodigo.nexo.ui.screens.resumo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.claudiocodigo.nexo.domain.repository.ServiceOrderRepository
import dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderExtractor
import dev.claudiocodigo.nexo.domain.serviceorder.StructuredServiceOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class SummaryExtractionUiState(
    val orderId: UUID? = null,
    val rawSummary: String = "",
    val detectedSegments: List<String> = emptyList(),
    val externalId: String = "",
    val title: String = "",
    val clientName: String = "",
    val unitName: String = "",
    val technician: String = "",
    val category: String = "",
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class SummaryExtractionViewModel @Inject constructor(
    private val repository: ServiceOrderRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SummaryExtractionUiState(isLoading = true))
    val uiState: StateFlow<SummaryExtractionUiState> = _uiState.asStateFlow()

    private var loadedOrder: StructuredServiceOrder? = null

    fun load(orderId: UUID) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, orderId = orderId) }
            val order = repository.getStructuredOrderById(orderId)
            if (order == null) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Ordem de Serviço não encontrada") }
                return@launch
            }

            loadedOrder = order
            val raw = order.baseSnapshot?.rawSummary?.takeIf { it.isNotBlank() } ?: order.title
            val extracted = ServiceOrderExtractor.extractSummary(raw)

            _uiState.update {
                it.copy(
                    isLoading = false,
                    rawSummary = raw,
                    detectedSegments = extracted.segments,
                    externalId = order.externalId?.takeIf { ext -> ext.isNotBlank() } ?: extracted.externalId.orEmpty(),
                    title = order.title.takeIf { t -> t.isNotBlank() } ?: extracted.title,
                    clientName = order.clientName.takeIf { c -> c.isNotBlank() } ?: extracted.clientName.orEmpty(),
                    unitName = order.unitName.takeIf { u -> u.isNotBlank() } ?: extracted.unitName.orEmpty(),
                    technician = order.technician?.takeIf { tech -> tech.isNotBlank() } ?: extracted.technician.orEmpty(),
                    category = order.category?.takeIf { cat -> cat.isNotBlank() } ?: extracted.category.orEmpty()
                )
            }
        }
    }

    fun onRawSummaryChange(newRaw: String) {
        _uiState.update { it.copy(rawSummary = newRaw) }
    }

    fun reExtractFromRaw() {
        val raw = _uiState.value.rawSummary
        val extracted = ServiceOrderExtractor.extractSummary(raw)
        _uiState.update {
            it.copy(
                detectedSegments = extracted.segments,
                externalId = extracted.externalId.orEmpty(),
                title = extracted.title,
                clientName = extracted.clientName.orEmpty(),
                unitName = extracted.unitName.orEmpty(),
                technician = extracted.technician.orEmpty(),
                category = extracted.category.orEmpty()
            )
        }
    }

    fun onExternalIdChange(value: String) = _uiState.update { it.copy(externalId = value) }
    fun onTitleChange(value: String) = _uiState.update { it.copy(title = value) }
    fun onClientNameChange(value: String) = _uiState.update { it.copy(clientName = value) }
    fun onUnitNameChange(value: String) = _uiState.update { it.copy(unitName = value) }
    fun onTechnicianChange(value: String) = _uiState.update { it.copy(technician = value) }
    fun onCategoryChange(value: String) = _uiState.update { it.copy(category = value) }

    fun applyChanges(onSuccess: (UUID) -> Unit) {
        val currentOrder = loadedOrder ?: return
        val state = _uiState.value

        val updated = currentOrder.copy(
            externalId = state.externalId.trim().takeIf { it.isNotBlank() },
            title = state.title.trim().ifBlank { "Atendimento sem título" },
            clientName = state.clientName.trim().ifBlank { "Cliente" },
            unitName = state.unitName.trim().ifBlank { "Unidade" },
            technician = state.technician.trim().takeIf { it.isNotBlank() },
            category = state.category.trim().takeIf { it.isNotBlank() }
        )

        viewModelScope.launch {
            repository.saveStructuredOrder(updated)
            _uiState.update { it.copy(isSaved = true) }
            onSuccess(currentOrder.id)
        }
    }
}
