import Fastify from 'fastify';

import { env } from './config/env';
import { closeDatabase } from './config/database';
import { disconnectConsumer } from './config/kafka';
import { startActionLogConsumer } from './consumer/action-log.consumer';
import { healthRoutes } from './route/health.route';
import { logger } from './util/logger';

async function main(): Promise<void> {
  const app = Fastify({ logger: false });

  // 라우트 등록
  await app.register(healthRoutes);

  // Kafka consumer 시작
  try {
    await startActionLogConsumer();
    logger.info('Kafka action.log consumer 시작 완료');
  } catch (error) {
    logger.error({ error }, 'Kafka consumer 시작 실패');
    process.exit(1);
  }

  // HTTP 서버 시작
  try {
    await app.listen({ port: env.PORT, host: '0.0.0.0' });
    logger.info({ port: env.PORT }, 'devticket-log 서버 시작');
  } catch (error) {
    logger.error({ error }, 'HTTP 서버 시작 실패');
    process.exit(1);
  }

  // Graceful shutdown
  const shutdown = async (signal: string): Promise<void> => {
    logger.info({ signal }, '종료 시그널 수신 — graceful shutdown 시작');

    try {
      await app.close();
      await disconnectConsumer();
      await closeDatabase();
      logger.info('graceful shutdown 완료');
      process.exit(0);
    } catch (error) {
      logger.error({ error }, 'graceful shutdown 중 에러');
      process.exit(1);
    }
  };

  process.on('SIGTERM', () => shutdown('SIGTERM'));
  process.on('SIGINT', () => shutdown('SIGINT'));
}

main();