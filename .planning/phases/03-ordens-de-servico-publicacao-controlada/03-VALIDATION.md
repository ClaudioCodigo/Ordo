---
phase: 03
slug: ordens-de-servico-publicacao-controlada
status: ready_for_uat
nyquist_compliant: true
wave_0_complete: true
created: 2026-08-27
validated: 2026-08-27
---

# Phase 03 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4, kotlinx-coroutines-test, MockWebServer; AndroidJUnit4 + Room MigrationTestHelper |
| **Config file** | `app/build.gradle.kts`; Room schemas in `app/schemas/` |
| **Quick run command** | `./gradlew testDebugUnitTest` |
| **Full suite command** | `./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug` |
| **Estimated runtime** | Under 1 minute for JVM tests/build |

---

## Sampling Rate

- **After every task commit:** Run focused JVM tests for the changed module; `./gradlew testDebugUnitTest`.
- **After every plan wave:** Run `./gradlew testDebugUnitTest assembleDebug`.
- **Before `$gsd-verify-work`:** Run the full suite, migration tests, then the controlled Nextcloud UAT.
- **Max feedback latency:** ~40-60 seconds for the automated JVM/build feedback loop.

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| W0-01 | 03-02 | 2 | OS-01 | T-03 | Parser preserves raw text and rejects oversized/invalid input safely | unit | `./gradlew testDebugUnitTest --tests '*ServiceOrderExtractionTest'` | ✅ | ✅ green |
| W0-02 | 03-01 | 1 | OS-02 | T-04 | Composite remote identity and local UUID prevent duplicate links | DAO/integration | `./gradlew testDebugUnitTest --tests '*NexoDatabaseMigrationTest*'` | ✅ | ✅ green |
| W0-03 | 03-04 | 4 | OS-03 | T-01, T-04 | Create is conditional and idempotent online/offline | unit + MockWebServer + worker | `./gradlew testDebugUnitTest --tests '*PublicationCoordinatorTest'` | ✅ | ✅ green |
| W0-04 | 03-05 | 5 | OS-04 | T-05 | Autosave never persists credentials or depends on network | ViewModel/unit | `./gradlew testDebugUnitTest --tests '*ServiceOrderEditorViewModelTest'` | ✅ | ✅ green |
| W0-05 | 03-02 | 2 | OS-05 | T-03 | Date divergence blocks silent correction; time remains native | unit | `./gradlew testDebugUnitTest --tests '*ServiceOrderRendererTest'` | ✅ | ✅ green |
| W0-06 | 03-02 | 2 | OS-06 | T-04 | Updates render once in stable chronological order | unit | `./gradlew testDebugUnitTest --tests '*ServiceOrderRendererTest'` | ✅ | ✅ green |
| W0-07 | 03-02 | 2 | OS-07 | T-04 | Finalization validates fields and preserves local evidence | unit/ViewModel | `./gradlew testDebugUnitTest --tests '*ServiceOrderRendererTest'` | ✅ | ✅ green |
| W0-08 | 03-01 | 1 | OS-08 | T-04, T-05 | Migration and sync preserve snapshots, drafts and versions | migration/DAO | `./gradlew testDebugUnitTest --tests '*RoomServiceOrderRepositoryTest'` | ✅ | ✅ green |
| W0-09 | 03-02/03 | 2/3 | SPEC R6/R7 | T-01, T-02, T-03, T-06 | Lossless ICS update uses mandatory preconditions and no DELETE | unit + MockWebServer | `./gradlew testDebugUnitTest --tests '*IcsDocumentEditorTest' --tests '*CalDavWriteClientTest'` | ✅ | ✅ green |
| W0-10 | 03-06 | 6 | D-17/D-18 | T-01, T-04 | Conflict preserves base/local/remote and requires reconfirmation | unit/ViewModel | `./gradlew testDebugUnitTest --tests '*ConflictReviewViewModelTest' --tests '*ServiceOrderDiffTest'` | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] Criar fixture factory anônima e helpers de invariantes ICS em testes de unidade.
- [x] Criar testes de documento/editor iCalendar lossless (`IcsDocumentEditorTest.kt`) antes de habilitar o writer.
- [x] Criar testes do writer provando ausência de `PUT` incondicional e de `DELETE` (`CalDavWriteClientTest.kt`, `SecurityPolicyTest.kt`).
- [x] Criar testes de migração 1→2 e 2→3 preservando rascunhos e cache remoto (`NexoDatabaseMigrationTest.kt`).
- [x] Criar testes do outbox para concorrência, lease interrompido, resposta perdida e retry transitório (`PublicationCoordinatorTest.kt`).
- [x] Criar testes golden de extração e renderização (`ServiceOrderExtractionTest.kt`, `ServiceOrderRendererTest.kt`).
- [x] Criar testes de navegação e ViewModel (`SyncCenterViewModelTest.kt`, `HojeViewModelTest.kt`, `AgendaViewModelTest.kt`).

---

## Manual-Only Verifications (UAT Nextcloud)

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Create real `[TESTE NEXO]` and validate ETag/preconditions | OS-03, SPEC R7 | Depends on the actual Nextcloud server and credentials kept only on device | On Saturday/Sunday, create one controlled event, confirm one resource, inspect returned/fetched ETag and ensure retry does not duplicate it |
| Preserve a real event across conditional update | OS-05..OS-08, SPEC R6 | Server may normalize ICS differently from MockWebServer | Compare `SUMMARY`, `DTSTART`, `DTEND`, `LOCATION`, `COLOR`, `UID`, recurrence, scheduling and unknown properties before/after; only authorized mutable properties may differ |
| Deliberate concurrent edit | SPEC R7 | Requires a second real client/server mutation | Modify remotely after preview, publish from Nexo, verify conflict state and intact local draft; review and reconfirm once |
| Shared/invited event permissions | D-22 | ACL and scheduling behavior are server/configuration specific | Test one writable shared event and one denied event; denied write must preserve draft and stop retries |
| Credential revocation and secret hygiene | Security | Real credential lifecycle cannot be proven by fixtures | Revoke temporary app password after UAT; inspect logs/export/repo for absence of credentials and real ICS |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 60 seconds on a healthy host
- [x] `nyquist_compliant: true` set in frontmatter after validation

**Approval:** verified_automated
