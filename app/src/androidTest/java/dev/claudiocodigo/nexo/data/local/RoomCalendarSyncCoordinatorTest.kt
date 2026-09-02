package dev.claudiocodigo.nexo.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.claudiocodigo.nexo.data.caldav.CalDavHttpException
import dev.claudiocodigo.nexo.data.caldav.RemoteEventMapper
import dev.claudiocodigo.nexo.data.caldav.RoomCalendarSyncCoordinator
import dev.claudiocodigo.nexo.data.repository.RoomCalendarSetupRepository
import dev.claudiocodigo.nexo.data.local.entity.*
import dev.claudiocodigo.nexo.domain.caldav.*
import dev.claudiocodigo.nexo.domain.repository.CalendarSetupRepository
import dev.claudiocodigo.nexo.domain.time.ClockProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RoomCalendarSyncCoordinatorTest {
    private lateinit var db: NexoDatabase
    private lateinit var reader: FakeReader
    private val id = "account-1"
    private val href = "https://cloud.example.test/cal/work/"
    private val calendar = CalendarInfo(href, "Work", null, null, true, false, null)

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), NexoDatabase::class.java).allowMainThreadQueries().build()
        db.calendarAccountDao().upsert(CalendarAccountEntity(id, "https://cloud.example.test", "maria", 1, 1))
        db.calendarDao().upsertAll(listOf(CalendarEntity.fromDomain(id, calendar, true, 1)))
        reader = FakeReader()
    }
    @After fun tearDown() { db.close() }
    private fun coordinator() = RoomCalendarSyncCoordinator(db, FakeStore(), FakeSetup(id, calendar), reader, db.remoteEventDao(), db.calendarSyncStateDao(), FixedClock(10_000))
    private fun ics(summary: String) = "BEGIN:VCALENDAR\nVERSION:2.0\nBEGIN:VEVENT\nUID:${summary.lowercase()}\nSUMMARY:$summary\nDTSTART:20260826T100000Z\nDTEND:20260826T110000Z\nEND:VEVENT\nEND:VCALENDAR"
    private fun event(path: String, etag: String?, summary: String) = RemoteEventEntity.fromDomain(RemoteEventMapper.map(EventResource(path, etag, ics(summary)), id, href, 1)!!)

    @Test fun invalidListingPreservesCache() = runBlocking {
        db.remoteEventDao().upsertAll(listOf(event("/cal/work/old.ics", "1", "Old")))
        reader.listing = ResourceListing(emptyList(), false, "invalid")
        val out = coordinator().syncNow() as SyncOutcome.Failure
        assertEquals(FailureKind.PARSE, out.kind)
        assertEquals(1, db.remoteEventDao().getAllForCalendar(id, href).size)
    }
    @Test fun unauthorizedPreservesCacheAndMarksState() = runBlocking {
        db.remoteEventDao().upsertAll(listOf(event("/cal/work/old.ics", "1", "Old")))
        reader.failure = CalDavHttpException(401, "unauthorized")
        val out = coordinator().syncNow() as SyncOutcome.Failure
        assertEquals(FailureKind.UNAUTHORIZED, out.kind)
        assertEquals(1, db.remoteEventDao().getAllForCalendar(id, href).size)
        assertEquals("unauthenticated", db.calendarSyncStateDao().get(id, href)?.lastResult)
    }
    @Test fun changeAndRemovalAreApplied() = runBlocking {
        db.remoteEventDao().upsertAll(listOf(event("/cal/work/e1.ics", "1", "Old"), event("/cal/work/e2.ics", "1", "Removed")))
        reader.listing = ResourceListing(listOf(ResourceEtag("/cal/work/e1.ics", "2"), ResourceEtag("/cal/work/e3.ics", null)), true)
        reader.fetched = listOf(EventResource("/cal/work/e1.ics", "2", ics("Changed")), EventResource("/cal/work/e3.ics", null, ics("Added")))
        val out = coordinator().syncNow() as SyncOutcome.Success
        assertEquals(1, out.added); assertEquals(1, out.updated); assertEquals(1, out.removed)
        val events = db.remoteEventDao().getAllForCalendar(id, href)
        assertEquals(2, events.size)
        assertEquals(2, events.map { it.href }.toSet().size)
        assertEquals(setOf("https://cloud.example.test/cal/work/e1.ics", "https://cloud.example.test/cal/work/e3.ics"), events.map { it.href }.toSet())
    }
    @Test fun newResourceWithoutEtagIsStored() = runBlocking {
        reader.listing = ResourceListing(listOf(ResourceEtag("/cal/work/new.ics", null)), true)
        reader.fetched = listOf(EventResource("/cal/work/new.ics", null, ics("New")))
        assertTrue(coordinator().syncNow() is SyncOutcome.Success)
        assertEquals(null, db.remoteEventDao().getAllForCalendar(id, href).single().etag)
    }
    @Test fun partialFetchPreservesCache() = runBlocking {
        db.remoteEventDao().upsertAll(listOf(event("/cal/work/old.ics", "1", "Old")))
        reader.listing = ResourceListing(listOf(ResourceEtag("/cal/work/new.ics", "1")), true)
        val out = coordinator().syncNow() as SyncOutcome.Failure
        assertEquals(FailureKind.PARSE, out.kind)
        assertEquals(setOf("/cal/work/old.ics"), db.remoteEventDao().getAllForCalendar(id, href).map { it.href }.toSet())
    }

    @Test fun incrementalSyncUsesTokenAndAppliesOnlyDelta() = runBlocking {
        db.remoteEventDao().upsertAll(listOf(event("/cal/work/e1.ics", "1", "Old"), event("/cal/work/e2.ics", "1", "Removed")))
        db.calendarSyncStateDao().upsert(CalendarSyncStateEntity(id, href, 1, 1, "success", null, "old-token"))
        reader.delta = SyncCollectionResult("new-token", listOf(ResourceEtag("/cal/work/e1.ics", "2")), setOf("/cal/work/e2.ics"), true)
        reader.fetched = listOf(EventResource("/cal/work/e1.ics", "2", ics("Changed")))

        val out = coordinator().syncNow() as SyncOutcome.Success
        assertEquals("new-token", out.token)
        assertEquals(listOf("https://cloud.example.test/cal/work/e1.ics"), reader.requestedHrefs)
        assertEquals(setOf("https://cloud.example.test/cal/work/e1.ics"), db.remoteEventDao().getAllForCalendar(id, href).map { it.href }.toSet())
        assertEquals("new-token", db.calendarSyncStateDao().get(id, href)?.syncToken)
    }

    @Test fun incrementalResponseWithoutTokenPreservesCacheAndPreviousToken() = runBlocking {
        db.remoteEventDao().upsertAll(listOf(event("/cal/work/old.ics", "1", "Old")))
        db.calendarSyncStateDao().upsert(CalendarSyncStateEntity(id, href, 1, 1, "success", null, "old-token"))
        reader.delta = SyncCollectionResult(null, emptyList(), emptySet(), false, "missing token")

        assertEquals(FailureKind.PARSE, (coordinator().syncNow() as SyncOutcome.Failure).kind)
        assertEquals(setOf("/cal/work/old.ics"), db.remoteEventDao().getAllForCalendar(id, href).map { it.href }.toSet())
        assertEquals("old-token", db.calendarSyncStateDao().get(id, href)?.syncToken)
    }

    @Test fun firstFullSyncPersistsTokenAndNextSyncUsesIncrementalReport() = runBlocking {
        reader.listing = ResourceListing(listOf(ResourceEtag("/cal/work/e1.ics", "1")), true, syncToken = "full-token")
        reader.fetched = listOf(EventResource("/cal/work/e1.ics", "1", ics("First")))
        assertEquals("full-token", (coordinator().syncNow() as SyncOutcome.Success).token)
        assertEquals("full-token", db.calendarSyncStateDao().get(id, href)?.syncToken)

        reader.delta = SyncCollectionResult("next-token", emptyList(), emptySet(), true)
        reader.fetched = emptyList()
        assertEquals("next-token", (coordinator().syncNow() as SyncOutcome.Success).token)
    }

    @Test fun invalidTokenFallsBackToFullListingAndStoresItsToken() = runBlocking {
        db.calendarSyncStateDao().upsert(CalendarSyncStateEntity(id, href, 1, 1, "success", null, "old-token"))
        reader.deltaFailure = InvalidSyncTokenException()
        reader.listing = ResourceListing(emptyList(), true, syncToken = "fresh-token")
        val out = coordinator().syncNow() as SyncOutcome.Success
        assertEquals("fresh-token", out.token)
        assertEquals("fresh-token", db.calendarSyncStateDao().get(id, href)?.syncToken)
    }
}

