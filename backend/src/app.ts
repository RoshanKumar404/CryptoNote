import fastify, { FastifyInstance, FastifyServerOptions } from 'fastify';
import cors from '@fastify/cors';
import helmet from '@fastify/helmet';
import { registerRoutes } from './routes';

// Ensure BigInt values serialize cleanly to Numbers in JSON responses
(BigInt.prototype as any).toJSON = function () {
  return Number(this);
};

export function buildApp(opts: FastifyServerOptions = {}): FastifyInstance {
  const app = fastify({
    bodyLimit: 10 * 1024 * 1024, // 10MB limit (accommodates 100-op batch pushes with 1MB note limit enforcement in route)
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
