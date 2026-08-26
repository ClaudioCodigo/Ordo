package dev.claudiocodigo.nexo.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.claudiocodigo.nexo.domain.model.ServiceOrder
import dev.claudiocodigo.nexo.domain.model.ServiceOrderStatus
import java.util.UUID

@Entity(tableName = "service_orders")
data class ServiceOrderEntity(
    @PrimaryKey val id: UUID,
    val externalId: String?,
    val title: String,
    val description: String,
    val status: String,
    val clientName: String,
    val unitName: String,
    val scheduledDate: Long?,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toDomain() = ServiceOrder(
        id = id,
        externalId = externalId,
        title = title,
        description = description,
        // Older/corrupt local data must not prevent the agenda from opening.
        // Unknown values remain actionable instead of crashing the mapper.
        status = ServiceOrderStatus.entries.firstOrNull { it.name == status }
            ?: ServiceOrderStatus.PENDENTE,
        clientName = clientName,
        unitName = unitName,
        scheduledDate = scheduledDate,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(domain: ServiceOrder) = ServiceOrderEntity(
            id = domain.id,
            externalId = domain.externalId,
            title = domain.title,
            description = domain.description,
            status = domain.status.name,
            clientName = domain.clientName,
            unitName = domain.unitName,
            scheduledDate = domain.scheduledDate,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )
    }
}
