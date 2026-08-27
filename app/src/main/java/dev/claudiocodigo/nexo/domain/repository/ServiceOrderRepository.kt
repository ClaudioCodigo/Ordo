package dev.claudiocodigo.nexo.domain.repository

import dev.claudiocodigo.nexo.domain.model.ServiceOrder
import dev.claudiocodigo.nexo.domain.serviceorder.RemoteOccurrenceKey
import dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderPreset
import dev.claudiocodigo.nexo.domain.serviceorder.StructuredServiceOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.util.UUID

interface ServiceOrderRepository {
    // --- Legacy Lightweight Interface ---
    fun getServiceOrders(): Flow<List<ServiceOrder>>
    suspend fun getServiceOrderById(id: UUID): ServiceOrder?
    suspend fun saveServiceOrder(serviceOrder: ServiceOrder)
    suspend fun deleteServiceOrder(id: UUID)

    // --- Structured Phase 3 Interface (with default implementations for test fakes) ---
    fun observeStructuredOrders(): Flow<List<StructuredServiceOrder>> = flowOf(emptyList())
    suspend fun getStructuredOrderById(id: UUID): StructuredServiceOrder? = null
    suspend fun saveStructuredOrder(order: StructuredServiceOrder) {}

    suspend fun createOrGetAttendance(
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
    ): StructuredServiceOrder = StructuredServiceOrder(
        id = UUID.randomUUID(),
        occurrenceKey = key,
        title = title,
        clientName = clientName,
        unitName = unitName,
        preset = initialPreset
    )

    suspend fun getLinkedOrder(key: RemoteOccurrenceKey): StructuredServiceOrder? = null
}
