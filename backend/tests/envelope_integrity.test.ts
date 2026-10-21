import { describe, it, expect, beforeAll, afterAll, beforeEach } from '@jest/globals';
import crypto, { randomUUID } from 'crypto';
import { buildApp } from '../src/app';
import { prisma } from '../src/db/prisma';
import { FastifyInstance } from 'fastify';

describe('Envelope Storage, Migration & Integrity (NV-BE-2, NV-BE-3, NV-BE-4)', () => {
  let app: FastifyInstance;
  let userToken: string;
  let userId: string;

  beforeAll(async () => {
    app = buildApp();
    await app.ready();
  });

  afterAll(async () => {
    await prisma.user.deleteMany({
      where: { email: { contains: 'env_test_' } },
    });
    await app.close();
    await prisma.$disconnect();
  });

  beforeEach(async () => {
    const email = `env_test_${Date.now()}_${Math.random().toString(36).substring(7)}@example.com`;
    const regRes = await app.inject({
      method: 'POST',
      url: '/auth/register',
      payload: {
        email,
        password: 'Password123!',
      },
    });

    expect(regRes.statusCode).toBe(201);
    const body = JSON.parse(regRes.body);
    userToken = body.accessToken;
    userId = body.user.id;
  });

  describe('NV-BE-2: Opaque Envelope Storage', () => {
    it('push then pull returns byte-identical envelopes with random bytes (including zeros and bytes above 0x7F)', async () => {
      const noteId = randomUUID();
      const opId = randomUUID();

      // Create random byte array including 0x00 and bytes > 0x7F
      const rawEnvelope = Buffer.alloc(128);
      crypto.randomFillSync(rawEnvelope);
      rawEnvelope[0] = 0x01; // format version 1
      rawEnvelope[1] = 0x00; // zero byte
      rawEnvelope[10] = 0xff; // > 0x7F
      rawEnvelope[50] = 0x80; // 0x80

      const envelopeB64Url = rawEnvelope.toString('base64url');
      const checksumBuf = crypto.createHash('sha256').update(rawEnvelope).digest();
      const checksumB64Url = checksumBuf.toString('base64url');
      const keyIdB64Url = Buffer.from('testkey1').toString('base64url');

      const pushRes = await app.inject({
        method: 'POST',
        url: '/sync/push',
        headers: { authorization: `Bearer ${userToken}` },
        payload: [
          {
            op_id: opId,
            note_id: noteId,
            type: 'create',
            base_version: 0,
            version: 1,
            envelope: envelopeB64Url,
            format_version: 1,
            key_id: keyIdB64Url,
            checksum: checksumB64Url,
          },
        ],
      });

      expect(pushRes.statusCode).toBe(200);
      const pushResults = JSON.parse(pushRes.body);
      expect(pushResults[0].status).toBe('applied');

      // Pull and check exact bytes
      const pullRes = await app.inject({
        method: 'GET',
        url: '/sync/pull?since=0',
        headers: { authorization: `Bearer ${userToken}` },
      });

      expect(pullRes.statusCode).toBe(200);
      const pullData = JSON.parse(pullRes.body);
      expect(pullData.changes).toHaveLength(1);

      const pulledNote = pullData.changes[0];
      expect(pulledNote.id).toBe(noteId);
      expect(pulledNote.format_version).toBe(1);
      expect(pulledNote.envelope).toBe(envelopeB64Url);
      expect(pulledNote.key_id).toBe(keyIdB64Url);
      expect(pulledNote.checksum).toBe(checksumB64Url);

      // Verify decoded bytes byte-for-byte
      const pulledDecoded = Buffer.from(pulledNote.envelope, 'base64url');
      expect(pulledDecoded.equals(rawEnvelope)).toBe(true);
    });

    it('server verifies SHA-256(envelope) == checksum and rejects a mismatch with 400', async () => {
      const noteId = randomUUID();
      const opId = randomUUID();
      const rawEnvelope = Buffer.from('hello envelope secret content');
      const envelopeB64Url = rawEnvelope.toString('base64url');
      const badChecksum = crypto.createHash('sha256').update('wrong data').digest().toString('base64url');

      const pushRes = await app.inject({
        method: 'POST',
        url: '/sync/push',
        headers: { authorization: `Bearer ${userToken}` },
        payload: [
          {
            op_id: opId,
            note_id: noteId,
            type: 'create',
            base_version: 0,
            version: 1,
            envelope: envelopeB64Url,
            format_version: 1,
            checksum: badChecksum,
          },
        ],
      });

      expect(pushRes.statusCode).toBe(400);
      const body = JSON.parse(pushRes.body);
      expect(body.message).toContain('checksum mismatch');
    });

    it('rejects envelopes over the 1MB size limit with 413', async () => {
      const noteId = randomUUID();
      const opId = randomUUID();
      const oversizedBuf = Buffer.alloc(1024 * 1024 + 10);
      const oversizedB64Url = oversizedBuf.toString('base64url');

      const pushRes = await app.inject({
        method: 'POST',
        url: '/sync/push',
        headers: { authorization: `Bearer ${userToken}` },
        payload: [
          {
            op_id: opId,
            note_id: noteId,
            type: 'create',
            base_version: 0,
            version: 1,
            envelope: oversizedB64Url,
            format_version: 1,
          },
        ],
      });

      expect(pushRes.statusCode).toBe(413);
    });

    it('legacy rows without envelope column still pull correctly after migration', async () => {
      // Simulate existing legacy row in database directly
      const legacyNoteId = randomUUID();
      const nonce = Buffer.from('12bytesnonce');
      const ciphertext = Buffer.from('encrypted_legacy_data');

      // Monotonically increment user_sync
      await prisma.$executeRaw`
        INSERT INTO "user_sync" ("user_id", "last_seq")
        VALUES (${userId}::uuid, 1)
        ON CONFLICT ("user_id") DO UPDATE SET "last_seq" = "user_sync"."last_seq" + 1
      `;

      await prisma.note.create({
        data: {
          id: legacyNoteId,
          userId,
          ciphertext,
          nonce,
          envelope: null,
          formatVersion: 0,
          version: 1,
          serverSeq: 1n,
          deleted: false,
        },
      });

      const pullRes = await app.inject({
        method: 'GET',
        url: '/sync/pull?since=0',
        headers: { authorization: `Bearer ${userToken}` },
      });

      expect(pullRes.statusCode).toBe(200);
      const pullData = JSON.parse(pullRes.body);
      expect(pullData.changes).toHaveLength(1);

      const legacyPulled = pullData.changes[0];
      expect(legacyPulled.id).toBe(legacyNoteId);
      expect(legacyPulled.format_version).toBe(0);
      expect(legacyPulled.ciphertext).toBe(ciphertext.toString('base64'));
      expect(legacyPulled.nonce).toBe(nonce.toString('base64'));
      // Fallback envelope is nonce || ciphertext
      const expectedFallback = Buffer.concat([nonce, ciphertext]).toString('base64url');
      expect(legacyPulled.envelope).toBe(expectedFallback);
    });
  });

  describe('NV-BE-3: Version Rules for Migration Updates', () => {
    it('migration update from legacy (format 0) to v1 is accepted and returns server_version', async () => {
      // 1. Create legacy note
      const noteId = randomUUID();
      const createRes = await app.inject({
        method: 'POST',
        url: '/sync/push',
        headers: { authorization: `Bearer ${userToken}` },
        payload: [
          {
            op_id: randomUUID(),
            note_id: noteId,
            type: 'create',
            base_version: 0,
            version: 1,
            ciphertext: Buffer.from('legacy_ciphertext').toString('base64'),
            nonce: Buffer.from('legacy_nonce12').toString('base64'),
          },
        ],
      });
      expect(createRes.statusCode).toBe(200);

      // 2. Client re-encrypts as v1 (migrate-on-read): sends version = base_version + 1 = 2
      const newEnvelope = Buffer.from('migrated_v1_envelope_content');
      const envB64Url = newEnvelope.toString('base64url');
      const checksumB64Url = crypto.createHash('sha256').update(newEnvelope).digest('base64url');
      const keyIdB64Url = Buffer.from('keyid123').toString('base64url');

      const migrateRes = await app.inject({
        method: 'POST',
        url: '/sync/push',
        headers: { authorization: `Bearer ${userToken}` },
        payload: [
          {
            op_id: randomUUID(),
            note_id: noteId,
            type: 'update',
            base_version: 1,
            version: 2,
            envelope: envB64Url,
            format_version: 1,
            key_id: keyIdB64Url,
            checksum: checksumB64Url,
          },
        ],
      });

      expect(migrateRes.statusCode).toBe(200);
      const results = JSON.parse(migrateRes.body);
      expect(results[0].status).toBe('applied');
      expect(results[0].server_version).toBe(2);

      // Verify DB now holds format 1 envelope
      const dbNote = await prisma.note.findUnique({ where: { id: noteId } });
      expect(dbNote?.formatVersion).toBe(1);
      expect(dbNote?.version).toBe(2);
      expect(Buffer.from(dbNote!.envelope!).equals(newEnvelope)).toBe(true);
    });

    it('stale base_version in migration update returns conflict', async () => {
      const noteId = randomUUID();
      // Create v1
      await app.inject({
        method: 'POST',
        url: '/sync/push',
        headers: { authorization: `Bearer ${userToken}` },
        payload: [
          {
            op_id: randomUUID(),
            note_id: noteId,
            type: 'create',
            base_version: 0,
            version: 1,
            envelope: Buffer.from('v1_envelope').toString('base64url'),
            format_version: 1,
          },
        ],
      });

      // Update to v2
      await app.inject({
        method: 'POST',
        url: '/sync/push',
        headers: { authorization: `Bearer ${userToken}` },
        payload: [
          {
            op_id: randomUUID(),
            note_id: noteId,
            type: 'update',
            base_version: 1,
            version: 2,
            envelope: Buffer.from('v2_envelope').toString('base64url'),
            format_version: 1,
          },
        ],
      });

      // Stale update sending base_version = 1 when server is at version 2
      const staleRes = await app.inject({
        method: 'POST',
        url: '/sync/push',
        headers: { authorization: `Bearer ${userToken}` },
        payload: [
          {
            op_id: randomUUID(),
            note_id: noteId,
            type: 'update',
            base_version: 1,
            version: 2,
            envelope: Buffer.from('stale_envelope').toString('base64url'),
            format_version: 1,
          },
        ],
      });

      expect(staleRes.statusCode).toBe(200);
      const results = JSON.parse(staleRes.body);
      expect(results[0].status).toBe('conflict');
      expect(results[0].server_version).toBe(2);
    });

    it('rejects unsupported format_version (> 1) with 400 validation error', async () => {
      const pushRes = await app.inject({
        method: 'POST',
        url: '/sync/push',
        headers: { authorization: `Bearer ${userToken}` },
        payload: [
          {
            op_id: randomUUID(),
            note_id: randomUUID(),
            type: 'create',
            base_version: 0,
            version: 1,
            envelope: Buffer.from('v99').toString('base64url'),
            format_version: 99,
          },
        ],
      });

      expect(pushRes.statusCode).toBe(400);
      const body = JSON.parse(pushRes.body);
      expect(body.details[0].message).toContain('Unsupported format_version');
    });
  });

  describe('NV-BE-4: Keys Integrity & Concurrency', () => {
    it('concurrent PUT /keys calls leave exactly one winner', async () => {
      const passWrapped1 = Buffer.from('pass-mk-1').toString('base64');
      const passWrapped2 = Buffer.from('pass-mk-2').toString('base64');
      const salt = Buffer.from('salt1234').toString('base64');
      const kdf = { iterations: 3, memory: 65536 };

      // Fire 2 concurrent PUT /keys requests for the fresh user with no current_key_version
      const [res1, res2] = await Promise.all([
        app.inject({
          method: 'PUT',
          url: '/keys',
          headers: { authorization: `Bearer ${userToken}` },
          payload: {
            pass_wrapped_mk: passWrapped1,
            pass_salt: salt,
            kdf_params: kdf,
            recovery_wrapped_mk: passWrapped1,
          },
        }),
        app.inject({
          method: 'PUT',
          url: '/keys',
          headers: { authorization: `Bearer ${userToken}` },
          payload: {
            pass_wrapped_mk: passWrapped2,
            pass_salt: salt,
            kdf_params: kdf,
            recovery_wrapped_mk: passWrapped2,
          },
        }),
      ]);

      const statusCodes = [res1.statusCode, res2.statusCode].sort();
      // Exactly one must succeed (201) and one must be rejected (409)
      expect(statusCodes).toEqual([201, 409]);

      // Exactly one row exists in wrapped_keys
      const keysCount = await prisma.wrappedKeys.count({ where: { userId } });
      expect(keysCount).toBe(1);
    });
  });
});
