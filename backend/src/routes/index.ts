import { FastifyInstance } from 'fastify';
import { healthRoutes } from './health.routes';
import { authRoutes } from './auth.routes';

export async function registerRoutes(app: FastifyInstance) {
  // Global health check
  await app.register(healthRoutes);

  // Auth routes (direct /auth prefix per PRD B3)
  await app.register(authRoutes, { prefix: '/auth' });

  // API v1 routes mounted under /api/v1
  await app.register(async (apiV1) => {
    await apiV1.register(authRoutes, { prefix: '/auth' });
    // Sync routes (BE-3, BE-4)
    // Keys routes (BE-7)
    // Device & Account routes (BE-6)
  }, { prefix: '/api/v1' });
}
