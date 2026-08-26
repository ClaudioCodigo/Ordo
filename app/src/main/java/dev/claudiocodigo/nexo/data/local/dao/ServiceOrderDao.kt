package dev.claudiocodigo.nexo.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.claudiocodigo.nexo.data.local.entity.ServiceOrderEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface ServiceOrderDao {
    @Query("SELECT * FROM service_orders ORDER BY scheduledDate ASC")
    fun getAllServiceOrders(): Flow<List<ServiceOrderEntity>>

    @Query("SELECT * FROM service_orders WHERE id = :id")
    suspend fun getServiceOrderById(id: UUID): ServiceOrderEntity?

    @Upsert
    suspend fun upsertServiceOrder(serviceOrder: ServiceOrderEntity)

    @Query("DELETE FROM service_orders WHERE id = :id")
    suspend fun deleteServiceOrder(id: UUID)
}
