import fastify, { FastifyInstance, FastifyServerOptions } from 'fastify';
import cors from '@fastify/cors';
import helmet from '@fastify/helmet';
import { registerRoutes } from './routes';

export function buildApp(opts: FastifyServerOptions = {}): FastifyInstance {
  const app = fastify({
    bodyLimit: 2 * 1024 * 1024, // 2MB limit (accommodates 1MB encrypted note + base64 overhead)
    logger: {
      level: process.env.NODE_ENV === 'test' ? 'silent' : 'info',
    },
    ...opts,
  });

  // Security headers
  app.register(helmet, {
    contentSecurityPolicy: false,
  });

  // CORS configuration
  app.register(cors, {
    origin: true,
    credentials: true,
    methods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'],
    allowedHeaders: ['Content-Type', 'Authorization', 'Accept'],
  });

  // Register all routes
  app.register(registerRoutes);

  return app;
}
