package dev.claudiocodigo.nexo.data.repository

import dev.claudiocodigo.nexo.data.local.dao.ServiceOrderDao
import dev.claudiocodigo.nexo.data.local.entity.ServiceOrderEntity
import dev.claudiocodigo.nexo.domain.model.ServiceOrder
import dev.claudiocodigo.nexo.domain.repository.ServiceOrderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject

/** The production local source of truth for service orders. */
class RoomServiceOrderRepository @Inject constructor(
    private val dao: ServiceOrderDao
) : ServiceOrderRepository {

    override fun getServiceOrders(): Flow<List<ServiceOrder>> =
        dao.getAllServiceOrders().map { entities -> entities.map(ServiceOrderEntity::toDomain) }

    override suspend fun getServiceOrderById(id: UUID): ServiceOrder? =
        dao.getServiceOrderById(id)?.toDomain()

    override suspend fun saveServiceOrder(serviceOrder: ServiceOrder) {
        dao.upsertServiceOrder(ServiceOrderEntity.fromDomain(serviceOrder))
    }

    override suspend fun deleteServiceOrder(id: UUID) {
        dao.deleteServiceOrder(id)
    }
}
