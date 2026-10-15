import { buildApp } from './app';
import { env } from './config/env';
import { prisma } from './db/prisma';

const app = buildApp();

async function start() {
  try {
    await app.listen({ port: env.PORT, host: env.HOST });
    app.log.info(`Server running at http://${env.HOST}:${env.PORT}`);
  } catch (err) {
    app.log.error(err);
    await prisma.$disconnect();
    process.exit(1);
  }
}

// Graceful shutdown handling
const signals: NodeJS.Signals[] = ['SIGINT', 'SIGTERM'];
for (const signal of signals) {
  process.on(signal, async () => {
    app.log.info(`Received ${signal}, shutting down gracefully...`);
    try {
      await app.close();
      await prisma.$disconnect();
      app.log.info('Graceful shutdown completed.');
      process.exit(0);
    } catch (err) {
      app.log.error(err, 'Error during shutdown');
      process.exit(1);
    }
  });
}

start();
