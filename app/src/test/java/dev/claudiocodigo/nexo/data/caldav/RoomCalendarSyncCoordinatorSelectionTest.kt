package dev.claudiocodigo.nexo.data.caldav

import dev.claudiocodigo.nexo.data.local.NexoDatabase
import dev.claudiocodigo.nexo.data.local.dao.CalendarSyncStateDao
import dev.claudiocodigo.nexo.data.local.dao.RemoteEventDao
import dev.claudiocodigo.nexo.domain.caldav.AccountIdentity
import dev.claudiocodigo.nexo.domain.caldav.CalDavReadClient
import dev.claudiocodigo.nexo.domain.caldav.CalendarInfo
import dev.claudiocodigo.nexo.domain.caldav.CredentialStore
import dev.claudiocodigo.nexo.domain.caldav.SyncOutcome
import dev.claudiocodigo.nexo.domain.repository.CalendarSetupRepository
import dev.claudiocodigo.nexo.domain.time.ClockProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.mockito.Mockito.mock

class RoomCalendarSyncCoordinatorSelectionTest {

    @Test
    fun syncWithoutExplicitCalendarSelectionPerformsNoRemoteRequest() = runTest {
        var readAppPasswordCalled = false
        val fakeCredentialStore = object : CredentialStore {
            override suspend fun saveAccount(server: String, user: String) = Unit
            override suspend fun saveAppPassword(password: CharArray) = Unit
            override suspend fun readAccount(): AccountIdentity = AccountIdentity("https://cloud.example.test", "maria")
            override suspend fun readAppPassword(): CharArray? {
                readAppPasswordCalled = true
                return null
            }
            override suspend fun clear() = Unit
            override fun hasAccount(): Flow<Boolean> = flowOf(true)
            override fun observeAccount(): Flow<AccountIdentity?> = flowOf(AccountIdentity("https://cloud.example.test", "maria"))
        }

        val fakeSetupRepository = object : CalendarSetupRepository {
            override suspend fun ensureAccount(server: String, user: String) = "account-1"
            override suspend fun getActiveAccountId(): String? = "account-1"
            override fun observeSelectedCalendar(): Flow<CalendarInfo?> = flowOf(null)
            override suspend fun saveCalendars(accountId: String, calendars: List<CalendarInfo>) = Unit
            override suspend fun selectWorkingCalendar(accountId: String, href: String) = Unit
            override fun observeCalendars(accountId: String): Flow<List<CalendarInfo>> = flowOf(emptyList())
            override suspend fun disconnectLocal() = Unit
        }

        val coordinator = RoomCalendarSyncCoordinator(
            database = mock(NexoDatabase::class.java),
            credentialStore = fakeCredentialStore,
            setupRepository = fakeSetupRepository,
            readClient = mock(CalDavReadClient::class.java),
            eventDao = mock(RemoteEventDao::class.java),
            syncStateDao = mock(CalendarSyncStateDao::class.java),
            clock = mock(ClockProvider::class.java)
        )

        assertEquals(SyncOutcome.SkippedNoAccount, coordinator.syncNow())
        assertFalse(readAppPasswordCalled)
    }
}
