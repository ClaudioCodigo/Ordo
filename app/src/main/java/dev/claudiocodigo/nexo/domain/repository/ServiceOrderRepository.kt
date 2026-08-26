package dev.claudiocodigo.nexo.domain.repository

import dev.claudiocodigo.nexo.domain.model.ServiceOrder
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface ServiceOrderRepository {
    fun getServiceOrders(): Flow<List<ServiceOrder>>
    suspend fun getServiceOrderById(id: UUID): ServiceOrder?
    suspend fun saveServiceOrder(serviceOrder: ServiceOrder)
    suspend fun deleteServiceOrder(id: UUID)
}