@RunWith(AndroidJUnit4::class)
class RoomCalendarSetupRepositoryTest {
    private lateinit var db: NexoDatabase
    @Before fun setUp() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), NexoDatabase::class.java).allowMainThreadQueries().build() }
    @After fun tearDown() { db.close() }
    @Test fun switchingAccountRemovesRemoteDataAndPreservesDraft() = runBlocking {
        val old = "old"; val cal = "https://old.test/cal/"
        val repo = RoomCalendarSetupRepository(db, db.calendarAccountDao(), db.calendarDao(), db.remoteEventDao(), db.calendarSyncStateDao())
        db.calendarAccountDao().upsert(CalendarAccountEntity(old, "https://old.test", "old", 1, 1))
        db.calendarDao().upsertAll(listOf(CalendarEntity(old, cal, "Old", null, null, true, false, null, true, 1)))
        db.remoteEventDao().upsertAll(listOf(RemoteEventEntity.fromDomain(RemoteEventMapper.map(EventResource("/old.ics", "1", "BEGIN:VCALENDAR\nBEGIN:VEVENT\nUID:old\nEND:VEVENT\nEND:VCALENDAR"), old, cal, 1)!!)))
        db.calendarSyncStateDao().upsert(CalendarSyncStateEntity(old, cal, 1, 1, "success", null, "t"))
        val draft = UUID.randomUUID(); db.serviceOrderDao().upsertServiceOrder(ServiceOrderEntity(draft, "os", "Draft", "", "PENDENTE", "C", "U", null, 1, 1))
        val newId = repo.ensureAccount("https://new.test", "new")
        assertEquals(newId, db.calendarAccountDao().getAll().single().id)
        assertTrue(db.calendarDao().getForAccount(old).isEmpty()); assertTrue(db.remoteEventDao().getAllForCalendar(old, cal).isEmpty())
        assertTrue(db.calendarSyncStateDao().get(old, cal) == null); assertTrue(db.serviceOrderDao().getServiceOrderById(draft) != null)
    }

    @Test fun discoveryDoesNotSelectCalendarImplicitly() = runBlocking {
        val repo = RoomCalendarSetupRepository(db, db.calendarAccountDao(), db.calendarDao(), db.remoteEventDao(), db.calendarSyncStateDao())
        val accountId = repo.ensureAccount("https://cloud.example.test", "maria")
        val calendars = listOf(
            CalendarInfo("https://cloud.example.test/cal/personal/", "Pessoal", null, null, true, true, null),
            CalendarInfo("https://cloud.example.test/cal/work/", "Trabalho", null, null, true, true, null)
        )

        repo.saveCalendars(accountId, calendars)

        assertEquals(null, db.calendarDao().getSelected(accountId))
    }

    @Test fun discoveryPreservesExplicitSelectionWhileItIsStillValid() = runBlocking {
        val repo = RoomCalendarSetupRepository(db, db.calendarAccountDao(), db.calendarDao(), db.remoteEventDao(), db.calendarSyncStateDao())
        val accountId = repo.ensureAccount("https://cloud.example.test", "maria")
        val selectedHref = "https://cloud.example.test/cal/work/"
        val calendars = listOf(
            CalendarInfo("https://cloud.example.test/cal/personal/", "Pessoal", null, null, true, true, null),
            CalendarInfo(selectedHref, "Trabalho", null, null, true, true, null)
        )
        repo.saveCalendars(accountId, calendars)
        repo.selectWorkingCalendar(accountId, selectedHref)

        repo.saveCalendars(accountId, calendars.map { it.copy(description = "Atualizada") })

        assertEquals(selectedHref, db.calendarDao().getSelected(accountId)?.href)
    }

    @Test fun discoveryClearsSelectionWhenSelectedCalendarNoLongerExists() = runBlocking {
        val repo = RoomCalendarSetupRepository(db, db.calendarAccountDao(), db.calendarDao(), db.remoteEventDao(), db.calendarSyncStateDao())
        val accountId = repo.ensureAccount("https://cloud.example.test", "maria")
        val selectedHref = "https://cloud.example.test/cal/work/"
        val selected = CalendarInfo(selectedHref, "Trabalho", null, null, true, true, null)
        repo.saveCalendars(accountId, listOf(selected))
        repo.selectWorkingCalendar(accountId, selectedHref)

        repo.saveCalendars(
            accountId,
            listOf(CalendarInfo("https://cloud.example.test/cal/other/", "Outra", null, null, true, true, null))
        )

        assertEquals(null, db.calendarDao().getSelected(accountId))
    }
}

