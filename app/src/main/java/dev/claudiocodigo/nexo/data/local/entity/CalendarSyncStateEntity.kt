package dev.claudiocodigo.nexo.data.local.entity

import androidx.room.Entity

/**
 * Per-calendar sync bookkeeping. The UI reads a real "last sync" result from
 * here; there is never a fabricated success. `lastResult` holds a stable token
 * such as `success`, `error` or `unauthenticated`.
 */
@Entity(
    tableName = "calendar_sync_state",
    primaryKeys = ["accountId", "calendarHref"]
)
data class CalendarSyncStateEntity(
    val accountId: String,
    val calendarHref: String,
    val lastSyncMillis: Long,
    val lastSuccessMillis: Long?,
    val lastResult: String?,
    val lastErrorMessage: String?,
    val syncToken: String?
)
