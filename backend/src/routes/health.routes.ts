import { FastifyInstance, FastifyPluginOptions } from 'fastify';
import { prisma } from '../db/prisma';

export async function healthRoutes(fastify: FastifyInstance, _opts: FastifyPluginOptions) {
  fastify.get('/health', async (_request, reply) => {
    let dbStatus = 'disconnected';

    try {
      // Simple 1-second timeout query to check DB liveness if configured
      await prisma.$queryRaw`SELECT 1`;
      dbStatus = 'connected';
    } catch {
      dbStatus = 'unavailable';
    }

    return reply.status(200).send({
      status: 'ok',
      service: 'cryptonote-backend',
      timestamp: new Date().toISOString(),
      uptime: process.uptime(),
      database: dbStatus,
    });
  });
}
