# Phase 3: Ordens de Serviço e publicação controlada - Pattern Map

**Mapped:** 2026-08-27  
**Files analyzed:** 46 existing files; 38 likely new/modified file groups  
**Analogs found:** 35 / 38

## Scope Used for Mapping

This map follows `03-SPEC.md`, `03-CONTEXT.md`, `03-RESEARCH.md`, and `03-VALIDATION.md`. Real exported ICS files and credentials were not read into fixtures or copied into this document. Phase 3 must preserve the Phase 2 read-only CalDAV boundary while adding a separate, narrow conditional writer.

## File Classification

| New/Modified File or Group | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `domain/serviceorder/ServiceOrderModels.kt` and `domain/model/ServiceOrder.kt` | model | CRUD / transform | `domain/model/ServiceOrder.kt` | role-match |
| `domain/serviceorder/ServiceOrderExtractor.kt` | utility | transform | `data/ical/IcsParser.kt` | data-flow match |
| `domain/serviceorder/ServiceOrderRenderer.kt` | utility | transform | `data/ical/IcsParser.kt` | partial; inverse direction |
| `domain/serviceorder/ServiceOrderDiff.kt` | utility | transform | no close analog | none |
| `domain/publication/PublicationModels.kt` | model | event-driven | `domain/caldav/CalendarSyncState.kt` | role-match |
| `domain/caldav/CalDavWriteClient.kt` | service contract | request-response | `domain/caldav/CalDavReadClient.kt` | exact role; opposite capability |
| `data/local/entity/*ServiceOrder*.kt`, `RemoteEventOccurrenceEntity.kt`, `PublicationOutboxEntity.kt` | model | CRUD / event-driven | `ServiceOrderEntity.kt`, `RemoteEventEntity.kt`, `CalendarSyncStateEntity.kt` | role-match |
| `data/local/dao/ServiceOrder*Dao.kt`, `PublicationOutboxDao.kt`, `RemoteEventOccurrenceDao.kt` | store | CRUD / streaming | `ServiceOrderDao.kt`, `RemoteEventDao.kt` | exact |
| `data/local/NexoDatabase.kt`, `NexoDatabaseMigrations.kt`, `di/DatabaseModule.kt` | config / migration | CRUD | existing files of same name | exact |
| `data/repository/RoomServiceOrderRepository.kt` and publication repository/coordinator | service | CRUD / event-driven | `RoomServiceOrderRepository.kt`, `RoomCalendarSyncCoordinator.kt` | exact/role-match |
| `data/ical/IcsDocument.kt`, `IcsDocumentEditor.kt` | utility/model | file-I/O / transform | `IcsModel.kt`, `IcsParser.kt` | role-match; new lossless responsibility |
| `data/caldav/NextcloudCalDavWriteClient.kt` | service | request-response | `NextcloudCalDavReadClient.kt`, `CalDavHttpClient.kt` | role-match |
| `data/caldav/CalDavXmlParser.kt` | utility | transform | same file | exact modification |
| `data/worker/PublicationWorker.kt`, `PublicationScheduler.kt` | service | batch / event-driven | `SyncWorker.kt`, `SyncScheduler.kt` | exact role/data flow |
| `ui/screens/oseditor/*` and evolution of `nova/`/`detalhes/` | component / ViewModel | CRUD / event-driven | `NovaOSScreen.kt`, `NovaOSViewModel.kt`, `DetalhesViewModel.kt` | exact role |
| `ui/screens/preview/*` | component / ViewModel | request-response | `DetalhesScreen.kt` confirmation flow | role-match |
| `ui/screens/conflito/*` | component / ViewModel | event-driven / transform | no close screen analog | none |
| `ui/screens/sincronizacoes/*` | component / ViewModel | streaming / event-driven | `AgendaScreen.kt`, `AgendaViewModel.kt` | role-match |
| `RemoteEventDetail*`, `Hoje*`, `Agenda*`, `MaisScreen.kt` | component / ViewModel | streaming / navigation | same files | exact modification |
| `Routes.kt`, `MainActivity.kt` | route / config | event-driven | same files | exact modification |
| Phase 3 JVM, Room, HTTP, ViewModel, navigation and security tests | test | all above | existing parallel tests | exact role |

## Pattern Assignments

### Room entities, DAOs, repositories, and migration

**Primary analogs:**

