import { Pool } from 'pg';

import { env } from './env';
import { logger } from '../util/logger';

export const pool = new Pool({
  host: env.DB_HOST,
  port: env.DB_PORT,
  database: env.DB_NAME,
  user: env.DB_USER,
  password: env.DB_PASSWORD,
  max: 10,
  connectionTimeoutMillis: 5000,
  idleTimeoutMillis: 30000,
});

pool.on('error', (err) => {
  logger.error({ error: err }, 'PostgreSQL 커넥션 풀 에러');
});

export async function pingDatabase(): Promise<boolean> {
  try {
    await pool.query('SELECT 1');
    return true;
  } catch (error) {
    logger.error({ error }, 'DB ping 실패');
    return false;
  }
}

export async function closeDatabase(): Promise<void> {
  await pool.end();
  logger.info('PostgreSQL 커넥션 풀 종료');
}
