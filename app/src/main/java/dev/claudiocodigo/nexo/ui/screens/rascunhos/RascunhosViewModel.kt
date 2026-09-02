package dev.claudiocodigo.nexo.ui.screens.rascunhos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.claudiocodigo.nexo.domain.repository.ServiceOrderRepository
import dev.claudiocodigo.nexo.domain.serviceorder.PublicationState
import dev.claudiocodigo.nexo.domain.serviceorder.StructuredServiceOrder
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class RascunhosViewModel @Inject constructor(
    private val repository: ServiceOrderRepository
) : ViewModel() {

    val drafts: StateFlow<List<StructuredServiceOrder>> = repository.observeStructuredOrders()
        .map { orders ->
            orders.filter {
                it.occurrenceKey == null && it.publicationState == PublicationState.LOCAL_DRAFT
            }.sortedByDescending { it.updatedAt }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun discardDraft(orderId: UUID) {
        viewModelScope.launch {
            repository.deleteStructuredOrder(orderId)
        }
    }

    fun clearAllDrafts() {
        viewModelScope.launch {
            val currentDrafts = drafts.value
            currentDrafts.forEach { draft ->
                repository.deleteStructuredOrder(draft.id)
            }
        }
    }
}
