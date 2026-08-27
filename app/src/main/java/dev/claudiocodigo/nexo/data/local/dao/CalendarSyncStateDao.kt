package dev.claudiocodigo.nexo.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.claudiocodigo.nexo.data.local.entity.CalendarSyncStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarSyncStateDao {

    @Query("SELECT * FROM calendar_sync_state WHERE accountId = :accountId AND calendarHref = :calendarHref")
    fun observe(accountId: String, calendarHref: String): Flow<CalendarSyncStateEntity?>

    @Query("SELECT * FROM calendar_sync_state WHERE accountId = :accountId AND calendarHref = :calendarHref")
    suspend fun get(accountId: String, calendarHref: String): CalendarSyncStateEntity?

    @Upsert
    suspend fun upsert(state: CalendarSyncStateEntity)

    @Query("DELETE FROM calendar_sync_state WHERE accountId = :accountId")
    suspend fun deleteForAccount(accountId: String)
}
