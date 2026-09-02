package dev.claudiocodigo.nexo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import dev.claudiocodigo.nexo.data.local.entity.PublicationOutboxEntity
import dev.claudiocodigo.nexo.data.local.entity.ServiceOrderEntity
import dev.claudiocodigo.nexo.data.local.entity.ServiceOrderItemEntity
import dev.claudiocodigo.nexo.data.local.entity.ServiceOrderLinkEntity
import dev.claudiocodigo.nexo.data.local.entity.ServiceOrderSnapshotEntity
import dev.claudiocodigo.nexo.data.local.entity.ServiceOrderUpdateEntity
import dev.claudiocodigo.nexo.data.local.entity.ServiceOrderVersionEntity
import dev.claudiocodigo.nexo.domain.serviceorder.RemoteOccurrenceKey
import dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderPreset
import dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderFlow
import dev.claudiocodigo.nexo.domain.serviceorder.StructuredServiceOrder
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface ServiceOrderStoreDao {

    // --- Basic Entity CRUD ---

    @Query("SELECT * FROM service_orders ORDER BY COALESCE(scheduledStart, scheduledDate, createdAt) ASC")
    fun observeAllOrders(): Flow<List<ServiceOrderEntity>>

    @Query("SELECT * FROM service_orders WHERE id = :id")
    suspend fun getOrderById(id: UUID): ServiceOrderEntity?

    @Upsert
    suspend fun upsertOrder(order: ServiceOrderEntity)

    @Query("DELETE FROM service_orders WHERE id = :id")
    suspend fun deleteOrderById(id: UUID)

    // --- Links ---

    @Query(
        """
        SELECT * FROM service_order_links
        WHERE accountId = :accountId
          AND calendarHref = :calendarHref
          AND eventHref = :eventHref
          AND recurrenceId = :recurrenceId
        LIMIT 1
        """
    )
    suspend fun findLink(
        accountId: String,
        calendarHref: String,
        eventHref: String,
        recurrenceId: String
    ): ServiceOrderLinkEntity?

    @Query("SELECT * FROM service_order_links WHERE orderId = :orderId LIMIT 1")
    suspend fun getLinkByOrderId(orderId: UUID): ServiceOrderLinkEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLink(link: ServiceOrderLinkEntity): Long

    @Query("DELETE FROM service_order_links WHERE orderId = :orderId")
    suspend fun deleteLinkByOrderId(orderId: UUID)

    // --- Snapshots ---

    @Query("SELECT * FROM service_order_snapshots WHERE orderId = :orderId ORDER BY capturedAt DESC LIMIT 1")
    suspend fun getSnapshotByOrderId(orderId: UUID): ServiceOrderSnapshotEntity?

    @Upsert
    suspend fun upsertSnapshot(snapshot: ServiceOrderSnapshotEntity)

    @Query("DELETE FROM service_order_snapshots WHERE orderId = :orderId")
    suspend fun deleteSnapshotByOrderId(orderId: UUID)

    // --- Updates ---

    @Query("SELECT * FROM service_order_updates WHERE orderId = :orderId ORDER BY sequenceOrder ASC, executionDate ASC, createdAt ASC")
    suspend fun getUpdatesByOrderId(orderId: UUID): List<ServiceOrderUpdateEntity>

    @Upsert
    suspend fun upsertUpdates(updates: List<ServiceOrderUpdateEntity>)

    @Query("DELETE FROM service_order_updates WHERE orderId = :orderId")
    suspend fun deleteUpdatesByOrderId(orderId: UUID)

    // --- Items ---

    @Query("SELECT * FROM service_order_items WHERE orderId = :orderId ORDER BY createdAt ASC")
    suspend fun getItemsByOrderId(orderId: UUID): List<ServiceOrderItemEntity>

    @Upsert
    suspend fun upsertItems(items: List<ServiceOrderItemEntity>)

    @Query("DELETE FROM service_order_items WHERE orderId = :orderId")
    suspend fun deleteItemsByOrderId(orderId: UUID)

    // --- Versions ---

    @Query("SELECT * FROM service_order_versions WHERE orderId = :orderId ORDER BY versionNumber ASC")
    suspend fun getVersionsByOrderId(orderId: UUID): List<ServiceOrderVersionEntity>

    @Query("UPDATE service_order_versions SET publishedEtag = :etag, publishedAt = :publishedAt WHERE orderId = :orderId AND confirmedRevision = :confirmedRevision AND publishedEtag IS NULL")
    suspend fun markVersionPublished(orderId: UUID, confirmedRevision: Long, etag: String?, publishedAt: Long): Int

    @Upsert
    suspend fun upsertVersion(version: ServiceOrderVersionEntity)

    @Query("DELETE FROM service_order_versions WHERE orderId = :orderId")
    suspend fun deleteVersionsByOrderId(orderId: UUID)

    // --- Outbox ---

    @Query("SELECT * FROM publication_outbox WHERE status = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPendingOutboxOperations(): List<PublicationOutboxEntity>

    @Query("SELECT * FROM publication_outbox WHERE orderId = :orderId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestOutboxForOrder(orderId: UUID): PublicationOutboxEntity?

    @Upsert
    suspend fun upsertOutbox(outbox: PublicationOutboxEntity)

    // --- Transactional Aggregates ---

    @Transaction
    suspend fun getStructuredOrderById(id: UUID): StructuredServiceOrder? {
        val order = getOrderById(id) ?: return null
        val link = getLinkByOrderId(id)
        val snapshot = getSnapshotByOrderId(id)
        val updates = getUpdatesByOrderId(id)
        val items = getItemsByOrderId(id)
        val versions = getVersionsByOrderId(id)
        return order.toStructured(link, snapshot, updates, items, versions)
    }

    @Transaction
    suspend fun saveStructuredOrder(structured: StructuredServiceOrder) {
        upsertOrder(ServiceOrderEntity.fromStructured(structured))

        structured.occurrenceKey?.let { key ->
            insertLink(ServiceOrderLinkEntity.fromDomain(key, structured.id, structured.createdAt))
        }

        structured.baseSnapshot?.let { snapshot ->
            upsertSnapshot(ServiceOrderSnapshotEntity.fromDomain(structured.id, snapshot))
        }

        deleteUpdatesByOrderId(structured.id)
        if (structured.updates.isNotEmpty()) {
            upsertUpdates(structured.updates.map { ServiceOrderUpdateEntity.fromDomain(structured.id, it) })
        }

        deleteItemsByOrderId(structured.id)
        if (structured.items.isNotEmpty()) {
            upsertItems(structured.items.map { ServiceOrderItemEntity.fromDomain(structured.id, it) })
        }

        if (structured.versions.isNotEmpty()) {
            structured.versions.forEach { version ->
                upsertVersion(ServiceOrderVersionEntity.fromDomain(structured.id, version))
            }
        }
    }

    @Transaction
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
    ): StructuredServiceOrder {
        val existingLink = findLink(
            accountId = key.accountId,
            calendarHref = key.calendarHref,
            eventHref = key.eventHref,
            recurrenceId = key.normalizedRecurrenceId
        )

        if (existingLink != null) {
            val existing = getStructuredOrderById(existingLink.orderId)
            if (existing != null) return existing
        }

        val orderId = UUID.randomUUID()
        val now = System.currentTimeMillis()

        val structured = StructuredServiceOrder(
            id = orderId,
            occurrenceKey = key,
            externalId = null,
            title = title,
            clientName = clientName,
            unitName = unitName,
            preset = initialPreset,
            flow = if (initialPreset == ServiceOrderPreset.SERVICO_SOLICITADO) ServiceOrderFlow.REQUEST else ServiceOrderFlow.RESOLUTION,
            originalDemand = rawDescription.orEmpty(),
            scheduledStart = startMillis,
            scheduledEnd = endMillis,
            baseSnapshot = if (rawIcs != null) {
                dev.claudiocodigo.nexo.domain.serviceorder.RemoteBaseSnapshot(
                    etag = etag,
                    rawIcs = rawIcs,
                    rawSummary = rawSummary,
                    rawDescription = rawDescription,
                    capturedAt = now
                )
            } else null,
            createdAt = now,
            updatedAt = now
        )

        saveStructuredOrder(structured)
        return structured
    }

    @Transaction
    suspend fun deleteStructuredOrder(id: UUID) {
        deleteOrderById(id)
        deleteLinkByOrderId(id)
        deleteSnapshotByOrderId(id)
        deleteUpdatesByOrderId(id)
        deleteItemsByOrderId(id)
        deleteVersionsByOrderId(id)
    }
}
