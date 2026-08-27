package dev.claudiocodigo.nexo.data.repository

import dev.claudiocodigo.nexo.data.local.dao.CalendarAccountDao
import dev.claudiocodigo.nexo.data.local.dao.CalendarDao
import dev.claudiocodigo.nexo.data.local.dao.CalendarSyncStateDao
import dev.claudiocodigo.nexo.data.local.dao.RemoteEventDao
import dev.claudiocodigo.nexo.data.local.entity.CalendarEntity
import dev.claudiocodigo.nexo.data.local.entity.RemoteEventEntity
import dev.claudiocodigo.nexo.domain.caldav.AccountIdentity
import dev.claudiocodigo.nexo.domain.caldav.CalendarInfo
import dev.claudiocodigo.nexo.domain.caldav.CalendarSyncState
import dev.claudiocodigo.nexo.domain.caldav.ColorClassifier
import dev.claudiocodigo.nexo.domain.caldav.EventColor
import dev.claudiocodigo.nexo.domain.caldav.RemoteEvent
import dev.claudiocodigo.nexo.domain.repository.CalendarRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Read-only local source of truth for the mirrored calendar cache. */
@OptIn(ExperimentalCoroutinesApi::class)
class RoomCalendarRepository @Inject constructor(
    private val accountDao: CalendarAccountDao,
    private val calendarDao: CalendarDao,
    private val eventDao: RemoteEventDao,
    private val syncStateDao: CalendarSyncStateDao
) : CalendarRepository {

    private fun selectedCalendarFlow(): Flow<CalendarEntity?> =
        accountDao.observeAll()
            .map { it.firstOrNull()?.id }
            .distinctUntilChanged()
            .flatMapLatest { accountId ->
                if (accountId == null) flowOf(null)
                else calendarDao.observeSelected(accountId)
            }

    override fun observeEvents(): Flow<List<RemoteEvent>> =
        selectedCalendarFlow().flatMapLatest { cal ->
            if (cal == null) flowOf(emptyList())
            else eventDao.observeForCalendar(cal.accountId, cal.href)
                .map(::toDomainList)
        }

    override fun observeEventsForDay(dayStartMillis: Long, dayEndMillis: Long): Flow<List<RemoteEvent>> =
        selectedCalendarFlow().flatMapLatest { cal ->
            if (cal == null) flowOf(emptyList())
            else eventDao.observeForDay(cal.accountId, cal.href, dayStartMillis, dayEndMillis)
                .map(::toDomainList)
        }

    override fun searchEvents(query: String): Flow<List<RemoteEvent>> =
        selectedCalendarFlow().flatMapLatest { cal ->
            if (cal == null) flowOf(emptyList())
            else eventDao.search(cal.accountId, cal.href, query)
                .map(::toDomainList)
        }

    override fun observeOverdue(nowMillis: Long): Flow<List<RemoteEvent>> =
        selectedCalendarFlow().flatMapLatest { cal ->
            if (cal == null) flowOf(emptyList())
            else eventDao.observeOverdue(cal.accountId, cal.href, nowMillis)
                .map(::toDomainList)
        }

    override suspend fun getEvent(accountId: String, calendarHref: String, href: String): RemoteEvent? =
        eventDao.getById(accountId, calendarHref, href)?.toDomain()

    override fun observeAccount(): Flow<AccountIdentity?> =
        accountDao.observeAll().map { it.firstOrNull()?.let { AccountIdentity(it.server, it.user) } }

    override fun observeSelectedCalendar(): Flow<CalendarInfo?> =
        selectedCalendarFlow().map { it?.toDomain() }

    override fun observeSyncState(): Flow<CalendarSyncState?> =
        selectedCalendarFlow().flatMapLatest { cal ->
            if (cal == null) flowOf(null)
            else syncStateDao.observe(cal.accountId, cal.href).map { state ->
                state?.let {
                    CalendarSyncState(
                        lastSyncMillis = it.lastSyncMillis,
                        lastSuccessMillis = it.lastSuccessMillis,
                        lastResult = it.lastResult,
                        lastErrorMessage = it.lastErrorMessage
                    )
                }
            }
        }

    override fun classifyColor(raw: String?): EventColor = ColorClassifier.classify(raw)

    private fun toDomainList(entities: List<RemoteEventEntity>): List<RemoteEvent> =
        entities.map(RemoteEventEntity::toDomain)
}
