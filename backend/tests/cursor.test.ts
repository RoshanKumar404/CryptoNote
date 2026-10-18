import { describe, it, expect, beforeAll, afterAll } from '@jest/globals';
import { randomUUID } from 'crypto';
import { buildApp } from '../src/app';
import { prisma } from '../src/db/prisma';

describe('Cursor & Overlapping Transactions (BE-3)', () => {
  const app = buildApp();
  let userToken: string;
  let userId: string;

  beforeAll(async () => {
    // Clean up test user
    await prisma.user.deleteMany({
      where: { email: { contains: '@cursortest.com' } },
    });

    const res = await app.inject({
      method: 'POST',
      url: '/auth/register',
      payload: {
        email: `cursor_${Date.now()}@cursortest.com`,
        password: 'Password123!',
      },
    });
    const body = JSON.parse(res.body);
    userToken = body.accessToken;
    userId = body.user.id;
  });

  afterAll(async () => {
    await prisma.user.deleteMany({
      where: { email: { contains: '@cursortest.com' } },
    });
    await app.close();
    await prisma.$disconnect();
  });

  it('should serialize overlapping concurrent pushes and guarantee commit-order sequence numbering without gaps or skipped rows', async () => {
    const batch1Size = 10;
    const batch2Size = 10;
    const totalNotes = batch1Size + batch2Size;

    const batch1Ops = Array.from({ length: batch1Size }, (_, i) => ({
      op_id: randomUUID(),
      note_id: randomUUID(),
      type: 'create' as const,
      base_version: 0,
      version: 1,
      ciphertext: Buffer.from(`batch_1_note_${i}`).toString('base64'),
      nonce: Buffer.from(`nonce_b1_${i}_123`).toString('base64'),
    }));

    const batch2Ops = Array.from({ length: batch2Size }, (_, i) => ({
      op_id: randomUUID(),
      note_id: randomUUID(),
      type: 'create' as const,
      base_version: 0,
      version: 1,
      ciphertext: Buffer.from(`batch_2_note_${i}`).toString('base64'),
      nonce: Buffer.from(`nonce_b2_${i}_123`).toString('base64'),
    }));

    const allPushedNoteIds = new Set([
      ...batch1Ops.map((o) => o.note_id),
      ...batch2Ops.map((o) => o.note_id),
    ]);

    // Dispatch both push batches simultaneously to test row-level concurrency serialization
    const [res1, res2] = await Promise.all([
      app.inject({
        method: 'POST',
        url: '/sync/push',
        headers: { authorization: `Bearer ${userToken}` },
        payload: batch1Ops,
      }),
      app.inject({
        method: 'POST',
        url: '/sync/push',
        headers: { authorization: `Bearer ${userToken}` },
        payload: batch2Ops,
      }),
    ]);

    expect(res1.statusCode).toBe(200);
    expect(res2.statusCode).toBe(200);

    const results1 = JSON.parse(res1.body);
    const results2 = JSON.parse(res2.body);

    expect(results1).toHaveLength(batch1Size);
    expect(results2).toHaveLength(batch2Size);

    // Verify all operations were successfully applied
    results1.forEach((r: any) => expect(r.status).toBe('applied'));
    results2.forEach((r: any) => expect(r.status).toBe('applied'));

    // Collect all allocated server sequence numbers
    const allSeqs: number[] = [
      ...results1.map((r: any) => r.server_seq),
      ...results2.map((r: any) => r.server_seq),
    ].sort((a, b) => a - b);

    // Verify that exactly 20 distinct contiguous sequence numbers were assigned (1..20)
    expect(allSeqs).toHaveLength(totalNotes);
    for (let i = 0; i < totalNotes; i++) {
      expect(allSeqs[i]).toBe(i + 1);
    }

    // Now paginate through pulls starting at cursor 0 with a small page size (3 items/page)
    const pulledNoteIds = new Set<string>();
    const pulledSeqs: number[] = [];
    let currentCursor = 0;
    let hasMore = true;
    let pageCount = 0;

    while (hasMore) {
      pageCount++;
      const pullRes = await app.inject({
        method: 'GET',
        url: `/sync/pull?since=${currentCursor}&limit=3`,
        headers: { authorization: `Bearer ${userToken}` },
      });

      expect(pullRes.statusCode).toBe(200);
      const pullBody = JSON.parse(pullRes.body);

      for (const change of pullBody.changes) {
        expect(pulledNoteIds.has(change.id)).toBe(false); // No duplicates
        pulledNoteIds.add(change.id);
        pulledSeqs.push(change.server_seq);
      }

      currentCursor = pullBody.next_cursor;
      hasMore = pullBody.has_more;

      // Safety guard against infinite loops
      expect(pageCount).toBeLessThan(15);
    }

    // CRITICAL ACCEPTANCE ASSERTIONS:
    // 1. Every single note pushed across the overlapping transactions was retrieved
    expect(pulledNoteIds.size).toBe(totalNotes);
    for (const noteId of allPushedNoteIds) {
      expect(pulledNoteIds.has(noteId)).toBe(true);
    }

    // 2. Pulled sequences are strictly monotonic with no skipped items
    expect(pulledSeqs).toEqual(allSeqs);
    expect(currentCursor).toBe(totalNotes);
  });

  it('should handle high concurrency of 5 simultaneous push batches without deadlocks or sequence collisions', async () => {
    const numBatches = 5;
    const opsPerBatch = 4;
    const totalNotes = numBatches * opsPerBatch;

    const batches = Array.from({ length: numBatches }, (_, bIndex) =>
      Array.from({ length: opsPerBatch }, (_, oIndex) => ({
        op_id: randomUUID(),
        note_id: randomUUID(),
        type: 'create' as const,
        base_version: 0,
        version: 1,
        ciphertext: Buffer.from(`stress_${bIndex}_${oIndex}`).toString('base64'),
        nonce: Buffer.from(`nonce_${bIndex}_${oIndex}_123`).toString('base64'),
      }))
    );

    // Execute 5 concurrent pushes
    const responses = await Promise.all(
      batches.map((batch) =>
        app.inject({
          method: 'POST',
          url: '/sync/push',
          headers: { authorization: `Bearer ${userToken}` },
          payload: batch,
        })
      )
    );

    for (const res of responses) {
      expect(res.statusCode).toBe(200);
      const results = JSON.parse(res.body);
      expect(results).toHaveLength(opsPerBatch);
      results.forEach((r: any) => expect(r.status).toBe('applied'));
    }

    // Pull changes with limit=50
    // Since previous test inserted 20 notes, these new 20 notes will have seq 21..40
    const pullRes = await app.inject({
      method: 'GET',
      url: `/sync/pull?since=20&limit=50`,
      headers: { authorization: `Bearer ${userToken}` },
    });

    expect(pullRes.statusCode).toBe(200);
    const pullBody = JSON.parse(pullRes.body);
    expect(pullBody.changes).toHaveLength(totalNotes);

    const seqs = pullBody.changes.map((c: any) => c.server_seq);
    for (let i = 0; i < totalNotes; i++) {
      expect(seqs[i]).toBe(21 + i);
    }
  });
});
