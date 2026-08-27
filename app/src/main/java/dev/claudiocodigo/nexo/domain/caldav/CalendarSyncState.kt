package dev.claudiocodigo.nexo.domain.caldav

/** Real sync bookkeeping exposed to the UI (never fabricated). */
data class CalendarSyncState(
    val lastSyncMillis: Long,
    val lastSuccessMillis: Long?,
    val lastResult: String?,
    val lastErrorMessage: String?
) {
    val isSuccess: Boolean get() = lastResult == "success"
    val isUnauthenticated: Boolean get() = lastResult == "unauthenticated"
    val isError: Boolean get() = lastResult == "error"
}
