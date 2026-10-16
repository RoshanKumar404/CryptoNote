import { FastifyInstance, FastifyPluginOptions } from 'fastify';
import { ZodError } from 'zod';
import { registerSchema, loginSchema, refreshSchema } from '../schemas/auth.schema';
import { authService, AuthError } from '../services/auth.service';
import { authenticate } from '../middleware/auth.middleware';
import { prisma } from '../db/prisma';

export async function authRoutes(fastify: FastifyInstance, _opts: FastifyPluginOptions) {
  // Error handling hook for auth routes
  fastify.setErrorHandler((error, _request, reply) => {
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

    if (error instanceof AuthError) {
      return reply.status(error.statusCode).send({
        statusCode: error.statusCode,
        error: error.statusCode === 409 ? 'Conflict' : 'Unauthorized',
        message: error.message,
      });
    }

    fastify.log.error(error);
    return reply.status(500).send({
      statusCode: 500,
      error: 'Internal Server Error',
      message: 'An unexpected error occurred',
    });
  });

  /**
   * POST /auth/register
   */
  fastify.post('/register', async (request, reply) => {
    const input = registerSchema.parse(request.body);
    const result = await authService.register(input);
    return reply.status(201).send(result);
  });

  /**
   * POST /auth/login
   */
  fastify.post('/login', async (request, reply) => {
    const input = loginSchema.parse(request.body);
    const result = await authService.login(input);
    return reply.status(200).send(result);
  });

  /**
   * POST /auth/refresh
   */
  fastify.post('/refresh', async (request, reply) => {
    const input = refreshSchema.parse(request.body);
    const result = await authService.refresh(input);
    return reply.status(200).send(result);
  });

  /**
   * GET /auth/me (Protected Route)
   */
  fastify.get('/me', { preHandler: [authenticate] }, async (request, reply) => {
    const user = await prisma.user.findUnique({
      where: { id: request.user!.userId },
      select: { id: true, email: true, createdAt: true },
    });

    if (!user) {
      return reply.status(404).send({
        statusCode: 404,
        error: 'Not Found',
        message: 'User account not found',
      });
    }

    return reply.status(200).send({
      user: {
        id: user.id,
        email: user.email,
        createdAt: user.createdAt.toISOString(),
      },
    });
  });
}
