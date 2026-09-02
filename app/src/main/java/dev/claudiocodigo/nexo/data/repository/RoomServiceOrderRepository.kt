package dev.claudiocodigo.nexo.data.repository

import dev.claudiocodigo.nexo.data.local.dao.ServiceOrderDao
import dev.claudiocodigo.nexo.data.local.dao.ServiceOrderStoreDao
import dev.claudiocodigo.nexo.data.local.entity.ServiceOrderEntity
import dev.claudiocodigo.nexo.domain.model.ServiceOrder
import dev.claudiocodigo.nexo.domain.repository.ServiceOrderRepository
import dev.claudiocodigo.nexo.domain.serviceorder.RemoteOccurrenceKey
import dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderPreset
import dev.claudiocodigo.nexo.domain.serviceorder.StructuredServiceOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject

/**
 * The production local source of truth for both legacy and structured service orders.
 */
class RoomServiceOrderRepository @Inject constructor(
    private val dao: ServiceOrderDao,
    private val storeDao: ServiceOrderStoreDao
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

    override fun observeStructuredOrders(): Flow<List<StructuredServiceOrder>> =
        storeDao.observeAllOrders().map { entities ->
            entities.map { entity ->
                storeDao.getStructuredOrderById(entity.id) ?: entity.toStructured()
            }
        }

    override suspend fun getStructuredOrderById(id: UUID): StructuredServiceOrder? =
        storeDao.getStructuredOrderById(id)

    override suspend fun saveStructuredOrder(order: StructuredServiceOrder) {
        storeDao.saveStructuredOrder(order)
    }

    override suspend fun deleteStructuredOrder(id: UUID) {
        storeDao.deleteStructuredOrder(id)
    }

    override suspend fun createOrGetAttendance(
        key: RemoteOccurrenceKey,
        initialPreset: ServiceOrderPreset,
        title: String,
        clientName: String,
        unitName: String,
        rawSummary: String?,
        rawDescription: String?,
        rawIcs: String?,
        etag: String?,
        startMillis: Long?,
        endMillis: Long?
    ): StructuredServiceOrder = storeDao.createOrGetAttendance(
        key = key,
        initialPreset = initialPreset,
        title = title,
        clientName = clientName,
        unitName = unitName,
        rawSummary = rawSummary,
        rawDescription = rawDescription,
        rawIcs = rawIcs,
        etag = etag,
        startMillis = startMillis,
        endMillis = endMillis
    )

    override suspend fun getLinkedOrder(key: RemoteOccurrenceKey): StructuredServiceOrder? {
        val link = storeDao.findLink(
            accountId = key.accountId,
            calendarHref = key.calendarHref,
            eventHref = key.eventHref,
            recurrenceId = key.normalizedRecurrenceId
        ) ?: return null
        return storeDao.getStructuredOrderById(link.orderId)
    }
}
