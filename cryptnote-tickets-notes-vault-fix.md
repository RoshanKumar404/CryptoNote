# Crypto Note: Tickets for the Decryption Fix and the Notes / Vault Tabs

*Implements PRD section 12 (added 20 October 2026). Frontend: Kotlin (Android). Backend: Node.js + PostgreSQL.*

**Numbering.** I could not open your existing ticket file (BE-1 to BE-8, FE-1 to FE-10, SPEC-1/2), so these use their own prefix, `NV-`, to avoid clashing with it. Renumber them to continue your scheme when you merge.

**Order of work:** SPEC-3, then NV-BE-1 and NV-FE-1 and NV-FE-2 (in parallel), then NV-BE-2, NV-FE-3, NV-FE-4, then NV-FE-5 to NV-FE-7, then NV-FE-8 and NV-FE-9. Do not start the tabs before the envelope and key rules are done; the tabs sit on top of them.

**Definition of done (every ticket):** acceptance boxes ticked; tests green; no key material or note content in logs; PRD section 12 re-read once.

## Contract between frontend and backend

| Item | Value |
|---|---|
| Push body per op | `{ op_id, note_id, type, base_version, version, envelope? }`, envelope as base64url without padding |
| Pull item | the same fields plus `format_version`, `key_id`, `checksum`, `server_seq` |
| Checksum | SHA-256 of the raw envelope bytes, computed by the client, stored and returned by the server |
| `PUT /keys` | create-only; 409 if keys exist; replacing needs the current `key_version` |
| Supported format versions | 0 (legacy) and 1 |

---

## SPEC-3: Envelope v1 and key rules
**Size** S · **Pairs** NV-BE-1, NV-BE-2, NV-FE-1, NV-FE-2
- PRD section 12 is the spec. Add one frozen test vector to it (master key, note ID, version, plaintext, expected envelope hex) so both sides can check their codecs.
  * **Master Key (32B hex)**: `0101010101010101010101010101010101010101010101010101010101010101`
  * **Derived Key ID (8B hex)**: `3d158e58ca752a50` (first 8 bytes of HKDF-SHA256(MK, info="cryptnote/key-id/v1"))
  * **Derived Encryption Key (32B hex)**: `a0d729ca98a8ed8d8e617d4c83bf80836c69848ac87d46d70afd3d02f869c2ce`
  * **Note ID (UUID)**: `12345678-1234-5678-1234-567812345678`
  * **Version (Long)**: `1`
  * **Plaintext**: `"Hello CryptoNote Envelope V1"`
  * **Fixed Nonce (12B hex)**: `020202020202020202020202`
  * **AAD (34B hex)**: `01013d158e58ca752a50123456781234567812345678123456780000000000000001`
  * **Expected Envelope (hex)**: `01013d158e58ca752a50020202020202020202020202b2da0b39908e92bbebf849e5536e74ae5533b31886f8d0bba24a4c3e73d5f5ccc0e353430d138c1d96a74743`
  * **Expected Envelope (base64url)**: `AQE9FY5YynUqUAICAgICAgICAgICArLaCzmQjpK76_hJ5VNudK5VM7MYhvjQu6JKTD5z1fXMwONTQw0TjB2Wp0dD`
  * **SHA-256 Checksum (hex)**: `9dcb46180f9bccaeddc781e292c7fe49c4e57ef1477352da41662567c97bf336`
- [x] The vector is reproduced exactly by the Kotlin codec

---

# Backend tickets

## NV-BE-1: Keys API hardening
**Size** M · **Pairs** NV-FE-2
- `wrapped_keys` becomes append-only by version: primary key `(user_id, key_version)`, plus a `kcv` column (key check value).
- `PUT /keys` is create-only. A replacement must send the current `key_version` (If-Match style); a mismatch returns 409. Old versions are never deleted.
- `GET /keys` returns the latest version plus the list of older `key_version` values.

```sql
ALTER TABLE wrapped_keys DROP CONSTRAINT wrapped_keys_pkey;
ALTER TABLE wrapped_keys ADD COLUMN kcv bytea;
ALTER TABLE wrapped_keys ADD PRIMARY KEY (user_id, key_version);
```
- [x] A second `PUT /keys` with no current version returns 409 and changes nothing
- [x] A passphrase change creates `key_version + 1` and keeps the old row
- [x] The server never logs a key blob

## NV-BE-2: Opaque envelope storage
**Size** M · **Pairs** NV-FE-3
- Add `envelope bytea`, `format_version smallint`, `key_id bytea`, `checksum bytea` to `notes`. Existing rows keep `format_version = 0`.
- Do **not** drop the old `ciphertext` and `nonce` columns until every client has migrated and you have verified it.