- `app/src/main/java/dev/claudiocodigo/nexo/data/local/entity/RemoteEventEntity.kt:17-46`
- `app/src/main/java/dev/claudiocodigo/nexo/data/local/dao/RemoteEventDao.kt:9-29`
- `app/src/main/java/dev/claudiocodigo/nexo/data/repository/RoomServiceOrderRepository.kt:12-29`
- `app/src/main/java/dev/claudiocodigo/nexo/data/local/NexoDatabaseMigrations.kt:13-52`

**Composite identity and non-unique UID pattern** (`RemoteEventEntity.kt:17-24`):

```kotlin
@Entity(
    tableName = "remote_events",
    primaryKeys = ["accountId", "calendarHref", "href"],
    indices = [Index("uid"), Index("start"), Index("calendarHref")]
)
```

Apply this explicit-identity style to the remote occurrence and OS link. The resource identity remains `accountId + calendarHref + href`; occurrence identity additionally carries normalized `RECURRENCE-ID`. Do not make UID unique.

**Streaming read plus suspend mutation pattern** (`ServiceOrderDao.kt:10-22`):

```kotlin
@Dao
interface ServiceOrderDao {
    @Query("SELECT * FROM service_orders ORDER BY scheduledDate ASC")
    fun getAllServiceOrders(): Flow<List<ServiceOrderEntity>>

    @Query("SELECT * FROM service_orders WHERE id = :id")
    suspend fun getServiceOrderById(id: UUID): ServiceOrderEntity?

    @Upsert
    suspend fun upsertServiceOrder(serviceOrder: ServiceOrderEntity)
}
```

Use `Flow` for editor/card/central observation and `suspend` methods for atomic commands. Outbox needs dedicated claim/transition queries instead of a generic `@Upsert` for state changes. Add unique indexes for one link per remote occurrence, one publication operation ID, and one published version per operation.

**Entity/domain mapping boundary** (`ServiceOrderEntity.kt:22-50`):

```kotlin
fun toDomain() = ServiceOrder(...)

companion object {
    fun fromDomain(domain: ServiceOrder) = ServiceOrderEntity(...)
}
```

Keep Room annotations and storage strings out of domain models. Enum parsing must remain tolerant of older/corrupt data, as shown at `ServiceOrderEntity.kt:27-30`.

**Repository as local source of truth** (`RoomServiceOrderRepository.kt:12-25`):

```kotlin
class RoomServiceOrderRepository @Inject constructor(
    private val dao: ServiceOrderDao
) : ServiceOrderRepository {
    override fun getServiceOrders(): Flow<List<ServiceOrder>> =
        dao.getAllServiceOrders().map { entities -> entities.map(ServiceOrderEntity::toDomain) }

    override suspend fun saveServiceOrder(serviceOrder: ServiceOrder) {
        dao.upsertServiceOrder(ServiceOrderEntity.fromDomain(serviceOrder))
    }
}
```

The structured draft, updates, items, snapshots, versions and publication operations must all reach Room before network work. DataStore may retain recent technician/company selections only; it is not canonical OS storage.

**Explicit non-destructive migration pattern** (`NexoDatabaseMigrations.kt:15-52`):

```kotlin
val MIGRATION_1_2_SQL: List<String> = listOf(
    "CREATE TABLE IF NOT EXISTS ...",
    "CREATE INDEX IF NOT EXISTS ..."
)

val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        MIGRATION_1_2_SQL.forEach { db.execSQL(it) }
    }
}
```

Create an explicit `MIGRATION_2_3`; retain `MIGRATION_1_2`; register both in `DatabaseModule.kt:25-32`; export schema 3. The Phase 3 migration must preserve legacy `service_orders` rows and all Phase 2 cache rows. Prefer additive tables and columns; do not use destructive fallback.

### Service-order extraction, rendering, and semantic diff

**Primary analog:** `app/src/main/java/dev/claudiocodigo/nexo/data/ical/IcsParser.kt:20-66,68-109,180-241,271-295`.

The current parser demonstrates the required pure, dependency-free transform style:

```kotlin
object IcsParser {
    fun parse(rawIcs: String): IcsCalendar {
        val lines = unfold(rawIcs)
        // best-effort typed extraction
        return IcsCalendar(rawIcs = rawIcs, ...)
    }
}
```

Constraints for new domain utilities:

