import { FastifyInstance, FastifyPluginOptions } from 'fastify';
import { ZodError } from 'zod';
import { pushRequestSchema, pullQuerySchema } from '../schemas/sync.schema';
import { syncService, SyncError } from '../services/sync.service';
import { authenticate } from '../middleware/auth.middleware';

export async function syncRoutes(fastify: FastifyInstance, _opts: FastifyPluginOptions) {
  // Enforce JWT authentication on all sync endpoints
  fastify.addHook('preHandler', authenticate);

  // Error handling hook for sync routes
  fastify.setErrorHandler((error: any, _request, reply) => {
    if (error instanceof ZodError) {
      return reply.status(400).send({
        statusCode: 400,
        error: 'Bad Request',
        message: 'Validation failed',
        details: error.issues.map((e) => ({
          path: e.path.join('.'),
          message: e.message,
        })),
      });
    }

    if (error instanceof SyncError) {
      return reply.status(error.statusCode).send({
        statusCode: error.statusCode,
        error: error.error,
        message: error.message,
      });
    }

    if (error.statusCode === 413) {
      return reply.status(413).send({
        statusCode: 413,
        error: 'Payload Too Large',
        message: error.message || 'Payload exceeds maximum limit',
      });
    }

    fastify.log.error(error);
    return reply.status(500).send({
      statusCode: 500,
      error: 'Internal Server Error',
      message: 'An unexpected error occurred during sync processing',
    });
  });

  /**
   * POST /sync/push
   * Accepts an array of operations or an object { ops: [...] }
   */
  fastify.post('/push', async (request, reply) => {
    const isArrayInput = Array.isArray(request.body);
    const parsed = pushRequestSchema.parse(request.body);
    const ops = Array.isArray(parsed) ? parsed : parsed.ops;

    const results = await syncService.push(request.user!.userId, ops);

    if (isArrayInput) {
      return reply.status(200).send(results);
    } else {
      return reply.status(200).send({ results });
    }
  });

  /**
   * GET /sync/pull?since={cursor}&limit={limit}
   */
  fastify.get('/pull', async (request, reply) => {
    const query = pullQuerySchema.parse(request.query);
    const result = await syncService.pull(request.user!.userId, query.since, query.limit);
    return reply.status(200).send(result);
  });
}
