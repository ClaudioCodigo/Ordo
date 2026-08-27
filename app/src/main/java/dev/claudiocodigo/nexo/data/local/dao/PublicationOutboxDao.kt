package dev.claudiocodigo.nexo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.claudiocodigo.nexo.data.local.entity.PublicationOutboxEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface PublicationOutboxDao {

    @Query("SELECT * FROM publication_outbox ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PublicationOutboxEntity>>

    @Query("SELECT * FROM publication_outbox WHERE id = :id")
    suspend fun getById(id: UUID): PublicationOutboxEntity?

    @Query("SELECT * FROM publication_outbox WHERE orderId = :orderId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestForOrder(orderId: UUID): PublicationOutboxEntity?

    @Query(
        """
        SELECT * FROM publication_outbox
        WHERE status = 'PENDING'
           OR (status = 'SENDING' AND updatedAt < :expiredThreshold)
        ORDER BY createdAt ASC
        LIMIT 1
        """
    )
    suspend fun findNextEligible(expiredThreshold: Long): PublicationOutboxEntity?

    @Query(
        """
        UPDATE publication_outbox
        SET status = 'SENDING', updatedAt = :nowMillis
        WHERE id = :id AND (status = 'PENDING' OR status = 'SENDING')
        """
    )
    suspend fun claim(id: UUID, nowMillis: Long): Int

    @Query(
        """
        UPDATE publication_outbox
        SET status = 'SENT', lastError = null, updatedAt = :nowMillis
        WHERE id = :id
        """
    )
    suspend fun markSent(id: UUID, nowMillis: Long): Int

    @Query(
        """
        UPDATE publication_outbox
        SET status = 'CONFLICT', lastError = :reason, updatedAt = :nowMillis
        WHERE id = :id
        """
    )
    suspend fun markConflict(id: UUID, reason: String, nowMillis: Long): Int

    @Query(
        """
        UPDATE publication_outbox
        SET status = :status, lastError = :reason, retryCount = retryCount + 1, updatedAt = :nowMillis
        WHERE id = :id
        """
    )
    suspend fun markFailed(id: UUID, status: String, reason: String, nowMillis: Long): Int

    @Query("DELETE FROM publication_outbox WHERE id = :id AND status = 'PENDING'")
    suspend fun deleteIfPending(id: UUID): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PublicationOutboxEntity)
}
