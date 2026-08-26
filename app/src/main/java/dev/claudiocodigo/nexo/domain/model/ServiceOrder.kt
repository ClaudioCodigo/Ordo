package dev.claudiocodigo.nexo.domain.model

import java.util.UUID

data class ServiceOrder(
    val id: UUID = UUID.randomUUID(),
    val externalId: String? = null, // Número da OS original
    val title: String,
    val description: String = "",
    val status: ServiceOrderStatus = ServiceOrderStatus.PENDENTE,
    val clientName: String,
    val unitName: String,
    val scheduledDate: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class ServiceOrderStatus {
    PENDENTE,
    EM_ANDAMENTO,
    CONCLUIDA,
    CANCELADA
}
