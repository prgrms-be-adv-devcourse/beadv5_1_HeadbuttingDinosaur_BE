import { FastifyInstance } from 'fastify';

import { pingDatabase } from '../config/database';

export async function healthRoutes(app: FastifyInstance): Promise<void> {
  app.get('/health', async (_request, reply) => {
    return reply.status(200).send({ status: 'ok' });
  });

  app.get('/ready', async (_request, reply) => {
    const dbReady = await pingDatabase();

    if (!dbReady) {
      return reply.status(503).send({ status: 'not ready', reason: 'database connection failed' });
    }

    return reply.status(200).send({ status: 'ready' });
  });
}