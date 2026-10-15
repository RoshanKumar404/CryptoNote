import { FastifyInstance } from 'fastify';
import { healthRoutes } from './health.routes';

export async function registerRoutes(app: FastifyInstance) {
  // Global health check
  await app.register(healthRoutes);

  // API v1 routes will be mounted under /api/v1
  await app.register(async (apiV1) => {
    // Auth routes (BE-2)
    // Sync routes (BE-3, BE-4)
    // Keys routes (BE-7)
    // Device & Account routes (BE-6)
  }, { prefix: '/api/v1' });
}
