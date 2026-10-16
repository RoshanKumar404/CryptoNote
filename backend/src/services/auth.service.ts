import { prisma } from '../db/prisma';
import { RegisterInput, LoginInput, RefreshInput } from '../schemas/auth.schema';
import { AuthResponse, TokenPair } from '../types/auth.types';
import { hashPassword, verifyPassword, hashToken, generateSecureToken } from '../utils/crypto';
import { signAccessToken, getRefreshTokenExpiryDate } from '../utils/jwt';

export class AuthError extends Error {
  constructor(public statusCode: number, message: string) {
    super(message);
    this.name = 'AuthError';
  }
}

export class AuthService {
  /**
   * Registers a new user account with login credentials.
   * Initializes the commit-ordered cursor in user_sync with last_seq = 0.
   */
  async register(input: RegisterInput): Promise<AuthResponse> {
    const existing = await prisma.user.findUnique({
      where: { email: input.email },
    });

    if (existing) {
      throw new AuthError(409, 'An account with this email address already exists');
    }

    const passwordHash = await hashPassword(input.password);

    return prisma.$transaction(async (tx) => {
      const user = await tx.user.create({
        data: {
          email: input.email,
          passwordHash,
        },
      });

      // Initialize the commit-ordered sequence counter for BE-3 push/pull sync
      await tx.userSync.create({
        data: {
          userId: user.id,
          lastSeq: 0n,
        },
      });

      const accessToken = signAccessToken({ userId: user.id, email: user.email });
      const rawRefreshToken = generateSecureToken();
      const tokenHash = hashToken(rawRefreshToken);

      await tx.refreshToken.create({
        data: {
          userId: user.id,
          tokenHash,
          expiresAt: getRefreshTokenExpiryDate(),
        },
      });

      return {
        accessToken,
        refreshToken: rawRefreshToken,
        user: {
          id: user.id,
          email: user.email,
        },
      };
    });
  }

  /**
   * Authenticates user with email and login password.
   * Verifies Argon2id password hash using constant-time comparison.
   */
  async login(input: LoginInput): Promise<AuthResponse> {
    const user = await prisma.user.findUnique({
      where: { email: input.email },
    });

    if (!user) {
      throw new AuthError(401, 'Invalid email or password');
    }

    const valid = await verifyPassword(user.passwordHash, input.password);
    if (!valid) {
      throw new AuthError(401, 'Invalid email or password');
    }

    const accessToken = signAccessToken({ userId: user.id, email: user.email });
    const rawRefreshToken = generateSecureToken();
    const tokenHash = hashToken(rawRefreshToken);

    await prisma.refreshToken.create({
      data: {
        userId: user.id,
        tokenHash,
        expiresAt: getRefreshTokenExpiryDate(),
      },
    });

    return {
      accessToken,
      refreshToken: rawRefreshToken,
      user: {
        id: user.id,
        email: user.email,
      },
    };
  }

  /**
   * Performs rotating refresh token exchange.
   * Validates token hash, enforces single-use rotation, and issues a new pair.
   */
  async refresh(input: RefreshInput): Promise<TokenPair> {
    const tokenHash = hashToken(input.refreshToken);

    const storedToken = await prisma.refreshToken.findFirst({
      where: { tokenHash },
      include: { user: true },
    });

    if (!storedToken || storedToken.revoked || storedToken.expiresAt < new Date()) {
      throw new AuthError(401, 'Invalid or expired refresh token');
    }

    return prisma.$transaction(async (tx) => {
      // Token rotation: Revoke the old refresh token
      await tx.refreshToken.update({
        where: { id: storedToken.id },
        data: { revoked: true },
      });

      // Issue new token pair
      const newRawRefreshToken = generateSecureToken();
      const newTokenHash = hashToken(newRawRefreshToken);

      await tx.refreshToken.create({
        data: {
          userId: storedToken.userId,
          tokenHash: newTokenHash,
          expiresAt: getRefreshTokenExpiryDate(),
        },
      });

      const accessToken = signAccessToken({
        userId: storedToken.user.id,
        email: storedToken.user.email,
      });

      return {
        accessToken,
        refreshToken: newRawRefreshToken,
      };
    });
  }
}

export const authService = new AuthService();
