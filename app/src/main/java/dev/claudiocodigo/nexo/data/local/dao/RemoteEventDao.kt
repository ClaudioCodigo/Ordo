package dev.claudiocodigo.nexo.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.claudiocodigo.nexo.data.local.entity.RemoteEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RemoteEventDao {

    @Upsert
    suspend fun upsertAll(events: List<RemoteEventEntity>)

    @Query("SELECT * FROM remote_events WHERE accountId = :accountId AND calendarHref = :calendarHref ORDER BY start ASC, summary ASC")
    fun observeForCalendar(accountId: String, calendarHref: String): Flow<List<RemoteEventEntity>>

    @Query("SELECT * FROM remote_events WHERE accountId = :accountId AND calendarHref = :calendarHref")
    suspend fun getAllForCalendar(accountId: String, calendarHref: String): List<RemoteEventEntity>

    @Query("SELECT * FROM remote_events WHERE accountId = :accountId AND calendarHref = :calendarHref AND href = :href")
    suspend fun getById(accountId: String, calendarHref: String, href: String): RemoteEventEntity?

    @Query("SELECT * FROM remote_events WHERE accountId = :accountId AND calendarHref = :calendarHref AND href IN (:hrefs)")
    suspend fun getByHrefs(
        accountId: String,
        calendarHref: String,
        hrefs: List<String>
    ): List<RemoteEventEntity>

    @Query("SELECT * FROM remote_events WHERE accountId = :accountId AND calendarHref = :calendarHref AND start >= :dayStart AND start < :dayEnd ORDER BY start ASC")
    fun observeForDay(
        accountId: String,
        calendarHref: String,
        dayStart: Long,
        dayEnd: Long
    ): Flow<List<RemoteEventEntity>>

    @Query("SELECT * FROM remote_events WHERE accountId = :accountId AND calendarHref = :calendarHref AND end < :now ORDER BY start ASC")
    fun observeOverdue(
        accountId: String,
        calendarHref: String,
        now: Long
    ): Flow<List<RemoteEventEntity>>

    @Query(
        """
        SELECT * FROM remote_events
        WHERE accountId = :accountId AND calendarHref = :calendarHref
          AND (:query = '' OR summary LIKE '%' || :query || '%'
            OR description LIKE '%' || :query || '%'
            OR location LIKE '%' || :query || '%')
        ORDER BY start ASC
        """
    )
    fun search(
        accountId: String,
        calendarHref: String,
        query: String
    ): Flow<List<RemoteEventEntity>>

    @Query("DELETE FROM remote_events WHERE accountId = :accountId AND calendarHref = :calendarHref AND href = :href")
    suspend fun deleteByHref(accountId: String, calendarHref: String, href: String)

    @Query("DELETE FROM remote_events WHERE accountId = :accountId AND calendarHref = :calendarHref")
    suspend fun deleteAllForCalendar(accountId: String, calendarHref: String)

    @Query("DELETE FROM remote_events WHERE accountId = :accountId")
    suspend fun deleteAllForAccount(accountId: String)
}
