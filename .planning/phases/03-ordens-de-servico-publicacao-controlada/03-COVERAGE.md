# Phase 3 — CalDAV/WebDAV Capability Coverage

**Gate:** external API coverage  
**Scope:** one active Nextcloud account and one selected work calendar  
**Authority:** `03-SPEC.md`, `03-CONTEXT.md`, RFC 3744/4791/5545/6638/9110

This matrix is the complete CalDAV/WebDAV capability decision for Phase 3. Phase 2's read-only client and method allowlist remain unchanged. Phase 3 adds a separate, typed writer whose public surface contains only conditional create and conditional update.

| capability | decision | reason |
|---|---|---|
| Service discovery (`PROPFIND`) | INTEGRATE | Retain the Phase 2 read capability with unchanged HTTPS and same-origin Authorization rules; verify through existing discovery/client regression tests. |
| Calendar discovery and exact privileges (`PROPFIND current-user-privilege-set`) | INTEGRATE | Extend the discovery result to retain exact privilege names; treat `bind`/`write-content` as hints and the actual server response as authoritative; verify with namespace-aware anonymous DAV XML. |
| Resource listing and incremental sync (`PROPFIND`, `REPORT sync-collection`) | INTEGRATE | Retain existing sync-token and href/ETag fallback as read-only and use post-publication readback for reconciliation. |
| Resource fetch and revalidation (`GET`, `REPORT calendar-multiget`) | INTEGRATE | Fetch current ETag and raw ICS before queued update without mutating the draft; cover conflict and worker paths. |
| Create one provisional VEVENT | INTEGRATE | Use deterministic href and stable UID with `If-None-Match: *` and `text/calendar; charset=utf-8`; no unconditional overload; verify idempotency with MockWebServer and controlled UAT. |
| Update one existing VEVENT/occurrence | INTEGRATE | Require nonblank `If-Match`, target `UID + RECURRENCE-ID`, mutate only DESCRIPTION/SEQUENCE/DTSTAMP/LAST-MODIFIED and preserve every other property; verify lossless round-trip. |
| Capability and permission check | INTEGRATE | Add operation-specific preflight and typed denial; `403` remains authoritative, preserves draft/outbox evidence and stops automatic retry; verify invited/shared fixtures and UAT. |
| Response-loss reconciliation | INTEGRATE | Fetch after ambiguous create/update or retry-time `412` and compare UID plus mutable semantic projection/digest, never blind-retry a second PUT. |
| Conditional conflict handling | INTEGRATE | Persist base/local/remote separately on `412`; renewed ETag requires manual field review, a new read-only preview and explicit reconfirmation. |
| DELETE event | OPT-OUT | Remote deletion is destructive and explicitly outside every Phase 3 user flow and the locked SPEC. |
| Color mutation | OPT-OUT | Color is external validation/attention evidence; Nexo preserves and classifies it but never writes it. |
| Calendar creation/deletion | OPT-OUT | Phase 3 operates only on the single selected work calendar and performs no collection mutation. |
| Scheduling mutation (`ATTENDEE`, `ORGANIZER`, `PARTSTAT`, `RSVP`, iTIP) | OPT-OUT | Scheduling and unknown properties are preserved; Nexo does not implement iTIP or change participant state in this phase. |
| Full recurrence expansion | OPT-OUT | Phase 3 indexes and targets explicit VEVENT components only; local expansion of implicit RRULE instances remains outside scope. |

## Security invariants

- The Phase 2 `CalDavReadClient`, `CalDavHttpClient` guard, and `HttpMethodAllowlist` are not broadened.
- The writer attaches Authorization only after validating HTTPS and exact origin; cross-origin redirects fail closed.
- Credentials, Authorization values, and app passwords are absent from ICS, previews, history, outbox, logs, fixtures, and exported artifacts.
- Malformed or oversized XML/ICS fails safely without replacing the last valid cache or mutable draft.
- `401`, `403`, `409`, `412`, network failure, timeout, and `5xx` map to explicit persisted outcomes; only transient network/timeout failures qualify for automatic WorkManager retry.

## Controlled-server validation

The real-server gate uses a temporary app password stored only on the device. On a Saturday or Sunday, create a `[TESTE NEXO]` event, update it conditionally, provoke one conflict, exercise one writable and one denied shared/invited event, compare preserved fields, then revoke the credential. Real ICS exports and credentials never enter the repository.