- Keep extraction/rendering/diff as pure Kotlin objects or classes with injected clock only where timestamps are required.
- Preserve raw SUMMARY/DESCRIPTION alongside extracted fields.
- Parse labels on the first unescaped/structural `:`; never use `split(':').last()`.
- `????` and `SEM OS` map to absent official number.
- Unstructured DESCRIPTION becomes the complete preset-origin field; do not rewrite it or invoke an LLM.
- Updates keep UUID identity and stable ordering by execution time, creation time, then UUID. Equal text does not imply duplicate records.
- Preview and final rendering are deterministic functions of an immutable structured snapshot.
- Final rendering omits the remote update timeline but never deletes local update entities.

No close diff analog exists. `ServiceOrderDiff` should return typed field differences (`base`, `local`, `remote`) and never mutate the draft while calculating.

### Lossless ICS document and surgical editor

**Primary analogs:**

- `app/src/main/java/dev/claudiocodigo/nexo/data/ical/IcsModel.kt:3-16`
- `app/src/main/java/dev/claudiocodigo/nexo/data/ical/IcsParser.kt:182-229,271-295`
- `app/src/main/java/dev/claudiocodigo/nexo/data/caldav/RemoteEventMapper.kt:21-47`

**Raw-preservation contract** (`IcsModel.kt:3-16`):

```kotlin
data class IcsCalendar(
    val rawIcs: String,
    val prodId: String?,
    val version: String?,
    val calendarColor: String?,
    val events: List<IcsEvent>
)
```

The typed model is an extraction view, not a writable document. Do not rebuild ICS from `IcsEvent`: it lacks ordered unknown properties, participants, alarms and sibling components.

New `IcsDocument` should retain physical lines/component boundaries and expose a selector by `UID + RECURRENCE-ID`. `IcsDocumentEditor` may replace/add only `DESCRIPTION`, `SEQUENCE`, `DTSTAMP`, and `LAST-MODIFIED` inside the selected VEVENT. It must preserve every other physical line exactly, including `VTIMEZONE`, `ORGANIZER`, `ATTENDEE`, `VALARM`, `ATTACH`, X-properties, parameters, siblings, order, and absent `COLOR`/`LOCATION`.

The current mapper is a known anti-pattern for Phase 3 targeting (`RemoteEventMapper.kt:21-25`):

```kotlin
val event = calendar.events.firstOrNull { it.recurrenceId == null }
    ?: calendar.events.firstOrNull()
```

Do not copy that master-first selection into the writer. Extend the cache with explicit occurrences and link to the exact target.

Reuse the current escape semantics (`IcsParser.kt:271-295`) in inverse form, but folding for newly emitted lines must count UTF-8 octets and emit CRLF + one space. Unchanged lines should not be unfolded/refolded.

### Conditional CalDAV writer

**Primary analogs:**

- `app/src/main/java/dev/claudiocodigo/nexo/domain/caldav/CalDavReadClient.kt:3-45`
- `app/src/main/java/dev/claudiocodigo/nexo/data/caldav/CalDavHttpClient.kt:24-35,68-95,99-115`
- `app/src/main/java/dev/claudiocodigo/nexo/data/caldav/NextcloudCalDavReadClient.kt:120-160`

**Narrow typed contract pattern** (`CalDavReadClient.kt:3-10`):

```kotlin
/** Read-only CalDAV client ... */
interface CalDavReadClient {
    // only explicit read operations
}
```

Create a separate `CalDavWriteClient` with only `create(ConditionalCreate)` and `update(ConditionalUpdate)`. The update request type must require a nonblank base ETag; create must always mean `If-None-Match: *`. Do not expose a generic method, DELETE, color mutation, or unconditional PUT.

**Same-origin authorization and redirect pattern** (`CalDavHttpClient.kt:68-95,99-102`):

```kotlin
private fun execute(request: Request): Response {
    var next = request
    repeat(MAX_REDIRECTS + 1) {
        val built = authorize(next)
        expectedOrigin?.let { expected ->
            val actual = Origin(next.url.scheme, next.url.host, next.url.port)
            if (actual != expected) throw CalDavOriginException(...)
        }
        val response = client.newCall(built).execute()
        // follow manually only when target remains same HTTPS origin
    }
}
```

Copy the origin, HTTPS redirect, timeout, and delayed-Authorization protections into a writer-specific HTTP adapter. Do **not** add `PUT` to `HttpMethodAllowlist.ALLOWED` (`HttpMethodAllowlist.kt:9-24`) and do not install `HttpMethodGuardInterceptor` in the writer.

