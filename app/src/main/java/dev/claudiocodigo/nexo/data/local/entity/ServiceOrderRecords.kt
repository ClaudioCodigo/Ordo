package dev.claudiocodigo.nexo.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.claudiocodigo.nexo.domain.serviceorder.RemoteBaseSnapshot
import dev.claudiocodigo.nexo.domain.serviceorder.RemoteOccurrenceKey
import dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderItem
import dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderUpdate
import dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderVersion
import java.util.UUID

/**
 * Composite link mapping a specific remote calendar occurrence to a local service order.
 *
 * Indexed by (accountId, calendarHref, eventHref, recurrenceId) with unique constraints
 * to guarantee that creating or retrieving a link for the same occurrence is strictly idempotent.
 */
@Entity(
    tableName = "service_order_links",
    primaryKeys = ["accountId", "calendarHref", "eventHref", "recurrenceId"],
    indices = [
        Index("orderId", unique = true),
        Index(value = ["accountId", "calendarHref", "eventHref", "recurrenceId"], unique = true)
    ]
)
data class ServiceOrderLinkEntity(
    val accountId: String,
    val calendarHref: String,
    val eventHref: String,
    val recurrenceId: String,
    val orderId: UUID,
    val linkedAt: Long
) {
    fun toOccurrenceKey() = RemoteOccurrenceKey(
        accountId = accountId,
        calendarHref = calendarHref,
        eventHref = eventHref,
        recurrenceId = recurrenceId.takeIf { it.isNotEmpty() }
    )

    companion object {
        fun fromDomain(key: RemoteOccurrenceKey, orderId: UUID, linkedAt: Long) = ServiceOrderLinkEntity(
            accountId = key.accountId,
            calendarHref = key.calendarHref,
            eventHref = key.eventHref,
            recurrenceId = key.normalizedRecurrenceId,
            orderId = orderId,
            linkedAt = linkedAt
        )
    }
}

/** Immutable snapshot of the remote CalDAV event at the time of link or revalidation. */
@Entity(
    tableName = "service_order_snapshots",
    primaryKeys = ["id"],
    indices = [Index("orderId")]
)
data class ServiceOrderSnapshotEntity(
    val id: UUID,
    val orderId: UUID,
    val etag: String?,
    val rawIcs: String,
    val rawSummary: String?,
    val rawDescription: String?,
    val capturedAt: Long
) {
    fun toDomain() = RemoteBaseSnapshot(
        id = id,
        etag = etag,
        rawIcs = rawIcs,
        rawSummary = rawSummary,
        rawDescription = rawDescription,
        capturedAt = capturedAt
    )

    companion object {
        fun fromDomain(orderId: UUID, snapshot: RemoteBaseSnapshot) = ServiceOrderSnapshotEntity(
            id = snapshot.id,
            orderId = orderId,
            etag = snapshot.etag,
            rawIcs = snapshot.rawIcs,
            rawSummary = snapshot.rawSummary,
            rawDescription = snapshot.rawDescription,
            capturedAt = snapshot.capturedAt
        )
    }
}

/** Individual update logged against a service order during execution. */
@Entity(
    tableName = "service_order_updates",
    primaryKeys = ["id"],
    indices = [Index("orderId"), Index(value = ["orderId", "sequenceOrder"])]
)
data class ServiceOrderUpdateEntity(
    val id: UUID,
    val orderId: UUID,
    val sequenceOrder: Int,
    val text: String,
    val executionDate: Long,
    val createdAt: Long
) {
    fun toDomain() = ServiceOrderUpdate(
        id = id,
        sequenceOrder = sequenceOrder,
        text = text,
        executionDate = executionDate,
        createdAt = createdAt
    )

    companion object {
        fun fromDomain(orderId: UUID, update: ServiceOrderUpdate) = ServiceOrderUpdateEntity(
            id = update.id,
            orderId = orderId,
            sequenceOrder = update.sequenceOrder,
            text = update.text,
            executionDate = update.executionDate,
            createdAt = update.createdAt
        )
    }
}

/** Generic equipment/item attached to a service order. */
@Entity(
    tableName = "service_order_items",
    primaryKeys = ["id"],
    indices = [Index("orderId")]
)
data class ServiceOrderItemEntity(
    val id: UUID,
    val orderId: UUID,
    val action: String,
    val itemType: String,
    val brand: String?,
    val model: String?,
    val serialNumber: String?,
    val relatedEquipment: String?,
    val location: String?,
    val notes: String?,
    val createdAt: Long
) {
    fun toDomain() = ServiceOrderItem(
        id = id,
        action = action,
        itemType = itemType,
        brand = brand,
        model = model,
        serialNumber = serialNumber,
        relatedEquipment = relatedEquipment,
        location = location,
        notes = notes,
        createdAt = createdAt
    )

    companion object {
        fun fromDomain(orderId: UUID, item: ServiceOrderItem) = ServiceOrderItemEntity(
            id = item.id,
            orderId = orderId,
            action = item.action,
            itemType = item.itemType,
            brand = item.brand,
            model = item.model,
            serialNumber = item.serialNumber,
            relatedEquipment = item.relatedEquipment,
            location = item.location,
            notes = item.notes,
            createdAt = item.createdAt
        )
    }
}

/** Immutable record of a published version of a service order. */
@Entity(
    tableName = "service_order_versions",
    primaryKeys = ["id"],
    indices = [
        Index("orderId"),
        Index(value = ["orderId", "versionNumber"], unique = true)
    ]
)
data class ServiceOrderVersionEntity(
    val id: UUID,
    val orderId: UUID,
    val versionNumber: Int,
    val formattedDescription: String,
    val publishedEtag: String?,
    val publishedAt: Long
) {
    fun toDomain() = ServiceOrderVersion(
        id = id,
        versionNumber = versionNumber,
        formattedDescription = formattedDescription,
        publishedEtag = publishedEtag,
        publishedAt = publishedAt
    )

    companion object {
        fun fromDomain(orderId: UUID, version: ServiceOrderVersion) = ServiceOrderVersionEntity(
            id = version.id,
            orderId = orderId,
            versionNumber = version.versionNumber,
            formattedDescription = version.formattedDescription,
            publishedEtag = version.publishedEtag,
            publishedAt = version.publishedAt
        )
    }
}

/** Offline publication outbox recording intended mutations before sync. */
@Entity(
    tableName = "publication_outbox",
    primaryKeys = ["id"],
    indices = [
        Index("orderId"),
        Index("status")
    ]
)
data class PublicationOutboxEntity(
    val id: UUID,
    val orderId: UUID,
    val action: String, // CREATE, UPDATE, FINALIZE
    val payloadIcs: String,
    val ifMatchEtag: String?,
    val status: String, // PENDING, SENDING, SENT, CONFLICT, FAILED
    val lastError: String?,
    val retryCount: Int,
    val createdAt: Long,
    val updatedAt: Long
)
