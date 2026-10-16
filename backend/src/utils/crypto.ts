import { hash, verify, Algorithm } from '@node-rs/argon2';
import crypto from 'crypto';

/**
 * Hashes a login password using Argon2id with recommended parameters:
 * m=64MB (65536 KiB), t=3 iterations, p=1 lane.
 * Salt is cryptographically generated and embedded in the returned hash string.
 */
export async function hashPassword(password: string): Promise<string> {
  return hash(password, {
    algorithm: Algorithm.Argon2id,
    memoryCost: 65536,
    timeCost: 3,
    outputLen: 32,
    parallelism: 1,
  });
}

/**
 * Constant-time verification of an Argon2id password hash.
 */
export async function verifyPassword(hashed: string, plain: string): Promise<boolean> {
  try {
    return await verify(hashed, plain);
  } catch {
    return false;
  }
}

/**
 * Computes the SHA-256 hex digest of a token string before database storage.
 */
export function hashToken(token: string): string {
  return crypto.createHash('sha256').update(token).digest('hex');
}

/**
 * Generates a cryptographically secure random token (64 hex characters / 256 bits).
 */
export function generateSecureToken(): string {
  return crypto.randomBytes(32).toString('hex');
}
