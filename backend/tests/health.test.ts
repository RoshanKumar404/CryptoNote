import { describe, it, expect, afterAll } from '@jest/globals';
import { buildApp } from '../src/app';

describe('GET /health', () => {
  const app = buildApp();

  afterAll(async () => {
    await app.close();
  });

  it('should return 200 with service info', async () => {
    const response = await app.inject({
      method: 'GET',
      url: '/health',
    });

    expect(response.statusCode).toBe(200);
    const body = JSON.parse(response.body);
    expect(body.status).toBe('ok');
    expect(body.service).toBe('cryptonote-backend');
    expect(body.timestamp).toBeDefined();
    expect(body.uptime).toBeGreaterThanOrEqual(0);
  });
});
