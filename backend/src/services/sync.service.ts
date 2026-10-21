import crypto from 'crypto';
import { prisma } from '../db/prisma';
import { PushOperation, PushResultItem, PullResponse, PullChangeItem } from '../schemas/sync.schema';

export class SyncError extends Error {
  constructor(
    public statusCode: number,
    public error: string,
    message: string
  ) {
    super(message);
    this.name = 'SyncError';
  }
}

export class SyncService {
  /**
   * Processes a batch of note mutations inside a single database transaction.
   * Row-level locking on user_sync ensures that sequence order strictly equals commit order.
   */
  async push(userId: string, rawOps: PushOperation[]): Promise<PushResultItem[]> {
    if (rawOps.length > 100) {
      throw new SyncError(400, 'Bad Request', 'Batch size exceeds maximum limit of 100 operations');
    }

    // Enforce 1MB max payload limit per note (ciphertext or envelope)
    for (const op of rawOps) {
      if (op.ciphertext) {
        const byteLen = Buffer.from(op.ciphertext, 'base64').byteLength;
        if (byteLen > 1024 * 1024) {
          throw new SyncError(413, 'Payload Too Large', 'Note ciphertext exceeds maximum size limit of 1MB');
        }
      }
      if (op.envelope) {
        const byteLen = Buffer.from(op.envelope, 'base64url').byteLength;
        if (byteLen > 1024 * 1024) {
          throw new SyncError(413, 'Payload Too Large', 'Note envelope exceeds maximum size limit of 1MB');
        }
      }
    }

    return await prisma.$transaction(async (tx) => {
      // 1. Ensure user_sync record exists for the user (defensive initialization)
      await tx.$executeRaw`
        INSERT INTO "user_sync" ("user_id", "last_seq")
        VALUES (${userId}::uuid, 0)
        ON CONFLICT ("user_id") DO NOTHING
      `;

      // 2. Acquire exclusive row lock on user_sync for this user.
      // This serializes concurrent push batches from the same user.
      await tx.$queryRaw`
        SELECT "last_seq" FROM "user_sync"
        WHERE "user_id" = ${userId}::uuid
        FOR UPDATE
      `;

      const results: PushResultItem[] = [];

      for (const op of rawOps) {
        // Step A: Idempotency check via processed_ops
        const processedOp = await tx.processedOp.findUnique({
          where: { opId: op.op_id },
        });

        if (processedOp) {
          // Op was already processed. Return duplicate without advancing sequence or mutating note.
          const existingNote = await tx.note.findUnique({
            where: { id: op.note_id },
          });

          results.push({
            op_id: op.op_id,
            note_id: op.note_id,
            status: 'duplicate',
            result: 'duplicate',
            server_version: processedOp.serverVersion,
            server_seq: existingNote?.serverSeq ? Number(existingNote.serverSeq) : null,
          });
          continue;
        }

        // Step B: Query current note state with row-level lock
        const existingRows = await tx.$queryRaw<
          Array<{
            id: string;
            user_id: string;
            version: number;
            deleted: boolean;
            server_seq: bigint;
          }>
        >`
          SELECT "id", "user_id", "version", "deleted", "server_seq"
          FROM "notes"
          WHERE "id" = ${op.note_id}::uuid
          FOR UPDATE
        `;

        const existingNote = existingRows.length > 0 ? existingRows[0] : null;

        // Step C: Cross-tenant access authorization check
        if (existingNote && existingNote.user_id !== userId) {
          throw new SyncError(403, 'Forbidden', 'Cross-tenant access attempt on note');
        }

        // Step D: Version rule evaluation
        let isConflict = false;
        const serverVersion = existingNote?.version ?? 0;

        if (op.type === 'create') {
          // create requires base_version = 0, version = 1, and note must not exist
          if (op.base_version !== 0 || op.version !== 1 || existingNote !== null) {
            isConflict = true;
          }
        } else {
          // update / delete require note to exist, not already deleted,
          // base_version === stored version, and version === base_version + 1
          if (
            !existingNote ||
            existingNote.deleted ||
            existingNote.version !== op.base_version ||
            op.version !== op.base_version + 1
          ) {
            isConflict = true;
          }
        }

        if (isConflict) {
          results.push({
            op_id: op.op_id,
            note_id: op.note_id,
            status: 'conflict',
            result: 'conflict',
            server_version: serverVersion,
            server_seq: null,
          });
          continue;
        }

        // Step E: Apply mutation
        // 1. Monotonically increment user_sync.last_seq within this transaction
        const [syncRow] = await tx.$queryRaw<Array<{ last_seq: bigint }>>`
          UPDATE "user_sync"
          SET "last_seq" = "last_seq" + 1
          WHERE "user_id" = ${userId}::uuid
          RETURNING "last_seq"
        `;
        const nextSeq = syncRow.last_seq;

        // 2. Prepare envelope / ciphertext fields
        const isDeleted = op.type === 'delete';
        let envelopeBuf: Buffer | null = null;
        let formatVersion = op.format_version ?? 0;
        let keyIdBuf: Buffer | null = null;
        let checksumBuf: Buffer | null = null;
        let ciphertextBuf: Buffer | null = null;
        let nonceBuf: Buffer | null = null;

        if (!isDeleted) {
          if (op.envelope) {
            envelopeBuf = Buffer.from(op.envelope, 'base64url');
            formatVersion = op.format_version ?? 1;
            const computedChecksum = crypto.createHash('sha256').update(envelopeBuf).digest();

            if (op.checksum) {
              const expectedBuf =
                op.checksum.length === 64
                  ? Buffer.from(op.checksum, 'hex')
                  : Buffer.from(op.checksum, 'base64url');

              if (expectedBuf.byteLength !== 32 || !crypto.timingSafeEqual(computedChecksum, expectedBuf)) {
                throw new SyncError(400, 'Bad Request', 'Envelope checksum mismatch');
              }
              checksumBuf = expectedBuf;
            } else {
              checksumBuf = computedChecksum;
            }

            if (op.key_id) {
              keyIdBuf = Buffer.from(op.key_id, 'base64url');
            }
          } else if (op.ciphertext && op.nonce) {
            ciphertextBuf = Buffer.from(op.ciphertext, 'base64');
            nonceBuf = Buffer.from(op.nonce, 'base64');
            envelopeBuf = Buffer.concat([nonceBuf, ciphertextBuf]);
            formatVersion = 0;
            checksumBuf = crypto.createHash('sha256').update(envelopeBuf).digest();
          }
        }

        await tx.note.upsert({
          where: { id: op.note_id },
          create: {
            id: op.note_id,
            userId,
            ciphertext: ciphertextBuf,
            nonce: nonceBuf,
            envelope: envelopeBuf,
            formatVersion,
            keyId: keyIdBuf,
            checksum: checksumBuf,
            version: op.version,
            deleted: isDeleted,
            serverSeq: nextSeq,
          },
          update: {
            ciphertext: ciphertextBuf,
            nonce: nonceBuf,
            envelope: envelopeBuf,
            formatVersion,
            keyId: keyIdBuf,
            checksum: checksumBuf,
            version: op.version,
            deleted: isDeleted,
            serverSeq: nextSeq,
            updatedAt: new Date(),
          },
        });

        // 3. Record op in processed_ops for idempotency tracking
        await tx.processedOp.create({
          data: {
            opId: op.op_id,
            userId,
            result: 'applied',
            serverVersion: op.version,
          },
        });

        // 4. Record applied result
        results.push({
          op_id: op.op_id,
          note_id: op.note_id,
          status: 'applied',
          result: 'applied',
          server_version: op.version,
          server_seq: Number(nextSeq),
        });
      }

      return results;
    });
  }

