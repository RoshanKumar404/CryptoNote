import { z } from 'zod';

export const putKeysSchema = z.object({
  pass_wrapped_mk: z.string().min(1, 'pass_wrapped_mk is required'),
  pass_salt: z.string().min(1, 'pass_salt is required'),
  kdf_params: z.record(z.string(), z.unknown()),
  recovery_wrapped_mk: z.string().min(1, 'recovery_wrapped_mk is required'),
  kcv: z.string().optional(),
  current_key_version: z.number().int().positive().optional(),
});
