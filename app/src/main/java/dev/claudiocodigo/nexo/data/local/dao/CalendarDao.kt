package dev.claudiocodigo.nexo.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import dev.claudiocodigo.nexo.data.local.entity.CalendarEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarDao {

    @Query("SELECT * FROM calendars WHERE accountId = :accountId ORDER BY displayName ASC")
    fun observeForAccount(accountId: String): Flow<List<CalendarEntity>>

    @Query("SELECT * FROM calendars WHERE accountId = :accountId")
    suspend fun getForAccount(accountId: String): List<CalendarEntity>

    @Query("SELECT * FROM calendars WHERE accountId = :accountId AND href = :href")
    suspend fun getByHref(accountId: String, href: String): CalendarEntity?

    @Query("SELECT * FROM calendars WHERE accountId = :accountId AND isSelected = 1 LIMIT 1")
    fun observeSelected(accountId: String): Flow<CalendarEntity?>

    @Query("SELECT * FROM calendars WHERE accountId = :accountId AND isSelected = 1 LIMIT 1")
    suspend fun getSelected(accountId: String): CalendarEntity?

    @Upsert
    suspend fun upsertAll(calendars: List<CalendarEntity>)

    @Query("UPDATE calendars SET isSelected = 0 WHERE accountId = :accountId")
    suspend fun clearSelection(accountId: String)

    @Query("UPDATE calendars SET isSelected = 1 WHERE accountId = :accountId AND href = :href")
    suspend fun markSelected(accountId: String, href: String)

    /** Selects exactly one work calendar for the account. */
    @Transaction
    suspend fun selectWorkingCalendar(accountId: String, href: String) {
        clearSelection(accountId)
        markSelected(accountId, href)
    }

    @Query("DELETE FROM calendars WHERE accountId = :accountId")
    suspend fun deleteForAccount(accountId: String)

    @Query("DELETE FROM calendars WHERE accountId = :accountId AND href = :href")
    suspend fun deleteByHref(accountId: String, href: String)

    @Query("DELETE FROM calendars WHERE accountId = :accountId AND href NOT IN (:keepHrefs)")
    suspend fun deleteStale(accountId: String, keepHrefs: List<String>)
}