**Credential handling pattern** (`NextcloudCalDavReadClient.kt:153-160`):

```kotlin
val password = credentials.appPassword()
val bytes = "${credentials.user}:${String(password)}".toByteArray(Charsets.UTF_8)
password.fill('\u0000')
return CalDavHttpClient().withAuthorization(
    "Basic " + Base64.getEncoder().encodeToString(bytes),
    credentials.server
)
```

Reuse the existing CredentialStore. Never persist password/Authorization in outbox, versions, previews or logs. Map 200/201/204, 401, 403, 409, 412, 5xx and network failures to typed outcomes. A 412 is a persisted conflict and never an automatic PUT retry.

### XML privilege parsing

**Analog:** `app/src/main/java/dev/claudiocodigo/nexo/data/caldav/CalDavXmlParser.kt:26-39,101-160,166-195`.

The parser already preserves exact privilege local names:

```kotlin
val privileges = linkedSetOf<String>()
// ...
val inner = privEl.firstElementChildNode() ?: continue
privileges.add(inner.localName ?: inner.nodeName)
```

The information loss happens later at `CalDavXmlParser.kt:90-98`:

```kotlin
hasWritePrivilege = entry.privileges.any { it.contains("write") }
```

Extend the domain/storage calendar capability model to retain the exact set. Update requires `write-content`; create requires `bind` on the collection. These are a preflight hint only: HTTP 403 remains authoritative. Preserve the namespace-aware, DOCTYPE-rejecting parser defenses at `CalDavXmlParser.kt:166-195`.

### Publication outbox, coordinator, worker, and scheduler

**Primary analogs:**

- `app/src/main/java/dev/claudiocodigo/nexo/data/caldav/RoomCalendarSyncCoordinator.kt:35-58,60-108,188-203`
- `app/src/main/java/dev/claudiocodigo/nexo/data/worker/SyncWorker.kt:21-35`
- `app/src/main/java/dev/claudiocodigo/nexo/data/worker/SyncScheduler.kt:23-58`

**Coordinator concurrency and transaction pattern** (`RoomCalendarSyncCoordinator.kt:49-58,91-103`):

```kotlin
private val syncMutex = Mutex()

override suspend fun syncNow(): SyncOutcome {
    if (!syncMutex.tryLock()) return SyncOutcome.AlreadyRunning
    try { return doSync() } finally { syncMutex.unlock() }
}

database.withTransaction {
    syncStateDao.upsert(...)
}
```

For publication, process-local Mutex is only defense in depth. The canonical claim must be a Room transaction with a persisted lease/timestamp so process death can recover `SENDING`. Confirming a preview must atomically insert an immutable intended version and one unique outbox operation referencing it.

**Typed worker result pattern** (`SyncWorker.kt:28-35`):

```kotlin
override suspend fun doWork(): Result = when (val outcome = coordinator.syncNow()) {
    is SyncOutcome.Success -> Result.success()
    is SyncOutcome.AlreadyRunning -> Result.retry()
    is SyncOutcome.Failure ->
        if (outcome.kind == FailureKind.NETWORK || outcome.kind == FailureKind.TIMEOUT) Result.retry()
        else Result.failure()
}
```

Publication mapping differs deliberately: empty/sent/conflict/permanent failure return success after state is persisted; only network/timeout returns retry. WorkManager input should contain only operation ID (or no payload for a queue drain), never raw ICS.

**Unique connected work pattern** (`SyncScheduler.kt:42-52`):

```kotlin
val request = OneTimeWorkRequestBuilder<SyncWorker>()
    .setConstraints(networkConstraint())
    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
    .build()
workManager.enqueueUniqueWork(MANUAL_WORK, ExistingWorkPolicy.KEEP, request)
```

Use one unique publication drain work name with `NetworkType.CONNECTED`. Room, not WorkManager, owns queue identity, cancellation, state and history.

### Structured editor ViewModel and Compose sections

**Primary analogs:**

- `app/src/main/java/dev/claudiocodigo/nexo/ui/screens/nova/NovaOSViewModel.kt:25-69,88-163`
- `app/src/main/java/dev/claudiocodigo/nexo/ui/screens/detalhes/DetalhesViewModel.kt:37-94,120-132,168-198`
- `app/src/main/java/dev/claudiocodigo/nexo/ui/screens/nova/NovaOSScreen.kt:44-100,102-185`

