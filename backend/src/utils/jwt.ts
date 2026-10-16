import jwt, { SignOptions } from 'jsonwebtoken';
import { env } from '../config/env';
import { TokenPayload } from '../types/auth.types';

export function signAccessToken(payload: TokenPayload): string {
  const options: SignOptions = {
    expiresIn: env.JWT_ACCESS_EXPIRATION as any,
  };
  return jwt.sign(payload, env.JWT_SECRET, options);
}

export function verifyAccessToken(token: string): TokenPayload {
  const decoded = jwt.verify(token, env.JWT_SECRET);
  return decoded as TokenPayload;
}

export function getRefreshTokenExpiryDate(): Date {
  // Default 7 days from now
  const now = new Date();
  now.setDate(now.getDate() + 7);
  return now;
}
