package dev.claudiocodigo.nexo.data.caldav

import androidx.room.withTransaction
import dev.claudiocodigo.nexo.data.local.NexoDatabase
import dev.claudiocodigo.nexo.data.local.dao.RemoteEventDao
import dev.claudiocodigo.nexo.data.local.dao.CalendarSyncStateDao
import dev.claudiocodigo.nexo.data.local.entity.CalendarSyncStateEntity
import dev.claudiocodigo.nexo.data.local.entity.RemoteEventEntity
import dev.claudiocodigo.nexo.domain.caldav.CalDavCredentials
import dev.claudiocodigo.nexo.domain.caldav.CalDavReadClient
import dev.claudiocodigo.nexo.domain.caldav.CalendarSyncCoordinator
import dev.claudiocodigo.nexo.domain.caldav.CredentialStore
import dev.claudiocodigo.nexo.domain.caldav.FailureKind
import dev.claudiocodigo.nexo.domain.caldav.SyncOutcome
import dev.claudiocodigo.nexo.domain.caldav.SyncCollectionUnsupportedException
import dev.claudiocodigo.nexo.domain.caldav.InvalidSyncTokenException
import dev.claudiocodigo.nexo.domain.repository.CalendarSetupRepository
import dev.claudiocodigo.nexo.domain.time.ClockProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Read-only synchronization of the selected work calendar into the Room mirror.
 *
 * It only reads (list, report, get) and writes to the LOCAL cache. It never
 * issues a remote write, never deletes a local draft, refuses concurrent syncs
 * (per calendar) and keeps the last consistent view on any partial failure.
 */
