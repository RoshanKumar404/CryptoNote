# CryptoNote 🔐

[![CI](https://github.com/RoshanKumar404/CryptoNote/actions/workflows/ci.yml/badge.svg)](https://github.com/RoshanKumar404/CryptoNote/actions/workflows/ci.yml)
[![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20Fastify-blue.svg)](https://github.com/RoshanKumar404/CryptoNote)
[![Security](https://img.shields.io/badge/Security-Zero--Knowledge%20E2EE-emerald.svg)](https://github.com/RoshanKumar404/CryptoNote)
[![License](https://img.shields.io/badge/License-Apache%202.0-lightgrey.svg)](LICENSE)

**CryptoNote** is an offline-first, zero-knowledge, end-to-end encrypted notes and vault platform built for modern security and privacy. All user notes, titles, tags, and contents are encrypted on-device before hitting the network or SQLite storage. The cloud backend acts strictly as an untrusted, opaque blob synchronizer with monotonic sequence tracking, cryptographic checksum verification, and deterministic conflict forking.

---

## 📑 Table of Contents

- [Key Highlights](#-key-highlights)
- [Architecture & Cryptographic Model](#-architecture--cryptographic-model)
  - [Key Hierarchy](#key-hierarchy)
  - [Envelope V1 Structure](#envelope-v1-structure)
  - [Sync Engine & Conflict Protocol](#sync-engine--conflict-protocol)
- [Tech Stack](#-tech-stack)
- [Project Directory Structure](#-project-directory-structure)
- [Quick Start & Launch Guide](#-quick-start--launch-guide)
  - [Prerequisites](#prerequisites)
  - [1. Running Backend Locally](#1-running-backend-locally)
  - [2. Running Android Client](#2-running-android-client)
  - [3. Connecting Android Client to Backend](#3-connecting-android-client-to-backend)
- [Running Test Suites](#-running-test-suites)
- [Production Deployment (Render)](#-production-deployment-render)
- [Threat Model & Security Invariants](#-threat-model--security-invariants)
- [Feature Status & Roadmap](#-feature-status--roadmap)

---

## 🌟 Key Highlights

- **Zero-Knowledge Encryption:** The server never sees plaintext, titles, tags, or unencrypted keys. Even if the database is dumped or the server is seized, note contents cannot be decrypted.
- **Dual-Zone Notes & Vault:**
  - **Notes Tab:** Everyday encrypted notes with instant search, tags, and biometric unlock.
  - **Vault Tab:** Ultra-sensitive notes with obscured titles/previews, in-memory zeroization upon lock, and an aggressive 1-minute idle auto-lock timer.
- **Offline-First Synchronization:** Seamless CRUD operations offline. Outbox queue pairs note changes in atomic Room transactions and pushes sequentially on reconnect with exponential backoff.
- **Deterministic Conflict Forking:** No silent data loss or arbitrary "last-write-wins". Conflicting edits are forked into independent encrypted notes for side-by-side user resolution.
- **Anti-Forensic Hardening:**
  - `FLAG_SECURE` blocks screenshots, screen recording, and task-switcher previews.
  - Automatic 60-second clipboard wipe after copying sensitive text.
  - Memory zeroization (`Arrays.fill(bytes, 0)`) for master keys and decrypted plaintext upon vault locking.
  - Cloud backup (`allowBackup="false"`) exclusion.
- **Material 3 Dynamic UX:** Dark and light mode with high-contrast Obsidian styling (WCAG AA compliant), native markdown editor with live preview, and tag filtering.

---

## 🛡️ Architecture & Cryptographic Model

### Key Hierarchy

CryptoNote separates **Authentication** from **Encryption** to allow zero-knowledge operations and instant passphrase changes without re-encrypting existing notes:

```mermaid
graph TD
    subgraph "User Secrets (Client-Side Only)"
        UP["Passphrase (Secret)"]
        LP["Login Password (Server Auth)"]
        RK["Recovery Key (256-bit Mnemonic)"]
    end

    subgraph "Key Derivation Layer"
        SALT["Random Salt (16B)"]
        ARGON["Argon2id (m=64MB, t=3, p=1)"]
        UP & SALT --> ARGON
        ARGON --> KEK["Key Encryption Key (KEK 256-bit)"]
        
        MK["Master Key (MK 256-bit CSPRNG)"]
        
        KEK -->|AES-256-GCM Wrap| PWMK["pass_wrapped_mk (Stored on Server)"]
        RK -->|AES-256-GCM Wrap| RWMK["recovery_wrapped_mk (Stored on Server)"]
        
        KSK["Android Keystore Key (OAEP/StrongBox)"]
        KSK -->|AES-256-GCM Wrap| DWMK["device_wrapped_mk (Local EncryptedPrefs)"]
        
        MK --> PWMK
        MK --> RWMK
        MK --> DWMK
    end

    subgraph "Per-Note Encryption"
        MK -->|HKDF-SHA256| DEK["Derived Note Key (DEK 256-bit)"]
        MK -->|HKDF-SHA256| KID["Key ID (8B Header Ident)"]
        DEK --> NOTE["EnvelopeV1 (AES-256-GCM + AAD)"]
    end
```

### Envelope V1 Structure

Every note payload is packaged into a compact binary envelope (`format_version = 1`):

```
+---------------+---------------+--------------------+------------------+-----------------------+------------------+
| Format (1B)   | Cipher (1B)   | Key ID (8B)        | Nonce (12B)      | Ciphertext (Variable) | GCM Tag (16B)    |
| 0x01          | 0x01 (AES-GCM)| HKDF prefix        | SecureRandom     | Encrypted JSON Payload| Auth Tag         |
+---------------+---------------+--------------------+------------------+-----------------------+------------------+
```

- **Additional Authenticated Data (AAD):** `Format || Cipher || KeyID || NoteUUID || Version` is authenticated into the AES-256-GCM tag to prevent cross-record ciphertext reuse or version manipulation.
- **Payload Integrity:** Every push verifies `SHA-256(envelope) == checksum` before applying changes to the database.

### Sync Engine & Conflict Protocol

```mermaid
sequenceDiagram
    autonumber
    actor User as Android Client (Outbox)
    participant Server as Fastify API
    participant DB as PostgreSQL

    User->>User: Save/Edit Note (Atomic Local Room Tx)
    User->>User: Enqueue Outbox Op (base_version, version=base+1)
    User->>Server: POST /api/v1/sync/push [ops]
    Server->>DB: Begin DB Transaction (Row Lock user_sync)
    alt Version matches current server_version
        Server->>DB: Update Note + Increment user_sync.last_seq
        Server->>DB: Record in processed_ops
        Server-->>User: [{ op_id, result: "applied", server_version }]
        User->>User: Mark Outbox op as SENT
    else Stale base_version detected (Concurrent Edit)
        Server-->>User: [{ op_id, result: "conflict", server_version }]
        User->>Server: GET /api/v1/sync/pull (Fetch server copy)
        User->>User: Fork local edit into new Note (UUID_2)
        User->>User: Enqueue create op for fork & apply server copy
    end
```

---

## 💻 Tech Stack

### Frontend (Android)
- **Language:** Kotlin 2.0+ (JVM 17 Target)
- **UI Framework:** Jetpack Compose (Material 3 Dynamic Theming)
- **Architecture:** MVVM + Clean Architecture + Repository Pattern
- **Local Persistence:** Room Database v2 (with quarantine table for unreadable payloads)
- **Security & Crypto:**
  - `androidx.security:security-crypto` & `Android Keystore`
  - BouncyCastle (`org.bouncycastle:bcprov-jdk18on`) for Argon2id and HKDF
  - `androidx.biometric:biometric` (StrongBox / Keystore OAEP integration)
- **Networking:** Retrofit 2, OkHttp 3 (with JWT refresh interceptor and logging)
- **Background Sync:** AndroidX WorkManager (`CoroutineWorker`) & `ConnectivityManager` NetworkObserver
- **Dependency Injection:** Dagger Hilt

### Backend
- **Runtime:** Node.js 20+ with TypeScript
- **Framework:** Fastify 5 (High throughput, schema validation via Zod)
- **Database & ORM:** PostgreSQL 16+ managed through Prisma ORM 6
- **Authentication:** Argon2id (`@node-rs/argon2`), JSON Web Tokens (15-min Access, 7-day Refresh rotation)
- **Hardening:** `@fastify/helmet`, `@fastify/cors`, rate-limiting, and binary envelope validation

---

## 📂 Project Directory Structure

```
CryptoNote/
├── .github/
│   └── workflows/ci.yml         # Automated CI (Frontend tests + Backend suite)
├── backend/                     # Node.js Fastify backend
│   ├── prisma/                  # Prisma schema and PostgreSQL migrations
│   ├── src/
│   │   ├── config/              # Environment and security config
│   │   ├── db/                  # Prisma client singleton
│   │   ├── middleware/          # JWT auth & error interceptors
│   │   ├── routes/              # Auth, Sync, Keys, Health endpoints
│   │   ├── services/            # Cryptographic checks, sync logic, token service
│   │   └── server.ts            # Fastify server entrypoint
│   ├── tests/                   # 50 Jest unit & integration tests
│   └── docker-compose.yml       # Local PostgreSQL service
├── CryptNote/                   # Android Client application
│   ├── app/
│   │   ├── src/main/java/com/example/cryptnote/
│   │   │   ├── data/            # Room DB, entities, DAOs, Retrofit API
│   │   │   ├── di/              # Hilt modules (Database, Network, Security)
│   │   │   ├── domain/          # Models, use-cases, search algorithms
│   │   │   ├── security/        # EnvelopeV1, KeySessionManager, VaultLock, Biometrics
│   │   │   ├── sync/            # SyncWorker, ConnectivityObserver, Outbox queue
│   │   │   └── ui/              # Compose screens (Notes, Vault, Editor, Auth, Profile)
│   │   └── src/test/            # Frontend unit tests & golden fixture checks
├── docs/                        # Specifications (SPEC-1, SPEC-2, PRD, Technical docs)
├── render.yaml                  # 1-Click Render Cloud deployment blueprint
└── README.md                    # Project documentation
```

---

## 🚀 Quick Start & Launch Guide

### Prerequisites

Ensure you have the following installed on your development machine:
- **Node.js**: v20 or higher (`node -v`)
- **Docker & Docker Compose**: For local PostgreSQL database
- **Android Studio**: Ladybug / Koala or newer with JDK 17
- **Android Device or Emulator**: Android 8.0+ (API Level 26+)

---

### 1. Running Backend Locally

#### Step 1.1: Clone & Configure Environment
Navigate to the `backend/` directory and configure the environment variables:

```bash
cd backend
cp .env.example .env
```

Default `.env` configuration for local development:
```env
PORT=3000
HOST=0.0.0.0
NODE_ENV=development
DATABASE_URL="postgresql://postgres:password@localhost:5432/cryptnote?schema=public"
JWT_SECRET="your-super-secret-jwt-key-min-32-chars-long"
JWT_ACCESS_EXPIRATION="15m"
JWT_REFRESH_EXPIRATION="7d"
```

#### Step 1.2: Start PostgreSQL with Docker
```bash
docker compose up -d
```

#### Step 1.3: Install Dependencies & Run Database Migrations
```bash
npm install
npx prisma migrate dev
```

#### Step 1.4: Launch the Server
```bash
npm run dev
```

The Fastify server will start at `http://localhost:3000`. You can verify health status by visiting:
```bash
curl http://localhost:3000/health
# Output: {"status":"ok","timestamp":"..."}
```

---

### 2. Running Android Client

1. Open **Android Studio**.
2. Select **Open an Existing Project** and choose the `CryptNote/` folder.
3. Allow Gradle to sync dependencies (JDK 17 required).
4. Run the project on an **Android Emulator** or a **Physical Device** with Developer Options and USB Debugging enabled.

---

### 3. Connecting Android Client to Backend

The Android client automatically determines how to route network traffic based on its deployment target in [NetworkModule.kt](file:///d:/KotlinAps/CryptoNote/CryptNote/app/src/main/java/com/example/cryptnote/di/NetworkModule.kt):

| Environment | Base URL Used | Notes |
| :--- | :--- | :--- |
| **Android Emulator** | `http://10.0.2.2:3000/` | Default loopback alias to host machine localhost. Set `RENDER_BASE_URL = ""` in `NetworkModule.kt` |
| **Physical Device (via USB)** | `http://127.0.0.1:3000/` | Run `adb reverse tcp:3000 tcp:3000` on your host PC |
| **Production Cloud (Render)** | `https://cryptonote-backend.onrender.com/` | Pre-configured in `NetworkModule.kt` for cloud connectivity |

> [!TIP]
> **Testing with a physical phone over USB:**
> ```bash
> adb reverse tcp:3000 tcp:3000
> ```
> This forwards port 3000 from your phone directly to your host machine's Fastify server!

---

## 🧪 Running Test Suites

### Backend Tests (Jest)
Runs all 50 unit and integration tests (auth, cursor order, keys hardening, envelope integrity):
```bash
cd backend
npm test
```

### Android Unit Tests (Gradle)
Executes all crypto pipeline tests, Room migration checks, and golden vector compatibility:
```bash
cd CryptNote
./gradlew testDebugUnitTest
```

---

## ☁️ Production Deployment (Render)

CryptoNote includes a native [render.yaml](file:///d:/KotlinAps/CryptoNote/render.yaml) blueprint for zero-touch cloud deployment:

1. Push your repository to **GitHub**.
2. Log in to [Render.com](https://render.com) and click **New +** → **Blueprint**.
3. Connect your repository. Render will automatically provision:
   - A managed **PostgreSQL database** (`cryptonote-db`).
   - A Node.js **Fastify web service** (`cryptonote-backend`).
   - Automatically runs `prisma migrate deploy` on every build.
4. Copy your backend URL (e.g. `https://cryptonote-backend.onrender.com`) and paste it into `RENDER_BASE_URL` in [NetworkModule.kt](file:///d:/KotlinAps/CryptoNote/CryptNote/app/src/main/java/com/example/cryptnote/di/NetworkModule.kt).

---

## 🛡️ Threat Model & Security Invariants

| Threat Scenario | Mitigation in CryptoNote |
| :--- | :--- |
| **Compromised Backend / DB Dump** | Server stores only opaque binary envelopes (`envelope bytea`) and wrapped keys (`pass_wrapped_mk`). Plainttext, titles, tags, and master keys never exist on the server. |
| **Man-In-The-Middle (MITM) Sniffing** | All traffic requires TLS. Note envelopes are encrypted with AES-256-GCM before transport; network eavesdroppers cannot read payloads even if TLS termination is intercepted. |
| **Stolen Phone (Device at Rest)** | Notes at rest are encrypted with AES-256-GCM. Unlocking requires the master passphrase or biometric authentication backed by the hardware Android Keystore / StrongBox. |
| **Forensic Memory Dumps / RAM Peeking** | Upon vault lock or inactivity timeout, all decrypted note caches and raw key byte arrays are wiped in memory with `Arrays.fill(bytes, 0)`. |
| **Shoulder Surfing / Screen Sniffing** | `FLAG_SECURE` prevents screen recording, screenshots, and recents app switcher thumbnail previews. Sensitive clipboard contents are wiped automatically after 60 seconds. |
| **Ciphertext Swapping & Version Replay** | AAD binds note UUID, version, and algorithm identifier directly to the GCM authentication tag. Replaying old versions or moving ciphertexts between notes fails MAC validation. |

---

## 📊 Feature Status & Roadmap

### ✅ Completed & Fully Implemented

- [x] **Core Specifications:**
  - `SPEC-1`: Key Hierarchy & Multi-Tier Wrapping (Argon2id KEK, Master Key, Keystore OAEP wrap, Recovery Key wrap).
  - `SPEC-2`: Sync Protocol Contract (Propose `version + 1`, atomic `user_sync.last_seq` commit order, FIFO outbox).
  - `SPEC-3`: Envelope V1 Binary Codec with frozen test vectors.
- [x] **Backend Infrastructure (Fastify + Prisma + PostgreSQL):**
  - Full relational schema (`users`, `user_sync`, `notes`, `processed_ops`, `refresh_tokens`, `wrapped_keys`, `devices`).
  - Argon2id password hashing and dual-token JWT rotation (15-min access / 7-day refresh).
  - Atomic push/pull sync engine (`POST /api/v1/sync/push`, `GET /api/v1/sync/pull`).
  - Idempotency via `processed_ops` and deterministic row locking.
  - Wrapped keys API (`PUT /api/v1/keys`, `GET /api/v1/keys`) with versioning and `kcv` validation.
  - Opaque envelope storage with SHA-256 checksum verification and legacy format support.
  - Comprehensive test suite (50/50 tests passing in Jest).
- [x] **Android Security & Cryptography:**
  - AES-256-GCM Envelope V1 codec with AAD validation.
  - Argon2id key derivation & Android Keystore StrongBox master key wrapping.
  - BiometricPrompt authentication (`BIOMETRIC_STRONG`) with fallback to passphrase.
  - Memory zeroization on lock (`KeySessionManager` & `VaultLock`).
  - `FLAG_SECURE` window privacy and 60-second clipboard self-destruction.
  - Granular decryption failure diagnostics and 30-day non-destructive quarantine storage.
- [x] **Android Offline-First Notes & UX:**
  - Room v2 local storage with atomic outbox synchronization.
  - WorkManager background sync engine with real-time `ConnectivityObserver`.
  - Dual-Tab UI: **Notes** tab and ultra-secure **Vault** tab with hidden metadata.
  - Swipe actions (move to vault, delete with confirmation).
  - Material 3 theming (light/dark with Obsidian AA-contrast compliance).
  - Native markdown editor with instant preview.
  - Tag filtering and note pinning.
  - In-memory zero-knowledge search over decrypted notes.
  - Interactive sync status indicators and side-by-side conflict resolution dialog.
  - User Profile screen with credentials, security statistics, and sign-out.
  - CI / CD GitHub Actions workflow running tests across both tracks.

### ⏳ Upcoming & Roadmap Items

- [ ] **FE-9 (Extended UX):** Dedicated UI screen in Settings for exporting and importing the 24-word BIP39 recovery mnemonic (crypto wrapping is already operational).
- [ ] **BE-8 (Production Mailer):** Connect external SMTP mail provider (SendGrid/Resend) for production password reset emails.
- [ ] **v2 Enhancements:** End-to-end encrypted file and image attachments, multi-user note sharing.

---

## 📄 License

Distributed under the Apache 2.0 License. See `LICENSE` for more information.