**Single immutable screen state and autosave pattern** (`NovaOSViewModel.kt:25-52`):

```kotlin
data class NovaOSFormState(...)

private val _form = MutableStateFlow(initialState())
val form: StateFlow<NovaOSFormState> = _form.asStateFlow()
```

**Revision-safe debounce pattern** (`NovaOSViewModel.kt:88-107,135-161`):

```kotlin
private fun update(transform: (NovaOSFormState) -> NovaOSFormState) {
    val next = transform(_form.value).copy(validationError = null)
    revision++
    _form.value = next.copy(saveState = NovaDraftSaveState.Saving)
    scheduleAutosave()
}

private suspend fun persistCurrent(...): Boolean = saveMutex.withLock {
    val snapshotRevision = revision
    val result = runCatching { repository.saveServiceOrder(snapshot) }
    if (result.isSuccess && snapshotRevision == revision) { ... }
    else { scheduleAutosave(); false }
}
```

Carry this protection into one `ServiceOrderEditorViewModel` shared by provisional and linked orders. Do not let an older Room/network load overwrite a newer edit. SavedStateHandle is a process UI aid; Room remains authoritative.

**Flush-on-exit pattern** (`NovaOSScreen.kt:54-74`):

```kotlin
BackHandler { requestExit() }
DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_STOP) viewModel.flushNow()
    }
    // remove observer and flush on dispose
}
```

Keep this behavior in the new editor. Remove competing manual “save draft” UI: autosave status remains visible, while the fixed bottom action is only the applicable remote action.

The editor screen should be a thin scaffold and split sections into stateless composables, for example `PresetSection`, `IdentificationSection`, `OriginSection`, `UpdatesSection`, `PendingSection`, `ItemsSection`, `CompletionSection`, and `PreviewSection`. Each receives values and callbacks; no section owns a second draft.

### Remote detail, preview, conflict, sync center, and navigation

**Remote detail analog:** `RemoteEventDetailScreen.kt:32-109` and `RemoteEventDetailViewModel.kt:14-35`.

Keep the existing detail read-only and add a bottom action fed by link state: `Iniciar atendimento` creates-or-gets the link; `Continuar atendimento` opens the existing local ID. Loading the remote detail itself must remain read-only.

**Navigation pattern** (`Routes.kt:5-48`, `MainActivity.kt:97-199`):

```kotlin
@Serializable
sealed interface Route {
    @Serializable data class EventoRemoto(
        val accountId: String,
        val calendarHref: String,
        val href: String
    ) : Route
}

NavDisplay(
    backStack = currentBackStack,
    entryProvider = { key -> when (key) { /* NavEntry */ } }
)
```

Add typed routes for editor, preview, conflict and sync center. Pass IDs, not payloads/ICS, through routes. Update `hideBottomBar` for full-screen flows. Preview confirmation should produce the immutable version/outbox transaction, then navigate by outcome.

**List/central analog:** `AgendaViewModel.kt:42-90` plus `AgendaScreen.kt:99-149`. Use combined `Flow`s and keyed `LazyColumn` items. The central shows persisted outbox states and exposes retry/cancel only where allowed.

**Card unification constraint:** `HojeViewModel.kt:29-70` and `AgendaViewModel.kt:42-84` currently expose separate local and remote lists. Phase 3 must project a linked card model so a linked pair is one operational card, while tapping it still opens remote read-only first. Do not delete or merge the underlying Room records.

**Conflict UI:** no close analog exists. Follow the same UDF/state-hoisting style, but model each difference explicitly with base/remote/local values and a manual choice. Applying choices updates the draft/base snapshot only; the blocked operation remains immutable and a new preview/confirmation creates a new operation.

### Color/state mapping

**Analog:** `app/src/main/java/dev/claudiocodigo/nexo/domain/caldav/EventColorMapping.kt:15-34`.

```kotlin
fun classify(raw: String?): EventColor {
    val normalized = raw?.trim()?.lowercase()?.removePrefix("#")?.uppercase()
        ?: return EventColor.NAO_CLASSIFICADO
    return when {
        normalized in GREEN_VARIANTS -> EventColor.VALIDADO
        normalized in RED_VARIANTS -> EventColor.REQUER_ATENCAO
        else -> EventColor.NAO_CLASSIFICADO
    }
}
```

