import { describe, it, expect, beforeAll, afterAll } from '@jest/globals';
import { randomUUID } from 'crypto';
import { buildApp } from '../src/app';
import { prisma } from '../src/db/prisma';

describe('Sync API (BE-3 Push & Pull Integration Tests)', () => {
  const app = buildApp();

  let userAToken: string;
  let userAId: string;
  let userBToken: string;
  let userBId: string;

  beforeAll(async () => {
    // Clean up test data
    await prisma.user.deleteMany({
      where: { email: { contains: '@synctest.com' } },
    });

    // Create User A
    const resA = await app.inject({
      method: 'POST',
      url: '/auth/register',
      payload: {
        email: `usera_${Date.now()}@synctest.com`,
        password: 'Password123!',
      },
    });
    const bodyA = JSON.parse(resA.body);
    userAToken = bodyA.accessToken;
    userAId = bodyA.user.id;

    // Create User B (for cross-tenant checks)
    const resB = await app.inject({
      method: 'POST',
      url: '/auth/register',
      payload: {
        email: `userb_${Date.now()}@synctest.com`,
        password: 'Password123!',
      },
    });
    const bodyB = JSON.parse(resB.body);
    userBToken = bodyB.accessToken;
    userBId = bodyB.user.id;
  });

  afterAll(async () => {
    await prisma.user.deleteMany({
      where: { email: { contains: '@synctest.com' } },
    });
    await app.close();
    await prisma.$disconnect();
  });

  describe('Authentication & Authorization', () => {
    it('should reject unauthenticated push request with 401', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/sync/push',
        payload: [],
      });
      expect(response.statusCode).toBe(401);
    });

    it('should reject unauthenticated pull request with 401', async () => {
      const response = await app.inject({
        method: 'GET',
        url: '/sync/pull',
      });
      expect(response.statusCode).toBe(401);
    });
  });

  describe('POST /sync/push – Note Creation & Batch Operations', () => {
    it('should successfully create a single note (create: base_version=0, version=1)', async () => {
      const noteId = randomUUID();
      const opId = randomUUID();
      const ciphertext = Buffer.from('encrypted_payload_1').toString('base64');
      const nonce = Buffer.from('random_nonce_12b').toString('base64');

      const response = await app.inject({
        method: 'POST',
        url: '/sync/push',
        headers: { authorization: `Bearer ${userAToken}` },
        payload: [
          {
            op_id: opId,
            note_id: noteId,
            type: 'create',
            base_version: 0,
            version: 1,
            ciphertext,
            nonce,
          },
        ],
      });

      expect(response.statusCode).toBe(200);
      const results = JSON.parse(response.body);
      expect(Array.isArray(results)).toBe(true);
      expect(results).toHaveLength(1);

      const res = results[0];
      expect(res.op_id).toBe(opId);
      expect(res.note_id).toBe(noteId);
      expect(res.status).toBe('applied');
      expect(res.result).toBe('applied');
      expect(res.server_version).toBe(1);
      expect(typeof res.server_seq).toBe('number');
      expect(res.server_seq).toBeGreaterThan(0);

      // Verify DB state
      const dbNote = await prisma.note.findUnique({ where: { id: noteId } });
      expect(dbNote).not.toBeNull();
      expect(dbNote?.version).toBe(1);
      expect(dbNote?.deleted).toBe(false);
      expect(Number(dbNote?.serverSeq)).toBe(res.server_seq);
    });

    it('should support object payload format { ops: [...] } and return { results: [...] }', async () => {
      const noteId = randomUUID();
      const opId = randomUUID();

      const response = await app.inject({
        method: 'POST',
        url: '/api/v1/sync/push',
        headers: { authorization: `Bearer ${userAToken}` },
        payload: {
          ops: [
            {
              op_id: opId,
              note_id: noteId,
              type: 'create',
              base_version: 0,
              version: 1,
              ciphertext: Buffer.from('hello_v1').toString('base64'),
              nonce: Buffer.from('nonce_12345678').toString('base64'),
            },
          ],
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.results).toBeDefined();
      expect(Array.isArray(body.results)).toBe(true);
      expect(body.results[0].status).toBe('applied');
      expect(body.results[0].server_version).toBe(1);
    });

    it('should process a multi-op batch in transaction order (create, then update in same batch)', async () => {
      const noteId = randomUUID();
      const opCreate = randomUUID();
      const opUpdate = randomUUID();

      const response = await app.inject({
        method: 'POST',
        url: '/sync/push',
        headers: { authorization: `Bearer ${userAToken}` },
        payload: [
          {
            op_id: opCreate,
            note_id: noteId,
            type: 'create',
            base_version: 0,
            version: 1,
            ciphertext: Buffer.from('initial_body').toString('base64'),
            nonce: Buffer.from('nonce_one_1234').toString('base64'),
          },
          {
            op_id: opUpdate,
            note_id: noteId,
            type: 'update',
            base_version: 1,
            version: 2,
            ciphertext: Buffer.from('updated_body').toString('base64'),
            nonce: Buffer.from('nonce_two_1234').toString('base64'),
          },
        ],
      });

      expect(response.statusCode).toBe(200);
      const results = JSON.parse(response.body);
      expect(results).toHaveLength(2);
      expect(results[0].status).toBe('applied');
      expect(results[0].server_version).toBe(1);
      expect(results[1].status).toBe('applied');
      expect(results[1].server_version).toBe(2);
      expect(results[1].server_seq).toBeGreaterThan(results[0].server_seq);

      const dbNote = await prisma.note.findUnique({ where: { id: noteId } });
      expect(dbNote?.version).toBe(2);
      expect(Buffer.from(dbNote!.ciphertext!).toString('base64')).toBe(
        Buffer.from('updated_body').toString('base64')
      );
    });

    it('should support soft-delete operation (tombstone with ciphertext: null, nonce: null)', async () => {
      const noteId = randomUUID();
      const opCreate = randomUUID();
      const opDelete = randomUUID();

      // Create note
      await app.inject({
        method: 'POST',
        url: '/sync/push',
        headers: { authorization: `Bearer ${userAToken}` },
        payload: [
          {
            op_id: opCreate,
            note_id: noteId,
            type: 'create',
            base_version: 0,
            version: 1,
            ciphertext: Buffer.from('to_be_deleted').toString('base64'),
            nonce: Buffer.from('nonce_to_del12').toString('base64'),
          },
        ],
      });

      // Delete note
      const response = await app.inject({
        method: 'POST',
        url: '/sync/push',
        headers: { authorization: `Bearer ${userAToken}` },
        payload: [
          {
            op_id: opDelete,
            note_id: noteId,
            type: 'delete',
            base_version: 1,
            version: 2,
            ciphertext: null,
            nonce: null,
          },
        ],
      });

      expect(response.statusCode).toBe(200);
      const results = JSON.parse(response.body);
      expect(results[0].status).toBe('applied');
      expect(results[0].server_version).toBe(2);

      const dbNote = await prisma.note.findUnique({ where: { id: noteId } });
      expect(dbNote?.deleted).toBe(true);
      expect(dbNote?.ciphertext).toBeNull();
      expect(dbNote?.nonce).toBeNull();
      expect(dbNote?.version).toBe(2);
    });
  });

  describe('Conflict Detection & Version Invariants', () => {
    it('should return conflict when create has base_version != 0 or version != 1', async () => {
      const noteId = randomUUID();
      const response = await app.inject({
        method: 'POST',
        url: '/sync/push',
        headers: { authorization: `Bearer ${userAToken}` },
        payload: [
          {
            op_id: randomUUID(),
            note_id: noteId,
            type: 'create',
            base_version: 1, // Invalid for create
            version: 2,
            ciphertext: Buffer.from('abc').toString('base64'),
            nonce: Buffer.from('123456789012').toString('base64'),
          },
        ],
      });

      expect(response.statusCode).toBe(200);
      const results = JSON.parse(response.body);
      expect(results[0].status).toBe('conflict');
      expect(results[0].result).toBe('conflict');
      expect(results[0].server_version).toBe(0);
      expect(results[0].server_seq).toBeNull();
    });

    it('should return conflict when attempting to create a note that already exists', async () => {
      const noteId = randomUUID();

      // Create note at version 1
      await app.inject({
        method: 'POST',
        url: '/sync/push',
        headers: { authorization: `Bearer ${userAToken}` },
        payload: [
          {
            op_id: randomUUID(),
            note_id: noteId,
            type: 'create',
            base_version: 0,
            version: 1,
            ciphertext: Buffer.from('abc').toString('base64'),
            nonce: Buffer.from('123456789012').toString('base64'),
          },
        ],
      });

      // Second create attempt with base_version = 0
      const response = await app.inject({
        method: 'POST',
        url: '/sync/push',
        headers: { authorization: `Bearer ${userAToken}` },
        payload: [
          {
            op_id: randomUUID(),
            note_id: noteId,
            type: 'create',
            base_version: 0,
            version: 1,
            ciphertext: Buffer.from('abc2').toString('base64'),
            nonce: Buffer.from('123456789012').toString('base64'),
          },
        ],
      });

      expect(response.statusCode).toBe(200);
      const results = JSON.parse(response.body);
      expect(results[0].status).toBe('conflict');
      expect(results[0].server_version).toBe(1);
    });

    it('should return conflict when base_version does not match current server version on update', async () => {
      const noteId = randomUUID();

      // Create note (v1)
      await app.inject({
        method: 'POST',
        url: '/sync/push',
        headers: { authorization: `Bearer ${userAToken}` },
        payload: [
          {
            op_id: randomUUID(),
            note_id: noteId,
            type: 'create',
            base_version: 0,
            version: 1,
            ciphertext: Buffer.from('v1').toString('base64'),
            nonce: Buffer.from('nonce1234567').toString('base64'),
          },
        ],
      });

      // Attempt update proposing base_version = 0 (stale, server is at 1)
      const response = await app.inject({
        method: 'POST',
        url: '/sync/push',
        headers: { authorization: `Bearer ${userAToken}` },
        payload: [
          {
            op_id: randomUUID(),
            note_id: noteId,
            type: 'update',
            base_version: 0,
            version: 1,
            ciphertext: Buffer.from('v_stale').toString('base64'),
            nonce: Buffer.from('nonce1234567').toString('base64'),
          },
        ],
      });

      expect(response.statusCode).toBe(200);
      const results = JSON.parse(response.body);
      expect(results[0].status).toBe('conflict');
      expect(results[0].server_version).toBe(1);
    });

    it('should return conflict when proposed version is not base_version + 1', async () => {
      const noteId = randomUUID();

      // Create note (v1)
      await app.inject({
        method: 'POST',
        url: '/sync/push',
        headers: { authorization: `Bearer ${userAToken}` },
        payload: [
          {
            op_id: randomUUID(),
            note_id: noteId,
            type: 'create',
            base_version: 0,
            version: 1,
            ciphertext: Buffer.from('v1').toString('base64'),
            nonce: Buffer.from('nonce1234567').toString('base64'),
          },
        ],
      });

      // Attempt update proposing version = base_version + 2 (skip version)
      const response = await app.inject({
        method: 'POST',
        url: '/sync/push',
        headers: { authorization: `Bearer ${userAToken}` },
        payload: [
          {
            op_id: randomUUID(),
            note_id: noteId,
            type: 'update',
            base_version: 1,
            version: 3, // Skipped version 2
            ciphertext: Buffer.from('v3').toString('base64'),
            nonce: Buffer.from('nonce1234567').toString('base64'),
          },
        ],
      });

      expect(response.statusCode).toBe(200);
      const results = JSON.parse(response.body);
      expect(results[0].status).toBe('conflict');
      expect(results[0].server_version).toBe(1);
    });

    it('should return conflict when updating a non-existent note', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/sync/push',
        headers: { authorization: `Bearer ${userAToken}` },
        payload: [
          {
            op_id: randomUUID(),
            note_id: randomUUID(),
            type: 'update',
            base_version: 1,
            version: 2,
            ciphertext: Buffer.from('v2').toString('base64'),
            nonce: Buffer.from('nonce1234567').toString('base64'),
          },
        ],
      });

      expect(response.statusCode).toBe(200);
      const results = JSON.parse(response.body);
      expect(results[0].status).toBe('conflict');
      expect(results[0].server_version).toBe(0);
    });
  });

  describe('Idempotency & Duplicate Handling', () => {
    it('should return duplicate when the same op_id is pushed again, without changing data or advancing seq', async () => {
      const noteId = randomUUID();
      const opId = randomUUID();

      // First push: applied
      const res1 = await app.inject({
        method: 'POST',
        url: '/sync/push',
        headers: { authorization: `Bearer ${userAToken}` },
        payload: [
          {
            op_id: opId,
            note_id: noteId,
            type: 'create',
            base_version: 0,
            version: 1,
            ciphertext: Buffer.from('first_content').toString('base64'),
            nonce: Buffer.from('first_nonce12').toString('base64'),
          },
        ],
      });
      const body1 = JSON.parse(res1.body);
      expect(body1[0].status).toBe('applied');
      const firstSeq = body1[0].server_seq;

      // Second push with identical op_id
      const res2 = await app.inject({
        method: 'POST',
        url: '/sync/push',
        headers: { authorization: `Bearer ${userAToken}` },
        payload: [
          {
            op_id: opId,
            note_id: noteId,
            type: 'create',
            base_version: 0,
            version: 1,
            ciphertext: Buffer.from('tampered_content').toString('base64'),
            nonce: Buffer.from('tampered_nonce').toString('base64'),
          },
        ],
      });

      expect(res2.statusCode).toBe(200);
      const body2 = JSON.parse(res2.body);
      expect(body2[0].status).toBe('duplicate');
      expect(body2[0].result).toBe('duplicate');
      expect(body2[0].server_version).toBe(1);
      expect(body2[0].server_seq).toBe(firstSeq);

      // Verify DB note was not overwritten
      const dbNote = await prisma.note.findUnique({ where: { id: noteId } });
      expect(Buffer.from(dbNote!.ciphertext!).toString('base64')).toBe(
        Buffer.from('first_content').toString('base64')
      );
    });
  });

  describe('System Limits & Payload Size Enforcement', () => {
    it('should reject push with ciphertext exceeding 1 MB with 413 Payload Too Large', async () => {
      const noteId = randomUUID();
      // 1.1 MB buffer
      const largeBuffer = Buffer.alloc(1100000, 65);

      const response = await app.inject({
        method: 'POST',
        url: '/sync/push',
        headers: { authorization: `Bearer ${userAToken}` },
        payload: [
          {
            op_id: randomUUID(),
            note_id: noteId,
            type: 'create',
            base_version: 0,
            version: 1,
            ciphertext: largeBuffer.toString('base64'),
            nonce: Buffer.from('123456789012').toString('base64'),
          },
        ],
      });

      expect(response.statusCode).toBe(413);
      const body = JSON.parse(response.body);
      expect(body.error).toBe('Payload Too Large');
    });

    it('should reject push with more than 100 operations with 400 Bad Request', async () => {
      const ops = Array.from({ length: 101 }, () => ({
        op_id: randomUUID(),
        note_id: randomUUID(),
        type: 'create' as const,
        base_version: 0,
        version: 1,
        ciphertext: Buffer.from('data').toString('base64'),
        nonce: Buffer.from('123456789012').toString('base64'),
      }));

      const response = await app.inject({
        method: 'POST',
        url: '/sync/push',
        headers: { authorization: `Bearer ${userAToken}` },
        payload: ops,
      });

      expect(response.statusCode).toBe(400);
      const body = JSON.parse(response.body);
      expect(body.error).toBe('Bad Request');
    });
  });

  describe('GET /sync/pull – Cursor Filtering & Pagination', () => {
    let noteIds: string[] = [];

    beforeAll(async () => {
      // Create 5 distinct notes for User A
      const ops = [];
      for (let i = 0; i < 5; i++) {
        const id = randomUUID();
        noteIds.push(id);
        ops.push({
          op_id: randomUUID(),
          note_id: id,
          type: 'create' as const,
          base_version: 0,
          version: 1,
          ciphertext: Buffer.from(`note_content_${i}`).toString('base64'),
          nonce: Buffer.from(`nonce_${i}_12345`).toString('base64'),
        });
      }

      await app.inject({
        method: 'POST',
        url: '/sync/push',
        headers: { authorization: `Bearer ${userAToken}` },
        payload: ops,
      });
    });

    it('should pull all notes updated since cursor 0', async () => {
      const response = await app.inject({
        method: 'GET',
        url: '/sync/pull?since=0&limit=50',
        headers: { authorization: `Bearer ${userAToken}` },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.changes.length).toBeGreaterThanOrEqual(5);
      expect(typeof body.next_cursor).toBe('number');
      expect(body.has_more).toBe(false);

      const firstChange = body.changes[0];
      expect(firstChange.id).toBeDefined();
      expect(firstChange.ciphertext).toBeDefined();
      expect(firstChange.nonce).toBeDefined();
      expect(firstChange.version).toBe(1);
      expect(firstChange.deleted).toBe(false);
      expect(typeof firstChange.updated_at).toBe('number');
      expect(typeof firstChange.server_seq).toBe('number');
    });

    it('should paginate pull results when limit is smaller than available changes', async () => {
      // Pull first 2 notes
      const page1Res = await app.inject({
        method: 'GET',
        url: '/sync/pull?since=0&limit=2',
        headers: { authorization: `Bearer ${userAToken}` },
      });

      expect(page1Res.statusCode).toBe(200);
      const page1 = JSON.parse(page1Res.body);
      expect(page1.changes).toHaveLength(2);
      expect(page1.has_more).toBe(true);
      expect(page1.next_cursor).toBe(page1.changes[1].server_seq);

      // Pull next page using next_cursor
      const page2Res = await app.inject({
        method: 'GET',
        url: `/sync/pull?since=${page1.next_cursor}&limit=2`,
        headers: { authorization: `Bearer ${userAToken}` },
      });

      expect(page2Res.statusCode).toBe(200);
      const page2 = JSON.parse(page2Res.body);
      expect(page2.changes).toHaveLength(2);
      expect(page2.changes[0].server_seq).toBeGreaterThan(page1.next_cursor);
    });

    it('should return empty changes and preserve cursor when up to date', async () => {
      const pullRes = await app.inject({
        method: 'GET',
        url: '/sync/pull?since=0&limit=100',
        headers: { authorization: `Bearer ${userAToken}` },
      });
      const pullBody = JSON.parse(pullRes.body);
      const currentCursor = pullBody.next_cursor;

      const emptyRes = await app.inject({
        method: 'GET',
        url: `/sync/pull?since=${currentCursor}&limit=50`,
        headers: { authorization: `Bearer ${userAToken}` },
      });

      expect(emptyRes.statusCode).toBe(200);
      const emptyBody = JSON.parse(emptyRes.body);
      expect(emptyBody.changes).toHaveLength(0);
      expect(emptyBody.has_more).toBe(false);
      expect(emptyBody.next_cursor).toBe(currentCursor);
    });
  });

  describe('Multi-Tenant Isolation', () => {
    it('should strictly isolate pull data between users (User B sees none of User A notes)', async () => {
      const response = await app.inject({
        method: 'GET',
        url: '/sync/pull?since=0&limit=50',
        headers: { authorization: `Bearer ${userBToken}` },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.changes).toHaveLength(0);
      expect(body.next_cursor).toBe(0);
    });

    it('should reject User B attempting to mutate User A note with 403 Forbidden', async () => {
      // User A creates a note
      const noteId = randomUUID();
      await app.inject({
        method: 'POST',
        url: '/sync/push',
        headers: { authorization: `Bearer ${userAToken}` },
        payload: [
          {
            op_id: randomUUID(),
            note_id: noteId,
            type: 'create',
            base_version: 0,
            version: 1,
            ciphertext: Buffer.from('user_a_secret').toString('base64'),
            nonce: Buffer.from('nonce12345678').toString('base64'),
          },
        ],
      });

      // User B attempts to update User A's note
      const attackRes = await app.inject({
        method: 'POST',
        url: '/sync/push',
        headers: { authorization: `Bearer ${userBToken}` },
        payload: [
          {
            op_id: randomUUID(),
            note_id: noteId,
            type: 'update',
            base_version: 1,
            version: 2,
            ciphertext: Buffer.from('injected_data').toString('base64'),
            nonce: Buffer.from('nonce12345678').toString('base64'),
          },
        ],
      });

      expect(attackRes.statusCode).toBe(403);
      const body = JSON.parse(attackRes.body);
      expect(body.error).toBe('Forbidden');
    });
  });
});
