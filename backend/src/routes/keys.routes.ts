import { FastifyInstance, FastifyPluginOptions } from 'fastify';
import { ZodError } from 'zod';
import { putKeysSchema } from '../schemas/keys.schema';
import { KeysService } from '../services/keys.service';
import { authenticate } from '../middleware/auth.middleware';

export async function keysRoutes(fastify: FastifyInstance, _opts: FastifyPluginOptions) {
  const keysService = new KeysService();

  // Enforce JWT authentication
  fastify.addHook('preHandler', authenticate);

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

    if (error.statusCode === 409) {
      return reply.status(409).send({
        statusCode: 409,
        error: 'Conflict',
        message: error.message,
      });
    }

    // Never log raw request body containing key blobs!
    fastify.log.error('Keys API Error: %s', error.message || 'Unknown error');
    return reply.status(error.statusCode || 500).send({
      statusCode: error.statusCode || 500,
      error: error.name || 'Internal Server Error',
      message: error.message || 'An error occurred during key management',
    });
  });

  /**
   * GET /keys
   * Returns latest wrapped key record + list of older versions.
   */
  fastify.get('/', async (request, reply) => {
    const userId = request.user!.userId;
    const result = await keysService.getKeys(userId);
    return reply.status(200).send(result);
  });

  /**
   * PUT /keys
   * Create-only / version-guarded key replacement.
   */
  fastify.put('/', async (request, reply) => {
    const userId = request.user!.userId;
    const validated = putKeysSchema.parse(request.body);
    const result = await keysService.putKeys(userId, validated);
    const status = result.status === 'created' ? 201 : 200;
    return reply.status(status).send(result);
  });
}
