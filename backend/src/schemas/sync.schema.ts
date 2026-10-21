import { z } from 'zod';

export const pushOperationTypeSchema = z.enum(['create', 'update', 'delete']);

export const pushOperationSchema = z
  .object({
    op_id: z.string().uuid({ message: 'op_id must be a valid UUID' }),
    note_id: z.string().uuid({ message: 'note_id must be a valid UUID' }),
    type: pushOperationTypeSchema,
    base_version: z.number().int().min(0, { message: 'base_version must be >= 0' }),
    version: z.number().int().min(1, { message: 'version must be >= 1' }),
    ciphertext: z.string().nullable().optional(),
    nonce: z.string().nullable().optional(),
    envelope: z.string().nullable().optional(),
    format_version: z.number().int().min(0).max(1, { message: 'Unsupported format_version. Supported versions: 0, 1' }).optional(),
    key_id: z.string().nullable().optional(),
    checksum: z.string().nullable().optional(),
  })
  .refine(
    (data) => {
      if (data.type === 'create' || data.type === 'update') {
        const hasLegacy =
          typeof data.ciphertext === 'string' &&
          data.ciphertext.length > 0 &&
          typeof data.nonce === 'string' &&
          data.nonce.length > 0;
        const hasEnvelope = typeof data.envelope === 'string' && data.envelope.length > 0;
        return hasLegacy || hasEnvelope;
      }
      return true;
    },
    {
      message: 'Either envelope or (ciphertext and nonce) are required for create and update operations',
      path: ['envelope'],
    }
  );

export const pushBatchObjectSchema = z.object({
  ops: z
    .array(pushOperationSchema)
    .min(1, 'Operations array must not be empty')
    .max(100, 'Batch size exceeds maximum limit of 100 operations'),
});

export const pushBatchArraySchema = z
  .array(pushOperationSchema)
  .min(1, 'Operations array must not be empty')
  .max(100, 'Batch size exceeds maximum limit of 100 operations');

export const pushRequestSchema = z.union([pushBatchArraySchema, pushBatchObjectSchema]);

export const pullQuerySchema = z.object({
  since: z.coerce.number().min(0, 'since must be >= 0').default(0),
  limit: z.coerce.number().int().min(1, 'limit must be >= 1').max(100, 'limit must be <= 100').default(50),
});

export type PushOperation = z.infer<typeof pushOperationSchema>;
export type PushRequestInput = z.infer<typeof pushRequestSchema>;
export type PullQueryInput = z.infer<typeof pullQuerySchema>;

export type OpResultStatus = 'applied' | 'conflict' | 'duplicate';

export interface PushResultItem {
  op_id: string;
  note_id: string;
  status: OpResultStatus;
  result: OpResultStatus;
  server_version: number;
  server_seq: number | null;
}

export interface PullChangeItem {
  id: string;
  ciphertext: string | null;
  nonce: string | null;
  envelope: string | null;
  format_version: number;
  key_id: string | null;
  checksum: string | null;
  version: number;
  deleted: boolean;
  updated_at: number;
  server_seq: number;
}

export interface PullResponse {
  changes: PullChangeItem[];
  next_cursor: number;
  has_more: boolean;
}

