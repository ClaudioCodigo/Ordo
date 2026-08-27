---
phase: 03
slug: ordens-de-servico-publicacao-controlada
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-08-27
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
| **Full suite command** | `./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug` plus `connectedDebugAndroidTest` on device/emulator |
| **Estimated runtime** | Host-dependent; target under 3 minutes for JVM tests/build/lint, instrumented suite measured separately |

---

## Sampling Rate

- **After every task commit:** Run focused JVM tests for the changed module; if no focused filter exists yet, run `./gradlew testDebugUnitTest`.
- **After every plan wave:** Run `./gradlew testDebugUnitTest assembleDebug`.
- **Before `$gsd-verify-work`:** Run the full suite, migration tests on device/emulator, then the controlled Nextcloud UAT.
- **Max feedback latency:** 180 seconds for the automated JVM/build feedback loop on a healthy Gradle host.

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| W0-01 | TBD | 0 | OS-01 | T-03 | Parser preserves raw text and rejects oversized/invalid input safely | unit | `./gradlew testDebugUnitTest --tests '*ServiceOrderExtractionTest'` | ❌ W0 | ⬜ pending |
| W0-02 | TBD | 0 | OS-02 | T-04 | Composite remote identity and local UUID prevent duplicate links | DAO/integration | `./gradlew connectedDebugAndroidTest` | ❌ W0 | ⬜ pending |
| W0-03 | TBD | 0 | OS-03 | T-01, T-04 | Create is conditional and idempotent online/offline | unit + MockWebServer + worker | `./gradlew testDebugUnitTest --tests '*Publication*Test'` | ❌ W0 | ⬜ pending |
| W0-04 | TBD | 0 | OS-04 | T-05 | Autosave never persists credentials or depends on network | ViewModel/unit | `./gradlew testDebugUnitTest --tests '*ServiceOrderEditorViewModelTest'` | ❌ W0 | ⬜ pending |
| W0-05 | TBD | 0 | OS-05 | T-03 | Date divergence blocks silent correction; time remains native | unit | `./gradlew testDebugUnitTest --tests '*ServiceOrderRendererTest'` | ❌ W0 | ⬜ pending |
| W0-06 | TBD | 0 | OS-06 | T-04 | Updates render once in stable chronological order | unit | `./gradlew testDebugUnitTest --tests '*ServiceOrderRendererTest'` | ❌ W0 | ⬜ pending |
| W0-07 | TBD | 0 | OS-07 | T-04 | Finalization validates fields and preserves local evidence | unit/ViewModel | `./gradlew testDebugUnitTest --tests '*ServiceOrderCompletionTest'` | ❌ W0 | ⬜ pending |
| W0-08 | TBD | 0 | OS-08 | T-04, T-05 | Migration and sync preserve snapshots, drafts and versions | migration/DAO | `./gradlew connectedDebugAndroidTest` | ❌ W0 | ⬜ pending |
| W0-09 | TBD | 0 | SPEC R6/R7 | T-01, T-02, T-03, T-06 | Lossless ICS update uses mandatory preconditions and no DELETE | unit + MockWebServer | `./gradlew testDebugUnitTest --tests '*IcsDocumentEditorTest' --tests '*CalDavWriteClientTest'` | ❌ W0 | ⬜ pending |
| W0-10 | TBD | 0 | D-17/D-18 | T-01, T-04 | Conflict preserves base/local/remote and requires reconfirmation | unit/ViewModel | `./gradlew testDebugUnitTest --tests '*ConflictReviewViewModelTest'` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

Threat references:

- **T-01:** overwrite concorrente ou retry cego;
- **T-02:** redirect cross-origin vazando `Authorization`;
- **T-03:** ICS/XML malicioso, gigante ou malformado;
- **T-04:** operação, vínculo ou histórico duplicado;
- **T-05:** segredo em outbox, histórico, log ou fixture;
- **T-06:** alteração indevida de campos de scheduling/convite.

---

## Wave 0 Requirements

- [ ] Criar fixture factory anônima e helpers de invariantes ICS em `app/src/test/resources/ical/` e código de teste correspondente.
- [ ] Criar testes de documento/editor iCalendar lossless antes de habilitar o writer.
- [ ] Criar testes do writer provando ausência de `PUT` incondicional e de `DELETE`.
- [ ] Criar testes de migração 1→3 e 2→3 preservando rascunhos e cache remoto.
- [ ] Criar testes do outbox para concorrência, lease interrompido, resposta perdida e retry transitório.
- [ ] Criar testes golden de extração e renderização.
- [ ] Criar teste de navegação remoto → iniciar/continuar → editor → prévia → Central de sincronizações.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Create real `[TESTE NEXO]` and validate ETag/preconditions | OS-03, SPEC R7 | Depends on the actual Nextcloud server and credentials kept only on device | On Saturday/Sunday, create one controlled event, confirm one resource, inspect returned/fetched ETag and ensure retry does not duplicate it |
| Preserve a real event across conditional update | OS-05..OS-08, SPEC R6 | Server may normalize ICS differently from MockWebServer | Compare `SUMMARY`, `DTSTART`, `DTEND`, `LOCATION`, `COLOR`, `UID`, recurrence, scheduling and unknown properties before/after; only authorized mutable properties may differ |
| Deliberate concurrent edit | SPEC R7 | Requires a second real client/server mutation | Modify remotely after preview, publish from Nexo, verify conflict state and intact local draft; review and reconfirm once |
| Shared/invited event permissions | D-22 | ACL and scheduling behavior are server/configuration specific | Test one writable shared event and one denied event; denied write must preserve draft and stop retries |
| Credential revocation and secret hygiene | Security | Real credential lifecycle cannot be proven by fixtures | Revoke temporary app password after UAT; inspect logs/export/repo for absence of credentials and real ICS |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 180 seconds on a healthy host
- [ ] `nyquist_compliant: true` set in frontmatter after validation

**Approval:** pending