```sql
ALTER TABLE notes
  ADD COLUMN envelope bytea,
  ADD COLUMN format_version smallint NOT NULL DEFAULT 0,
  ADD COLUMN key_id bytea,
  ADD COLUMN checksum bytea;
-- Copy legacy rows so nothing is lost. VERIFY the legacy layout (nonce then ciphertext?) first.
UPDATE notes SET envelope = nonce || ciphertext WHERE envelope IS NULL;
```
- Push accepts `envelope` (base64url) and stores the decoded bytes untouched. Pull returns it the same way. Reject envelopes over the size limit. Never parse them.
- The server verifies `SHA-256(envelope) == checksum` on push and rejects a mismatch.
- [x] Push then pull returns byte-identical envelopes (test with random bytes, including zeros and bytes above 0x7F)
- [x] Legacy rows still pull correctly after the migration
- [x] No request or error log contains an envelope

## NV-BE-3: Version rules for migration updates
**Size** S · **Pairs** NV-FE-3
- A re-encryption update (legacy to v1) is a normal update with `version = base_version + 1`. The server accepts format 0 and 1 and rejects any other format version with a clear error.
- [x] A migration update is accepted and returns `server_version`
- [x] A stale `base_version` still returns a conflict

## NV-BE-4: Integrity and key-overwrite tests
**Size** M · **Pairs** NV-FE-8
- Tests: byte-exact round trip; checksum mismatch rejected; `PUT /keys` overwrite rejected; concurrent `PUT /keys` calls leave exactly one winner; pull after a legacy-to-v1 migration.
- [x] All pass in CI

---

# Frontend tickets

## NV-FE-1: Envelope codec, legacy reader and golden fixtures
**Size** L · **Pairs** SPEC-3, NV-BE-2
- `EnvelopeV1.seal` and `open` per PRD 12.3. `open` returns a typed result so every failure has a name.

```kotlin
sealed interface OpenResult {
    class Opened(val plaintext: ByteArray) : OpenResult
    class UnknownFormat(val formatVersion: Int) : OpenResult
    class KeyMismatch(val noteKeyId: ByteArray) : OpenResult
    object Corrupt : OpenResult
}

object EnvelopeV1 {
    const val FORMAT = 1
    private const val ALG_AES_256_GCM = 1
    private const val HEADER = 10   // format(1) + alg(1) + keyId(8)
    private const val NONCE = 12

    fun seal(key: ByteArray, keyId: ByteArray, noteId: UUID, version: Long, plaintext: ByteArray): ByteArray {
        val nonce = ByteArray(NONCE).also { SecureRandom().nextBytes(it) }
        val header = byteArrayOf(FORMAT.toByte(), ALG_AES_256_GCM.toByte()) + keyId
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(aad(header, noteId, version))
        return header + nonce + cipher.doFinal(plaintext)
    }

    fun open(key: ByteArray, currentKeyId: ByteArray, noteId: UUID, version: Long, env: ByteArray): OpenResult {
        if (env.size < HEADER + NONCE + 16) return OpenResult.Corrupt
        if (env[0].toInt() != FORMAT) return OpenResult.UnknownFormat(env[0].toInt())
        val header = env.copyOfRange(0, HEADER)
        val keyId = header.copyOfRange(2, HEADER)
        if (!keyId.contentEquals(currentKeyId)) return OpenResult.KeyMismatch(keyId)
        val nonce = env.copyOfRange(HEADER, HEADER + NONCE)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(aad(header, noteId, version))
            OpenResult.Opened(cipher.doFinal(env, HEADER + NONCE, env.size - HEADER - NONCE))
        } catch (e: GeneralSecurityException) { OpenResult.Corrupt }
    }

    private fun aad(header: ByteArray, noteId: UUID, version: Long): ByteArray =
        ByteBuffer.allocate(header.size + 16 + 8).put(header)
            .putLong(noteId.mostSignificantBits).putLong(noteId.leastSignificantBits)
            .putLong(version).array()
}
```
- Wrap today's decryption code, unchanged, as `LegacyDecryptor` (format 0). Dispatch on the format byte (or the `format_version` column for legacy rows). **Never delete it.**
- Derive `key_id` with HKDF: first 8 bytes of `HKDF(MK, info = "cryptnote/key-id/v1")`.
- Save one golden fixture per released app version under `androidTest/assets/fixtures/` as JSON: master key (hex), note ID, version, envelope (base64url), expected plaintext.
- [x] The frozen test vector from SPEC-3 passes
- [x] Every fixture opens with the current code
- [x] Flipping one byte anywhere in the envelope returns `Corrupt`, or `KeyMismatch` if it is in the key ID

## NV-FE-2: Key lifecycle rules
**Size** L · **Pairs** NV-BE-1
- **Setup is fetch-first:** call `GET /keys`. If keys exist, run the restore flow (passphrase or recovery key). Generate a master key only when the server confirms there are none. If the fetch fails, show "Connect to set up" and stop; never generate.

```kotlin
sealed interface SetupDecision { object CreateNew : SetupDecision; object Restore : SetupDecision; object Blocked : SetupDecision }

suspend fun decideSetup(api: KeysApi): SetupDecision = when (val r = api.getKeys()) {
    is Result.Ok -> if (r.value.exists) SetupDecision.Restore else SetupDecision.CreateNew
    is Result.Failure -> SetupDecision.Blocked   // offline or error: never create a key on a guess
}
```
- Unlock reads salt and KDF parameters from the stored wrap record, never from constants. After unwrapping, verify the key check value; on failure show "Wrong passphrase", not a note error.
- If Android Keystore throws `KeyPermanentlyInvalidatedException` (or the key is missing), fall back to passphrase or recovery key. Do not regenerate.
- Keep a `key_version` history so older notes can still be opened by an older key.
- [x] Reinstall plus login restores the same `key_id`
- [x] With the network off, setup is blocked instead of creating a key
- [x] A wrong passphrase is reported as a wrong passphrase