Evolve this into configurable mapping without ever writing color remotely. Seed the observed green variants (`008000`, `228B22`, `32CD32`, `green`, `darkolivegreen`) and red `B22222`; blues remain neutral and `9370DB` unmapped. Red has visual precedence. Local `Aguardando validação externa` and remote `Validada externamente` are distinct states.

## Testing Patterns

### Room migration and DAO tests

**Sources:**

- `app/src/androidTest/java/dev/claudiocodigo/nexo/data/local/NexoDatabaseMigrationTest.kt:23-85`
- `app/src/androidTest/java/dev/claudiocodigo/nexo/data/local/dao/ServiceOrderDaoTest.kt:19-60`
- `app/src/test/java/dev/claudiocodigo/nexo/data/local/NexoDatabaseMigrationsConsistencyTest.kt:12-45`

Use `MigrationTestHelper`, seed real v1/v2 schema rows before migration, run `runMigrationsAndValidate`, and assert byte-level business preservation plus new uniqueness behavior. Add both 1→3 and 2→3 paths. Update the JVM schema-consistency guard for `MIGRATION_2_3_SQL` and schema `3.json`.

Use Room in-memory instrumented tests for link idempotency, occurrence identity, update ordering, version/outbox uniqueness, atomic confirmation, claim lease, cancellation and state transitions.

### Parser/renderer golden tests

**Source:** `app/src/test/java/dev/claudiocodigo/nexo/data/ical/IcsParserTest.kt:9-146`.

Keep inline/fixture inputs anonymous and minimal. Assert raw preservation plus individual semantic fields. Add `app/src/test/resources/ical/` fixtures for Unicode/folding, VTIMEZONE/TZID, master+explicit exception, invited event properties, missing optional color/location, raw DESCRIPTION, and structured DESCRIPTION containing `:`. Never copy the real Downloads files.

For `IcsDocumentEditorTest`, assert unchanged physical lines/components exactly and allowed mutations semantically. Include UTF-8 75-octet folding tests.

### HTTP and security tests

**Sources:**

- `app/src/test/java/dev/claudiocodigo/nexo/data/caldav/CalDavHttpClientTest.kt:15-113`
- `app/src/test/java/dev/claudiocodigo/nexo/data/caldav/NextcloudCalDavReadClientTest.kt:14-180`
- `app/src/test/java/dev/claudiocodigo/nexo/SecurityPolicyTest.kt:16-61`

Use MockWebServer and inspect recorded method, headers and body. Writer tests must prove:

- create sends `If-None-Match: *`;
- update cannot be constructed/called without an ETag and sends `If-Match`;
- no DELETE API/path exists;
- cross-origin redirects receive no credentials;
- 412 is typed as conflict and does not trigger a second PUT;
- response-loss reconciliation GET distinguishes own accepted payload from external change;
- 401/403/409/5xx/timeout classifications are persisted correctly.

Extend `SecurityPolicyTest.sensitiveDirs` coverage if new publication packages sit outside `data/caldav`/`data/worker`. Assert no logs, hardcoded Basic literal, password field, Authorization column, credential-shaped outbox field, or real ICS fixture.

### ViewModel and navigation tests

**Sources:**

- `NovaOSViewModelTest.kt:22-115`
- `DetalhesViewModelTest.kt:25-143`
- `NavigationTest.kt:15-85`

Continue using `StandardTestDispatcher`, `Dispatchers.setMain/resetMain`, `runTest`, `advanceUntilIdle`, and in-memory recording/blocking repositories. Preserve the existing concurrency test pattern where an in-flight older save completes after a newer edit.

Add tests for deterministic extraction, linked create-or-get, preset validation, preview read-only behavior, immutable queued payload, finishing requirements, field-by-field conflict choices, new confirmation after conflict, and no mutation on preview cancel.

Use stable `testTag`s for remote → start/continue → editor → preview and Mais → Central de sincronizações. Navigation tests should use anonymous local/mock state, never a real account.

## Shared Patterns

### Offline-first and version separation

Apply to every editor/publication file:

1. Mutable draft is persisted locally.
2. Remote base snapshot/ETag is immutable evidence.
3. Confirmed preview/version is immutable evidence.
4. Outbox references the confirmed version, not the current draft.
5. Current remote fetched during conflict is separate from all three.

Never load a new remote value into the mutable draft without explicit field choices.

### Error handling

