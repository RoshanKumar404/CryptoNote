import { FastifyInstance } from 'fastify';
import { healthRoutes } from './health.routes';
import { authRoutes } from './auth.routes';
import { syncRoutes } from './sync.routes';

export async function registerRoutes(app: FastifyInstance) {
  // Global health check
  await app.register(healthRoutes);

  // Auth routes (direct /auth prefix per PRD B3)
  await app.register(authRoutes, { prefix: '/auth' });

  // Sync routes (direct /sync prefix per Ticket BE-3)
  await app.register(syncRoutes, { prefix: '/sync' });

  // API v1 routes mounted under /api/v1
  await app.register(async (apiV1) => {
    await apiV1.register(authRoutes, { prefix: '/auth' });
    await apiV1.register(syncRoutes, { prefix: '/sync' });
    // Keys routes (BE-7)
    // Device & Account routes (BE-6)
  }, { prefix: '/api/v1' });
}
