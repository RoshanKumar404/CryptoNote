import { buildApp } from '../src/app';
import { FastifyInstance } from 'fastify';
import { prisma } from '../src/db/prisma';

describe('Keys API (NV-BE-1)', () => {
  let app: FastifyInstance;
  let accessToken: string;
  let userId: string;

  beforeAll(async () => {
    app = await buildApp();
    await app.ready();
  });

  afterAll(async () => {
    await app.close();
  });

  beforeEach(async () => {
    // Register a fresh test user
    const email = `keys-test-${Date.now()}@example.com`;
    const regRes = await app.inject({
      method: 'POST',
      url: '/auth/register',
      payload: {
        email,
        password: 'testPassword123!',
      },
    });

    expect(regRes.statusCode).toBe(201);
    const body = JSON.parse(regRes.payload);
    accessToken = body.accessToken;
    userId = body.user.id;
  });

  afterEach(async () => {
    if (userId) {
      await prisma.wrappedKeys.deleteMany({ where: { userId } });
      await prisma.user.deleteMany({ where: { id: userId } });
    }
  });

  test('GET /keys returns exists: false when no keys exist', async () => {
    const res = await app.inject({
      method: 'GET',
      url: '/keys',
      headers: {
        authorization: `Bearer ${accessToken}`,
      },
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.payload);
    expect(body.exists).toBe(false);
  });

  test('PUT /keys creates key_version 1 initially', async () => {
    const res = await app.inject({
      method: 'PUT',
      url: '/keys',
      headers: {
        authorization: `Bearer ${accessToken}`,
      },
      payload: {
        pass_wrapped_mk: Buffer.from('wrapped-mk-v1').toString('base64'),
        pass_salt: Buffer.from('salt-v1').toString('base64'),
        kdf_params: { iterations: 3, memory: 65536 },
        recovery_wrapped_mk: Buffer.from('rec-wrapped-v1').toString('base64'),
        kcv: Buffer.from('kcv-v1').toString('base64'),
      },
    });

    expect(res.statusCode).toBe(201);
    const body = JSON.parse(res.payload);
    expect(body.key_version).toBe(1);
    expect(body.status).toBe('created');

    // Verify GET /keys returns the newly created version
    const getRes = await app.inject({
      method: 'GET',
      url: '/keys',
      headers: {
        authorization: `Bearer ${accessToken}`,
      },
    });

    expect(getRes.statusCode).toBe(200);
    const getBody = JSON.parse(getRes.payload);
    expect(getBody.exists).toBe(true);
    expect(getBody.latest.key_version).toBe(1);
    expect(getBody.older_key_versions).toEqual([]);
  });

  test('A second PUT /keys with no current version returns 409 and changes nothing', async () => {
    // 1. Initial create
    await app.inject({
      method: 'PUT',
      url: '/keys',
      headers: {
        authorization: `Bearer ${accessToken}`,
      },
      payload: {
        pass_wrapped_mk: Buffer.from('wrapped-mk-v1').toString('base64'),
        pass_salt: Buffer.from('salt-v1').toString('base64'),
        kdf_params: { iterations: 3, memory: 65536 },
        recovery_wrapped_mk: Buffer.from('rec-wrapped-v1').toString('base64'),
      },
    });

    // 2. Second PUT without current_key_version
    const conflictRes = await app.inject({
      method: 'PUT',
      url: '/keys',
      headers: {
        authorization: `Bearer ${accessToken}`,
      },
      payload: {
        pass_wrapped_mk: Buffer.from('wrapped-mk-v2').toString('base64'),
        pass_salt: Buffer.from('salt-v2').toString('base64'),
        kdf_params: { iterations: 3, memory: 65536 },
        recovery_wrapped_mk: Buffer.from('rec-wrapped-v2').toString('base64'),
      },
    });

    expect(conflictRes.statusCode).toBe(409);

    // Verify no new row was created
    const count = await prisma.wrappedKeys.count({ where: { userId } });
    expect(count).toBe(1);
  });

  test('A passphrase change creates key_version + 1 and keeps the old row', async () => {
    // 1. Initial create (v1)
    await app.inject({
      method: 'PUT',
      url: '/keys',
      headers: {
        authorization: `Bearer ${accessToken}`,
      },
      payload: {
        pass_wrapped_mk: Buffer.from('wrapped-mk-v1').toString('base64'),
        pass_salt: Buffer.from('salt-v1').toString('base64'),
        kdf_params: { iterations: 3, memory: 65536 },
        recovery_wrapped_mk: Buffer.from('rec-wrapped-v1').toString('base64'),
      },
    });

    // 2. Passphrase change sending current_key_version = 1
    const rotateRes = await app.inject({
      method: 'PUT',
      url: '/keys',
      headers: {
        authorization: `Bearer ${accessToken}`,
      },
      payload: {
        pass_wrapped_mk: Buffer.from('wrapped-mk-v2').toString('base64'),
        pass_salt: Buffer.from('salt-v2').toString('base64'),
        kdf_params: { iterations: 3, memory: 65536 },
        recovery_wrapped_mk: Buffer.from('rec-wrapped-v2').toString('base64'),
        current_key_version: 1,
      },
    });

    expect(rotateRes.statusCode).toBe(200);
    const rotateBody = JSON.parse(rotateRes.payload);
    expect(rotateBody.key_version).toBe(2);

    // Verify both rows exist in DB
    const allKeys = await prisma.wrappedKeys.findMany({
      where: { userId },
      orderBy: { keyVersion: 'asc' },
    });
    expect(allKeys.length).toBe(2);
    expect(allKeys[0].keyVersion).toBe(1);
    expect(allKeys[1].keyVersion).toBe(2);

    // Verify GET /keys returns latest v2 and older [1]
    const getRes = await app.inject({
      method: 'GET',
      url: '/keys',
      headers: {
        authorization: `Bearer ${accessToken}`,
      },
    });

    const getBody = JSON.parse(getRes.payload);
    expect(getBody.latest.key_version).toBe(2);
    expect(getBody.older_key_versions).toEqual([1]);
  });
});