Follow typed outcomes rather than leaking exceptions into composables. Existing examples are `SyncOutcome` in `SyncWorker.kt:28-35` and failure mapping in `RoomCalendarSyncCoordinator.kt:111-123`. Persist user-actionable states (`UNAUTHORIZED`, `FORBIDDEN`, `CONFLICT`, `TRANSIENT_FAILURE`) before returning worker results. Error messages must not include ICS bodies, descriptions or credentials.

### Dependency injection

Follow `@Binds` interfaces in `CalDavModule.kt:15-35` and `RepositoryModule.kt:15-35`; follow DAO `@Provides` in `DatabaseModule.kt:19-58`. Bind writer and publication coordinator separately from read sync. Do not replace or broaden the existing read interfaces.

### Time and identity

Use injected `ClockProvider` in coordinators/renderers requiring current timestamps, matching `RoomCalendarSyncCoordinator.kt:36-44`. Generate local UUID, stable publication UID and deterministic target href once, persist them, and reuse across retries.

## Collision Risks and Plan Ownership

These files are high-contention integration points and should have one plan owner or be changed in a single integration wave:

| File | Collision Risk | Ownership Guidance |
|---|---|---|
| `NexoDatabase.kt` | Very high | One storage plan owns entity registration, version bump and DAO accessors. |
| `NexoDatabaseMigrations.kt` and `app/schemas/**/3.json` | Very high | Same storage plan owns migration SQL and exported schema; no parallel schema edits. |
| `DatabaseModule.kt` | High | Storage integration plan adds all DAO providers and migration registration together. |
| `ServiceOrder.kt`, `ServiceOrderEntity.kt`, `ServiceOrderRepository.kt`, `RoomServiceOrderRepository.kt` | Very high | One domain/storage plan evolves the aggregate end-to-end; UI plans consume the new contract. |
| `IcsParser.kt`, `IcsModel.kt`, `RemoteEventMapper.kt`, `RemoteEventEntity.kt` | High | ICS/occurrence plan owns parser/cache cardinality; writer plan consumes the lossless editor instead of modifying parser independently. |
| `CalDavXmlParser.kt`, `CalDavModels.kt`, calendar entity/setup mapper | High | One privilege-capability plan retains exact privileges across XML → domain → Room. |
| `CalDavHttpClient.kt`, `HttpMethodAllowlist.kt` | Critical security boundary | Prefer no edits. Writer plan creates separate files; any necessary shared refactor must land alone with read-only regression tests. |
| `CalDavModule.kt`, `RepositoryModule.kt` | Medium/high | Defer bindings to an integration task after contracts/implementations exist. |
| `Routes.kt`, `MainActivity.kt` | Very high | One navigation integration task adds editor/preview/conflict/central routes. |
| `NovaOS*` and `Detalhes*` | Very high | One editor plan consolidates/evolves both flows; avoid separate plans refactoring each simultaneously. |
| `RemoteEventDetail*` | High | Link-start plan owns it after repository create-or-get is stable. |
| `Hoje*`, `Agenda*` | High | One projection/card plan owns linked-card unification and publication status badges. |
| `MaisScreen.kt` | Low/medium | Sync-center navigation task owns the single entry addition. |
| `SecurityPolicyTest.kt`, migration consistency test, `NavigationTest.kt` | High gate files | Assign to final integration/validation plan to avoid parallel assertion churn. |

Recommended dependency order: schema/domain → pure extraction/renderer/lossless ICS tests → conditional writer tests → outbox/coordinator/worker → editor UI → preview/conflict/central/navigation → linked card integration → full security/migration/navigation gate.

## No Analog Found

| File/Capability | Role | Data Flow | Reason |
|---|---|---|---|
| `domain/serviceorder/ServiceOrderDiff.kt` | utility | transform | No existing three-version or field-choice comparison implementation. |
| `ui/screens/conflito/*` | component / ViewModel | event-driven | No existing manual base/local/remote review screen. |
| Lossless physical-line ICS editor | utility | file-I/O / transform | Current parser preserves raw text but is extractive and discards ordered unknown-property structure. |

For these, use the concrete contracts in `03-RESEARCH.md`; do not infer behavior from the legacy donor app.

## Metadata

**Analog search scope:** `app/src/main`, `app/src/test`, `app/src/androidTest`  
**Files scanned:** 92 paths; 46 read/inspected for patterns  
**Strong analog families:** Room/store, pure parser/transform, CalDAV request-response, WorkManager batch, Compose/ViewModel UDF, test infrastructure  
**Pattern extraction date:** 2026-08-27
