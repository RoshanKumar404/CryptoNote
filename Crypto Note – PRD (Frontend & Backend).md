# Crypto Note – PRD

Offline-first notes app with on-device encryption. Frontend: Kotlin (Android). Backend: Node.js.

## 1. Overview

- **Goal:** Notes app that works fully offline; notes are encrypted on device and synced later as ciphertext.
- **Principle:** The server never sees plaintext or keys (zero-knowledge).
- **Flow:** User action → local write → encrypt → UI updates instantly → background sync.

## 2. Architecture

UI → Repository → Encrypt → Local DB (ciphertext) + Outbox (one transaction) → Sync Worker → Backend API → Cloud DB

---

# PART A – Frontend PRD (Kotlin / Android)

## A1. Scope

Android app, Jetpack Compose UI, MVVM, single-user per account.

## A2. Functional Requirements

| ID | Requirement | Priority |
| --- | --- | --- |
| F1 | Create, edit, delete, view notes (title + body) | P0 |
| F2 | Works fully offline | P0 |
| F3 | Passphrase setup and unlock; biometric unlock | P0 |
| F4 | Encrypt every note on device before storing | P0 |
| F5 | Outbox queue records every create/update/delete | P0 |
| F6 | Background sync when network is available | P0 |
| F7 | Sync status per note (pending / synced / failed) | P1 |
| F8 | Local search (FTS over decrypted content) | P1 |
| F9 | Conflict handling (keep both copies) | P1 |
| F10 | Recovery key export | P1 |
| F11 | Auto-lock after inactivity | P2 |
| F12 | Tags, pin, dark mode | P2 |

## A3. Security Requirements

- Master key derived with **Argon2id** from passphrase.
- Notes encrypted with **AES-256-GCM**, unique nonce per note.
- Keys wrapped by **Android Keystore**; never stored in plaintext.
- Titles are encrypted too.
- Block screenshots on note screens (FLAG\_SECURE); exclude app from backups.

## A4. Tech Stack

Kotlin, Jetpack Compose, MVVM, Coroutines + Flow, Room (SQLCipher optional), WorkManager, Retrofit/OkHttp, Hilt.

## A5. Local Data Model

- **notes:** id (UUID), ciphertext, nonce, version, updated\_at, deleted (tombstone), sync\_status
- **outbox:** op\_id (UUID), note\_id, type (create/update/delete), payload, attempts, status, created\_at
- **sync\_state:** last\_pull\_cursor

## A6. Sync Behavior

- Note write and outbox insert in **one transaction**.
- WorkManager runs with a network-connected constraint; exponential backoff on failure.
- Push outbox in order, then pull changes with `since=<cursor>`; apply to local DB; UI refreshes via Flow.

## A7. Non-Functional

- Local write to UI update under 100 ms.
- Cold start under 2 s.
- No data loss on app kill mid-sync.
- Min SDK 26.

## A8. Acceptance Criteria

- Airplane mode: create/edit/delete works, and all changes sync after reconnect.
- Database inspection shows no plaintext.
- Retried syncs create no duplicates.

---

# PART B – Backend PRD (Node.js)

## B1. Scope

REST API that authenticates users and stores/syncs encrypted blobs. No decryption, no search over content.

## B2. Functional Requirements

| ID | Requirement | Priority |
| --- | --- | --- |
| B1 | Register / login (JWT access + refresh tokens) | P0 |
| B2 | Push endpoint accepting batched operations | P0 |
| B3 | Pull endpoint returning changes since cursor | P0 |
| B4 | Idempotency on `op_id` | P0 |
| B5 | Version check and conflict detection | P0 |
| B6 | Soft-delete (tombstones) sync | P0 |
| B7 | Device registration and listing | P1 |
| B8 | Rate limiting, request validation | P1 |
| B9 | Account deletion (wipe all data) | P1 |

## B3. API

- `POST /auth/register`, `POST /auth/login`, `POST /auth/refresh`
- `POST /sync/push` – body: `[{op_id, note_id, type, ciphertext, nonce, base_version}]`; returns per-op result (applied / conflict / duplicate)
- `GET /sync/pull?since=<cursor>&limit=100` – returns changes plus next cursor
- `GET /notes/:id` (optional), `DELETE /account`

