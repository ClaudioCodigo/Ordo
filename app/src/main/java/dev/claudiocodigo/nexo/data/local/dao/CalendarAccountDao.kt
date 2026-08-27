package dev.claudiocodigo.nexo.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.claudiocodigo.nexo.data.local.entity.CalendarAccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarAccountDao {

    @Query("SELECT * FROM calendar_accounts ORDER BY updatedAt DESC, id ASC")
    fun observeAll(): Flow<List<CalendarAccountEntity>>

    @Query("SELECT * FROM calendar_accounts ORDER BY updatedAt DESC, id ASC")
    suspend fun getAll(): List<CalendarAccountEntity>

    @Query("SELECT * FROM calendar_accounts WHERE id = :id")
    suspend fun getById(id: String): CalendarAccountEntity?

    @Upsert
    suspend fun upsert(account: CalendarAccountEntity)

    @Query("DELETE FROM calendar_accounts WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM calendar_accounts")
    suspend fun deleteAll()
}
