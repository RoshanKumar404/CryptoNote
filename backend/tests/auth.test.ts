import { describe, it, expect, beforeAll, afterAll } from '@jest/globals';
import { buildApp } from '../src/app';
import { prisma } from '../src/db/prisma';

describe('Auth Service (BE-2 Integration Tests)', () => {
  const app = buildApp();
  const testEmail = `test_${Date.now()}@example.com`;
  const testPassword = 'StrongPassword123!';

  let accessToken: string;
  let refreshToken: string;
  let userId: string;

  beforeAll(async () => {
    // Clean up any existing test user if present
    await prisma.user.deleteMany({
      where: { email: { contains: '@example.com' } },
    });
  });

  afterAll(async () => {
    // Clean up created test accounts
    await prisma.user.deleteMany({
      where: { email: { contains: '@example.com' } },
    });
    await app.close();
    await prisma.$disconnect();
  });

  describe('POST /auth/register', () => {
    it('should successfully register a new user and return tokens', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/auth/register',
        payload: {
          email: testEmail,
          password: testPassword,
        },
      });

      expect(response.statusCode).toBe(201);
      const body = JSON.parse(response.body);

      expect(body.accessToken).toBeDefined();
      expect(body.refreshToken).toBeDefined();
      expect(body.user).toBeDefined();
      expect(body.user.email).toBe(testEmail.toLowerCase());
      expect(body.user.id).toBeDefined();

      accessToken = body.accessToken;
      refreshToken = body.refreshToken;
      userId = body.user.id;

      // Verify that user_sync table was atomically initialized with last_seq = 0
      const userSync = await prisma.userSync.findUnique({
        where: { userId },
      });
      expect(userSync).not.toBeNull();
      expect(userSync?.lastSeq).toBe(0n);
    });

    it('should reject registration with a duplicate email address (409 Conflict)', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/auth/register',
        payload: {
          email: testEmail,
          password: 'AnotherPassword123!',
        },
      });

      expect(response.statusCode).toBe(409);
      const body = JSON.parse(response.body);
      expect(body.error).toBe('Conflict');
    });

    it('should reject registration when password is less than 8 characters (400 Bad Request)', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/auth/register',
        payload: {
          email: 'shortpass@example.com',
          password: 'short',
        },
      });

      expect(response.statusCode).toBe(400);
      const body = JSON.parse(response.body);
      expect(body.error).toBe('Bad Request');
    });

    it('should reject registration with an invalid email address (400 Bad Request)', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/auth/register',
        payload: {
          email: 'not-an-email',
          password: 'ValidPassword123!',
        },
      });

      expect(response.statusCode).toBe(400);
    });
  });

  describe('POST /auth/login', () => {
    it('should authenticate with valid credentials and return new tokens', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/auth/login',
        payload: {
          email: testEmail,
          password: testPassword,
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);

      expect(body.accessToken).toBeDefined();
      expect(body.refreshToken).toBeDefined();
      expect(body.user.id).toBe(userId);
    });

    it('should reject login with wrong password (401 Unauthorized)', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/auth/login',
        payload: {
          email: testEmail,
          password: 'WrongPassword!',
        },
      });

      expect(response.statusCode).toBe(401);
      const body = JSON.parse(response.body);
      expect(body.message).toBe('Invalid email or password');
    });

    it('should reject login for a nonexistent user (401 Unauthorized)', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/auth/login',
        payload: {
          email: 'nonexistent@example.com',
          password: 'AnyPassword123!',
        },
      });

      expect(response.statusCode).toBe(401);
      const body = JSON.parse(response.body);
      expect(body.message).toBe('Invalid email or password');
    });
  });

  describe('POST /auth/refresh & Token Rotation', () => {
    let secondRefreshToken: string;

    it('should exchange a valid refresh token for a new token pair', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/auth/refresh',
        payload: {
          refreshToken,
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);

      expect(body.accessToken).toBeDefined();
      expect(body.refreshToken).toBeDefined();
      expect(body.refreshToken).not.toBe(refreshToken);

      secondRefreshToken = body.refreshToken;
    });

    it('should reject reuse of an already-used refresh token (token rotation enforcement)', async () => {
      // Re-using the initial refreshToken, which was revoked during previous exchange
      const response = await app.inject({
        method: 'POST',
        url: '/auth/refresh',
        payload: {
          refreshToken,
        },
      });

      expect(response.statusCode).toBe(401);
      const body = JSON.parse(response.body);
      expect(body.message).toBe('Invalid or expired refresh token');
    });

    it('should allow the newly rotated refresh token to be used once', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/auth/refresh',
        payload: {
          refreshToken: secondRefreshToken,
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.accessToken).toBeDefined();
    });

    it('should reject a fake/tampered refresh token (401 Unauthorized)', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/auth/refresh',
        payload: {
          refreshToken: 'fake_random_token_that_does_not_exist',
        },
      });

      expect(response.statusCode).toBe(401);
    });
  });

  describe('GET /auth/me (Protected Route)', () => {
    it('should return user profile when valid Bearer token is provided', async () => {
      const response = await app.inject({
        method: 'GET',
        url: '/auth/me',
        headers: {
          authorization: `Bearer ${accessToken}`,
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.user.id).toBe(userId);
      expect(body.user.email).toBe(testEmail.toLowerCase());
    });

    it('should reject request without Authorization header (401 Unauthorized)', async () => {
      const response = await app.inject({
        method: 'GET',
        url: '/auth/me',
      });

      expect(response.statusCode).toBe(401);
    });

    it('should reject request with an invalid/tampered token (401 Unauthorized)', async () => {
      const response = await app.inject({
        method: 'GET',
        url: '/auth/me',
        headers: {
          authorization: 'Bearer invalid.jwt.token',
        },
      });

      expect(response.statusCode).toBe(401);
    });
  });
});