  /**
   * Retrieves notes updated or deleted since the given cursor for the authenticated user.
   */
  async pull(userId: string, since: number = 0, limit: number = 50): Promise<PullResponse> {
    const sinceSeq = BigInt(Math.max(0, since));
    const pageSize = Math.min(Math.max(1, limit), 100);

    const notes = await prisma.note.findMany({
      where: {
        userId,
        serverSeq: { gt: sinceSeq },
      },
      orderBy: {
        serverSeq: 'asc',
      },
      take: pageSize + 1,
    });

    const hasMore = notes.length > pageSize;
    const pageNotes = hasMore ? notes.slice(0, pageSize) : notes;

    const changes: PullChangeItem[] = pageNotes.map((note) => {
      let envelopeStr: string | null = null;
      if (note.envelope) {
        envelopeStr = Buffer.from(note.envelope).toString('base64url');
      } else if (note.nonce && note.ciphertext) {
        envelopeStr = Buffer.concat([Buffer.from(note.nonce), Buffer.from(note.ciphertext)]).toString('base64url');
      }

      return {
        id: note.id,
        ciphertext: note.ciphertext ? Buffer.from(note.ciphertext).toString('base64') : null,
        nonce: note.nonce ? Buffer.from(note.nonce).toString('base64') : null,
        envelope: envelopeStr,
        format_version: note.formatVersion,
        key_id: note.keyId ? Buffer.from(note.keyId).toString('base64url') : null,
        checksum: note.checksum ? Buffer.from(note.checksum).toString('base64url') : null,
        version: note.version,
        deleted: note.deleted,
        updated_at: note.updatedAt.getTime(),
        server_seq: Number(note.serverSeq),
      };
    });


    const nextCursor = changes.length > 0 ? changes[changes.length - 1].server_seq : Number(sinceSeq);

    return {
      changes,
      next_cursor: nextCursor,
      has_more: hasMore,
    };
  }
}

export const syncService = new SyncService();