## NV-FE-3: Local schema migration and migrate-on-read
**Size** L · **Pairs** NV-BE-2, NV-BE-3
- Room migration: add `format_version`, `key_id`, `checksum`, `is_vault` to `notes`; add a `quarantine` table (note ID, envelope, reason, saved_at). Write a Room migration test.
- Migrate on read: when a legacy note opens, enqueue a normal update (version + 1, re-encrypted as v1) through the outbox, in the same transaction as the local write.
- Store the checksum on every write and verify it after every pull (see NV-FE-4).
- [x] The Room migration test passes from every released schema version
- [x] A legacy note becomes a v1 note after one open, and the server holds the v1 envelope

## NV-FE-4: Decryption failure handling
**Size** M · **Pairs** NV-BE-2
- Replace the "Unreadable Note" dialog. Map each result to a message and actions:

| Result | Message | Actions |
|---|---|---|
| UnknownFormat | "This note was saved by a newer version. Update the app." | Open Play listing |
| KeyMismatch | "This note was saved under a different key." | Try recovery key, try older key versions |
| Corrupt | "This note is damaged on this device." | Download again from the cloud |
| Checksum mismatch after pull | "The cloud copy changed in transit." | Retry |

- "Delete" requires confirmation and first copies the envelope to `quarantine`; quarantined notes can be restored for 30 days.
- A small diagnostics sheet shows `format_version`, key ID prefix, app version and the failure class (no keys, no content).
- [x] No unreadable note is deleted without a confirmation and a quarantined copy
- [x] Each failure class shows the right message (UI test per class)

## NV-FE-5: Notes and Vault tabs
**Size** L · **Pairs** none
- Bottom navigation: Notes (default after login) and Vault.
- Notes tab queries `is_vault = 0`. Vault tab queries `is_vault = 1` and shows locked rows: date plus ciphertext preview, no title. While locked, vault notes never appear in the Notes list or in search.
- Notes tab opens on a trusted device through biometric (Keystore wrap), with no passphrase prompt.
- `FLAG_SECURE` on both tabs.
- [x] After login, the first screen is the Notes tab
- [x] A locked vault shows no titles or content, including in recents

## NV-FE-6: Vault lock manager
**Size** L · **Pairs** none
- One state machine (Locked, Unlocking, Unlocked) in a single place.
- Unlock: the full passphrase derivation and unwrap (biometric if enabled in settings), key check value verified.
- Lock triggers: 1 minute idle, leaving the Vault route, screen off, app to background, "Lock now".
- Locking zeroes key bytes and decrypted text, clears the in-memory search index and the clipboard if the app copied from the vault.

```kotlin
sealed interface VaultState { object Locked : VaultState; object Unlocking : VaultState; object Unlocked : VaultState }

class VaultLock(
    private val scope: CoroutineScope,
    private val wipe: () -> Unit,            // zero keys, clear index, clear clipboard
    private val idleMs: Long = 60_000,
) {
    private val _state = MutableStateFlow<VaultState>(VaultState.Locked)
    val state: StateFlow<VaultState> = _state
    private var idleJob: Job? = null

    fun onUnlocked() { _state.value = VaultState.Unlocked; touch() }
    fun touch() { idleJob?.cancel(); idleJob = scope.launch { delay(idleMs); lock() } }
    fun lock() { idleJob?.cancel(); wipe(); _state.value = VaultState.Locked }
}
// Call lock() from: ProcessLifecycleOwner ON_STOP, a ACTION_SCREEN_OFF receiver, and leaving the Vault route.
```
- [x] Each lock trigger locks the vault (one UI or instrumented test per trigger)
- [x] A heap or log check shows no plaintext or key after lock

## NV-FE-7: Move to Vault and Move out
**Size** M · **Pairs** none
- Requires the vault unlocked. Flip `is_vault` inside the encrypted payload, mirror it in the local column, save as a new version through the outbox.
- [x] A moved note disappears from one tab and appears in the other
- [x] The server cannot tell which notes are in the vault (the flag exists only inside the payload)

## NV-FE-8: Upgrade compatibility CI
**Size** M · **Pairs** NV-BE-4
- Unit tests load every golden fixture (NV-FE-1).
- Instrumented upgrade test: install the previous release APK, create a note, install the new APK over it with `adb install -r`, then check the note still opens.
- Run both on every pull request.
- [x] CI fails if any released version's fixture stops opening

## NV-FE-9: Decrypt diagnostics
**Size** S · **Pairs** none
- Log `key_id` prefix, `format_version`, app version and result class on every decrypt. Never log keys, plaintext or envelopes. Surface the last 20 results in a debug screen.
- [x] A review of the logs finds no key material or note content