## B4. Data Model (PostgreSQL)

- **users:** id, email, password\_hash (Argon2), auth\_salt, created\_at
- **notes:** id, user\_id, ciphertext, nonce, version, deleted, updated\_at, server\_seq
- **processed\_ops:** op\_id (unique), user\_id, result, created\_at
- **devices:** id, user\_id, last\_seen

`server_seq` is a monotonically increasing number used as the pull cursor.

## B5. Tech Stack

Node.js, Express or Fastify, PostgreSQL (Prisma or pg), JWT, Zod validation, Helmet, rate-limiter, Jest.

## B6. Security Requirements

- HTTPS only; hash passwords with Argon2.
- Use a login secret separate from the encryption passphrase, so the server can't derive the key.
- Server stores ciphertext only; never log payloads.
- All queries scoped by `user_id`.

## B7. Non-Functional

- Push/pull p95 under 300 ms for 100 ops.
- Idempotent, safely retryable endpoints.
- Max payload size per note (e.g. 1 MB).

## B8. Acceptance Criteria

- Same `op_id` sent twice produces one change.
- A stale `base_version` returns a conflict, not an overwrite.
- Pull with a cursor returns only newer changes, including deletes.

---

## 3. Out of Scope (v1)

Server-side search, sharing/collaboration, web client, attachments.

## 4. Milestones

1. Local notes + Room + UI
2. Encryption + key management
3. Outbox + WorkManager sync
4. Node backend + auth + push/pull
5. Conflicts, search, polish, testing

## 5. Skills Showcase (Project Goals)

Each item below is a deliberate feature, so it can be demoed and explained in interviews.

### UI

- Material 3 design system, light/dark theme, adaptive layouts (phone and tablet)
- Compose animations: lock/unlock transition, sync-status indicator (pending, syncing, synced, failed)
- Markdown editor with live preview; swipe actions; empty/error states
- Accessibility: TalkBack labels, large-text support, contrast checks

### OS (Android internals)

- Android Keystore / StrongBox-backed keys; BiometricPrompt
- WorkManager constraints, Doze and background-execution limits; survive process death and reboot
- `ConnectivityManager.NetworkCallback` to detect connectivity changes
- Room/SQLite transactions and WAL mode; atomic note + outbox write
- FLAG\_SECURE, clipboard auto-clear, auto-lock on app background, backup exclusion
- Zeroing sensitive byte arrays after use

### Networking

- Idempotent REST sync with `op_id`; cursor-based pull (`server_seq`)
- Exponential backoff with jitter; batching; gzip compression
- TLS with certificate pinning (OkHttp); JWT refresh via interceptor
- ETag / version checks for conflict detection
- Stretch: WebSocket or SSE push so other devices update live

### Cryptography

- Argon2id key derivation; AES-256-GCM with per-note nonce
- HKDF to derive a separate **auth key** and **encryption key** from one passphrase (zero-knowledge login)
- Per-note data keys wrapped by the master key (key wrapping), enabling cheap key rotation
- AAD binding (`note_id + version`) to prevent ciphertext swapping and rollback
- Recovery key, encrypted export/import
- Short **threat model** document: attacker with stolen phone, malicious server, network sniffer

### Deliverables to prove it

- Architecture diagram and threat model in the README
- Demo video: offline edit, reconnect, sync, then DB dump showing only ciphertext
- Unit tests for crypto (known-answer vectors) and sync (retry, duplicate, conflict)

## 6. Offline Test Plan \[FLAG: v2\]

Not in v1 scope. Planned for version 2.

| Scenario | Expected result |
| --- | --- |
| Airplane mode: create, edit, delete | All work locally; outbox grows |
| Reconnect after offline edits | Outbox drains in order; statuses become synced |
| Kill app mid-sync | No data loss; unsent ops retried on next run |
| Flaky network (drops, timeouts) | Backoff with jitter; no duplicates |
| Same `op_id` pushed twice | Server applies once |
| Two devices edit the same note | Conflict detected; both copies kept |
| Delete on one device, edit on another | Tombstone wins or conflict flagged, per policy |
| Reboot or process death while pending | Sync resumes via WorkManager |
| DB dump after sync | Ciphertext only; no plaintext |