@Singleton
class RoomCalendarSyncCoordinator @Inject constructor(
    private val database: NexoDatabase,
    private val credentialStore: CredentialStore,
    private val setupRepository: CalendarSetupRepository,
    private val readClient: CalDavReadClient,
    private val eventDao: RemoteEventDao,
    private val syncStateDao: CalendarSyncStateDao,
    private val clock: ClockProvider
) : CalendarSyncCoordinator {

    private val _isSyncing = MutableStateFlow(false)
    override val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val syncMutex = Mutex()

    override suspend fun syncNow(): SyncOutcome {
        if (!syncMutex.tryLock()) return SyncOutcome.AlreadyRunning
        try {
            return doSync()
        } finally {
            syncMutex.unlock()
        }
    }

    private suspend fun doSync(): SyncOutcome {
        val accountId = setupRepository.getActiveAccountId() ?: return SyncOutcome.SkippedNoAccount
        val calendar = setupRepository.observeSelectedCalendar().first() ?: return SyncOutcome.SkippedNoAccount

        val identity = credentialStore.readAccount() ?: return SyncOutcome.SkippedNoAccount
        val password = credentialStore.readAppPassword() ?: return SyncOutcome.SkippedNoAccount

        val credentials = CalDavCredentials(identity.server, identity.user, password)
        password.fill('\u0000')

        _isSyncing.value = true
        val previous = syncStateDao.get(accountId, calendar.href)
        try {
            val outcome = if (previous?.syncToken != null) {
                try {
                    val delta = readClient.syncCollection(calendar.href, previous.syncToken, credentials)
                    if (!delta.complete || delta.newToken.isNullOrBlank()) {
                        throw CalDavParseException(delta.errorMessage ?: "Resposta incremental inconclusiva")
                    }
                    applyDelta(accountId, calendar.href, credentials, previous, delta)
                } catch (_: SyncCollectionUnsupportedException) {
                    fullSync(accountId, calendar.href, credentials, previous)
                } catch (_: InvalidSyncTokenException) {
                    fullSync(accountId, calendar.href, credentials, previous)
                }
            } else {
                fullSync(accountId, calendar.href, credentials, previous)
            }
            return outcome
        } catch (e: Exception) {
            val kind = mapException(e)
            database.withTransaction {
                syncStateDao.upsert(
                    CalendarSyncStateEntity(
                        accountId = accountId,
                        calendarHref = calendar.href,
                        lastSyncMillis = clock.nowMillis(),
                        lastSuccessMillis = previous?.lastSuccessMillis,
                        lastResult = if (kind == FailureKind.UNAUTHORIZED) RESULT_UNAUTHENTICATED else RESULT_ERROR,
                        lastErrorMessage = e.message,
                        syncToken = previous?.syncToken
                    )
                )
            }
            return SyncOutcome.Failure(kind, e.message ?: "Falha na sincronização")
        } finally {
            _isSyncing.value = false
            credentials.wipe()
        }
    }

    private fun mapException(e: Exception): FailureKind = when {
        e is CalDavOriginException -> FailureKind.REDIRECT_INSECURE
        e is CalDavHttpException -> when (e.statusCode) {
            401 -> FailureKind.UNAUTHORIZED
            403 -> FailureKind.FORBIDDEN
            404 -> FailureKind.NOT_FOUND
            else -> FailureKind.UNKNOWN
        }
        e is CalDavParseException -> FailureKind.PARSE
        e is java.net.UnknownHostException || e is java.net.SocketTimeoutException || e is java.net.ConnectException ->
            FailureKind.NETWORK
        e is javax.net.ssl.SSLException -> FailureKind.TLS_INVALID
        else -> FailureKind.UNKNOWN
    }

    private suspend fun fullSync(
        accountId: String,
        calendarHref: String,
        credentials: CalDavCredentials,
        previous: CalendarSyncStateEntity?
    ): SyncOutcome {
        val listing = readClient.listHrefAndEtagsResult(calendarHref, credentials)
        if (!listing.complete) throw CalDavParseException(listing.errorMessage ?: "Resposta CalDAV inconclusiva")
        val serverResources = listing.resources.map { it.copy(href = canonicalHref(calendarHref, it.href)) }
        val currentHrefs = serverResources.map { it.href }.toSet()
        val existing = eventDao.getAllForCalendar(accountId, calendarHref).associateBy { canonicalHref(calendarHref, it.href) }
        val changedHrefs = serverResources.filter { existing[it.href] == null || existing[it.href]?.etag != it.etag }.map { it.href }
        val fetched = fetchCanonical(calendarHref, changedHrefs, credentials)
        val now = clock.nowMillis()
        val remoteEvents = fetched.mapNotNull { RemoteEventMapper.map(it, accountId, calendarHref, now) }
        if (remoteEvents.size != fetched.size) throw CalDavParseException("Evento CalDAV inválido ou incompleto")
        val removed = existing.keys.filter { it !in currentHrefs }
        applyCache(accountId, calendarHref, existing, remoteEvents, removed, listing.syncToken, now)
        return SyncOutcome.Success(
            added = remoteEvents.count { existing[it.href] == null },
            updated = remoteEvents.count { existing[it.href] != null },
            removed = removed.size,
            token = listing.syncToken
        )
    }

    private suspend fun applyDelta(
        accountId: String,
        calendarHref: String,
        credentials: CalDavCredentials,
        previous: CalendarSyncStateEntity,
        delta: dev.claudiocodigo.nexo.domain.caldav.SyncCollectionResult
    ): SyncOutcome {
        val changed = delta.changed.map { it.copy(href = canonicalHref(calendarHref, it.href)) }.distinctBy { it.href }
        val removedHrefs = delta.removed.map { canonicalHref(calendarHref, it) }.toSet()
        val existing = eventDao.getAllForCalendar(accountId, calendarHref).associateBy { canonicalHref(calendarHref, it.href) }
        val fetched = fetchCanonical(calendarHref, changed.map { it.href }, credentials)
        val now = clock.nowMillis()
        val remoteEvents = fetched.mapNotNull { RemoteEventMapper.map(it, accountId, calendarHref, now) }
        if (remoteEvents.size != fetched.size) throw CalDavParseException("Evento CalDAV inválido ou incompleto")
        val actuallyRemoved = removedHrefs.intersect(existing.keys)
        applyCache(accountId, calendarHref, existing, remoteEvents, actuallyRemoved.toList(), delta.newToken, now)
        return SyncOutcome.Success(
            added = remoteEvents.count { existing[it.href] == null },
            updated = remoteEvents.count { existing[it.href] != null },
            removed = actuallyRemoved.size,
            token = delta.newToken
        )
    }

    private suspend fun fetchCanonical(
        calendarHref: String,
        hrefs: List<String>,
        credentials: CalDavCredentials
    ): List<dev.claudiocodigo.nexo.domain.caldav.EventResource> {
        val fetched = mutableListOf<dev.claudiocodigo.nexo.domain.caldav.EventResource>()
        for (chunk in hrefs.chunked(BATCH_SIZE)) fetched += readClient.fetchEvents(calendarHref, chunk, credentials)
        val canonical = fetched.map { it.copy(href = canonicalHref(calendarHref, it.href)) }
        if (canonical.map { it.href }.toSet() != hrefs.toSet()) throw CalDavParseException("Resposta parcial ao buscar eventos CalDAV")
        return canonical
    }

    private suspend fun applyCache(
        accountId: String,
        calendarHref: String,
        existing: Map<String, RemoteEventEntity>,
        remoteEvents: List<dev.claudiocodigo.nexo.domain.caldav.RemoteEvent>,
        removed: Collection<String>,
        token: String?,
        now: Long
    ) {
        val legacyRowsReplaced = remoteEvents.mapNotNull { event -> existing[event.href]?.takeIf { it.href != event.href } }
        database.withTransaction {
            legacyRowsReplaced.forEach { eventDao.deleteByHref(accountId, calendarHref, it.href) }
            eventDao.upsertAll(remoteEvents.map(RemoteEventEntity::fromDomain))
            removed.forEach { eventDao.deleteByHref(accountId, calendarHref, existing[it]?.href ?: it) }
            syncStateDao.upsert(CalendarSyncStateEntity(accountId, calendarHref, now, now, RESULT_SUCCESS, null, token))
        }
    }

    private fun canonicalHref(base: String, href: String): String {
        try {
            val resolved = CalDavXmlParser.resolveHref(base, href)
            val baseUri = java.net.URI(base)
            val targetUri = java.net.URI(resolved)
            val baseScheme = baseUri.scheme ?: throw CalDavOriginException("Origem sem esquema")
            val targetScheme = targetUri.scheme ?: throw CalDavOriginException("Href sem esquema")
            val baseHost = baseUri.host ?: throw CalDavOriginException("Origem sem host")
            val targetHost = targetUri.host ?: throw CalDavOriginException("Href sem host")
            val basePort = if (baseUri.port != -1) baseUri.port else if (baseScheme.equals("https", true)) 443 else 80
            val targetPort = if (targetUri.port != -1) targetUri.port else if (targetScheme.equals("https", true)) 443 else 80
            if (!baseScheme.equals(targetScheme, true) || !baseHost.equals(targetHost, true) || basePort != targetPort) {
                throw CalDavOriginException("URL CalDAV fora da origem configurada")
            }
            return resolved
        } catch (e: CalDavOriginException) {
            throw e
        } catch (_: Exception) {
            throw CalDavOriginException("URL CalDAV inválida")
        }
    }

    companion object {
        private const val BATCH_SIZE = 128
        private const val RESULT_SUCCESS = "success"
        private const val RESULT_ERROR = "error"
        private const val RESULT_UNAUTHENTICATED = "unauthenticated"
    }
}
