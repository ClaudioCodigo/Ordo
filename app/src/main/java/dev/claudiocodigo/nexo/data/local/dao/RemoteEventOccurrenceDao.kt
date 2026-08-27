package dev.claudiocodigo.nexo.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.claudiocodigo.nexo.data.local.entity.RemoteEventOccurrenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RemoteEventOccurrenceDao {

    @Upsert
    suspend fun upsertAll(occurrences: List<RemoteEventOccurrenceEntity>)

    @Query("SELECT * FROM remote_event_occurrences WHERE accountId = :accountId AND calendarHref = :calendarHref ORDER BY start ASC")
    fun observeForCalendar(accountId: String, calendarHref: String): Flow<List<RemoteEventOccurrenceEntity>>

    @Query(
        """
        SELECT * FROM remote_event_occurrences
        WHERE accountId = :accountId
          AND calendarHref = :calendarHref
          AND eventHref = :eventHref
          AND recurrenceId = :recurrenceId
        LIMIT 1
        """
    )
    suspend fun getOccurrence(
        accountId: String,
        calendarHref: String,
        eventHref: String,
        recurrenceId: String
    ): RemoteEventOccurrenceEntity?

    @Query("DELETE FROM remote_event_occurrences WHERE accountId = :accountId AND calendarHref = :calendarHref AND eventHref = :eventHref")
    suspend fun deleteForEvent(accountId: String, calendarHref: String, eventHref: String)

    @Query("DELETE FROM remote_event_occurrences WHERE accountId = :accountId AND calendarHref = :calendarHref")
    suspend fun deleteAllForCalendar(accountId: String, calendarHref: String)
}