private class FixedClock(private val now: Long) : ClockProvider { override fun nowMillis() = now }
private class FakeStore : CredentialStore {
    override suspend fun saveAccount(server: String, user: String) = Unit
    override suspend fun readAccount() = AccountIdentity("https://cloud.example.test", "maria")
    override suspend fun saveAppPassword(password: CharArray) = Unit
    override suspend fun readAppPassword() = "secret".toCharArray()
    override suspend fun clear() = Unit
    override fun hasAccount(): Flow<Boolean> = flowOf(true)
    override fun observeAccount(): Flow<AccountIdentity?> = flowOf(AccountIdentity("https://cloud.example.test", "maria"))
}
private class FakeSetup(private val id: String, private val cal: CalendarInfo) : CalendarSetupRepository {
    override suspend fun ensureAccount(server: String, user: String) = id
    override suspend fun getActiveAccountId() = id
    override suspend fun saveCalendars(accountId: String, calendars: List<CalendarInfo>) = Unit
    override suspend fun selectWorkingCalendar(accountId: String, href: String) = Unit
    override fun observeCalendars(accountId: String): Flow<List<CalendarInfo>> = flowOf(listOf(cal))
    override fun observeSelectedCalendar(): Flow<CalendarInfo?> = flowOf(cal)
    override suspend fun disconnectLocal() = Unit
}
private class FakeReader : CalDavReadClient {
    var listing = ResourceListing(emptyList(), true); var fetched = emptyList<EventResource>(); var failure: Exception? = null
    var delta: SyncCollectionResult? = null
    var deltaFailure: Exception? = null
    var requestedHrefs = emptyList<String>()
    override suspend fun getSyncToken(calendarHref: String, credentials: CalDavCredentials) = null
    override suspend fun listHrefAndEtags(calendarHref: String, credentials: CalDavCredentials) = listing.resources
    override suspend fun listHrefAndEtagsResult(calendarHref: String, credentials: CalDavCredentials): ResourceListing { failure?.let { throw it }; return listing }
    override suspend fun syncCollection(calendarHref: String, syncToken: String, credentials: CalDavCredentials): SyncCollectionResult {
        deltaFailure?.let { throw it }
        return delta ?: throw SyncCollectionUnsupportedException()
    }
    override suspend fun fetchEvents(calendarHref: String, hrefs: List<String>, credentials: CalDavCredentials): List<EventResource> {
        requestedHrefs = hrefs
        return fetched
    }
}
