package dev.claudiocodigo.nexo.data.repository

import androidx.room.withTransaction
import dev.claudiocodigo.nexo.data.local.NexoDatabase
import dev.claudiocodigo.nexo.data.local.dao.CalendarAccountDao
import dev.claudiocodigo.nexo.data.local.dao.CalendarDao
import dev.claudiocodigo.nexo.data.local.dao.CalendarSyncStateDao
import dev.claudiocodigo.nexo.data.local.dao.RemoteEventDao
import dev.claudiocodigo.nexo.data.local.entity.CalendarAccountEntity
import dev.claudiocodigo.nexo.data.local.entity.CalendarEntity
import dev.claudiocodigo.nexo.domain.caldav.CalendarInfo
import dev.claudiocodigo.nexo.domain.repository.CalendarSetupRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Room-backed local setup operations for the Nextcloud account. */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class RoomCalendarSetupRepository @Inject constructor(
    private val database: NexoDatabase,
    private val accountDao: CalendarAccountDao,
    private val calendarDao: CalendarDao,
    private val eventDao: RemoteEventDao,
    private val syncStateDao: CalendarSyncStateDao
) : CalendarSetupRepository {

    override suspend fun ensureAccount(server: String, user: String): String {
        return database.withTransaction {
            val accounts = accountDao.getAll()
            val existing = accounts.firstOrNull()
            if (accounts.size == 1 && existing != null && existing.server == server && existing.user == user) {
                return@withTransaction existing.id
            }

            // There is exactly one account. Switching identity replaces all
            // account-owned metadata/cache atomically, while service_orders
            // remain untouched.
            accounts.forEach {
                eventDao.deleteAllForAccount(it.id)
                syncStateDao.deleteForAccount(it.id)
                calendarDao.deleteForAccount(it.id)
            }
            accountDao.deleteAll()
            val id = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            accountDao.upsert(CalendarAccountEntity(id = id, server = server, user = user, createdAt = now, updatedAt = now))
            id
        }
    }

    override suspend fun getActiveAccountId(): String? = accountDao.getAll().firstOrNull()?.id

    override suspend fun saveCalendars(accountId: String, calendars: List<CalendarInfo>) {
        val now = System.currentTimeMillis()
        val previous = calendarDao.getForAccount(accountId).associateBy { it.href }
        val entities = calendars.map { info ->
            CalendarEntity.fromDomain(
                accountId = accountId,
                info = info,
                // Selection is an explicit user decision. Discovery may refresh
                // metadata, but it must never infer a destination calendar.
                isSelected = previous[info.href]?.isSelected ?: false,
                updatedAt = now
            )
        }
        calendarDao.upsertAll(entities)
        if (calendars.isNotEmpty()) {
            calendarDao.deleteStale(accountId, entities.map { it.href })
        }
    }

    override suspend fun selectWorkingCalendar(accountId: String, href: String) {
        database.withTransaction {
            val previouslySelected = calendarDao.getSelected(accountId)?.href
            calendarDao.selectWorkingCalendar(accountId, href)
            if (previouslySelected != null && previouslySelected != href) {
                eventDao.deleteAllForCalendar(accountId, previouslySelected)
            }
            syncStateDao.deleteForAccount(accountId)
        }
    }

    override fun observeCalendars(accountId: String): Flow<List<CalendarInfo>> =
        calendarDao.observeForAccount(accountId).map { list -> list.map { it.toDomain() } }

    override fun observeSelectedCalendar(): Flow<CalendarInfo?> =
        accountDao.observeAll()
            .map { it.firstOrNull()?.id }
            .distinctUntilChanged()
            .flatMapLatest { id ->
                if (id == null) flowOf(null) else calendarDao.observeSelected(id).map { it?.toDomain() }
            }

    override suspend fun disconnectLocal() {
        database.withTransaction {
            accountDao.getAll().forEach { account ->
                eventDao.deleteAllForAccount(account.id)
                syncStateDao.deleteForAccount(account.id)
                calendarDao.deleteForAccount(account.id)
            }
            accountDao.deleteAll()
            // service_orders (local drafts) are never touched here.
        }
    }
}
