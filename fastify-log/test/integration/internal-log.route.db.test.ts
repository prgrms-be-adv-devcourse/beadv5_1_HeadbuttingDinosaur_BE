import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import type { FastifyInstance } from 'fastify';
import type { Pool } from 'pg';

/**
 * Internal log route → 실제 Postgres 통합 테스트.
 * RUN_DB_TESTS=1 환경변수가 세팅된 경우에만 실행. 기본은 skip.
 *
 * 실행 예:
 *   RUN_DB_TESTS=1 npx vitest run test/integration/internal-log.route.db.test.ts
 *
 * 접속 정보 (docker devticket-postgres): localhost:5433, devticket/devticket/devticket
 */

const runDbTests = process.env.RUN_DB_TESTS === '1';

if (runDbTests) {
  process.env.DB_HOST ??= process.env.TEST_DB_HOST ?? 'localhost';
  process.env.DB_PORT ??= process.env.TEST_DB_PORT ?? '5433';
  process.env.DB_NAME ??= process.env.TEST_DB_NAME ?? 'devticket';
  process.env.DB_USER ??= process.env.TEST_DB_USER ?? 'devticket';
  process.env.DB_PASSWORD ??= process.env.TEST_DB_PASSWORD ?? 'devticket';
  process.env.DB_SCHEMA ??= 'log';
  process.env.KAFKA_BROKERS ??= 'localhost:9093';
}

const describeIfDb = runDbTests ? describe : describe.skip;

const USER_UUID = '550e8400-e29b-41d4-a716-446655440000';
const OTHER_USER_UUID = '660e8400-e29b-41d4-a716-446655440000';
const EVENT_A = '11111111-1111-4111-8111-111111111111';
const EVENT_B = '22222222-2222-4222-8222-222222222222';

const AI_HEADER = { 'x-internal-service': 'ai' };

describeIfDb('InternalLogRoutes (실 Postgres)', () => {
  let pool: Pool;
  let app: FastifyInstance;

  beforeAll(async () => {
    const db = await import('../../src/config/database');
    pool = db.pool;

    // V1 스키마 idempotent 적용
    const v1 = readFileSync(
      resolve(__dirname, '../../sql/V1__create_action_log.sql'),
      'utf-8',
    );
    await pool.query('CREATE SCHEMA IF NOT EXISTS log');
    await pool.query('DROP TABLE IF EXISTS log.action_log CASCADE');
    await pool.query(v1);

    const { internalLogRoutes } = await import('../../src/route/internal-log.route');
    const Fastify = (await import('fastify')).default;
    app = Fastify({ logger: false });
    await app.register(internalLogRoutes);
    await app.ready();
  }, 20000);

  afterEach(async () => {
    await pool.query('TRUNCATE log.action_log RESTART IDENTITY');
  });

  afterAll(async () => {
    await app.close();
    await pool.query('DROP TABLE IF EXISTS log.action_log CASCADE');
    await pool.end();
  });

  interface SeedRow {
    userId: string;
    eventId: string | null;
    actionType: string;
    dwellTimeSeconds?: number | null;
    createdAt?: string;
  }

  async function seed(rows: SeedRow[]): Promise<void> {
    for (const r of rows) {
      await pool.query(
        `INSERT INTO log.action_log
           (user_id, event_id, action_type, dwell_time_seconds, created_at)
         VALUES ($1, $2, $3, $4, COALESCE($5::timestamptz, NOW()))`,
        [
          r.userId,
          r.eventId,
          r.actionType,
          r.dwellTimeSeconds ?? null,
          r.createdAt ?? null,
        ],
      );
    }
  }

  it('매칭되는 로그 반환', async () => {
    await seed([
      { userId: USER_UUID, eventId: EVENT_A, actionType: 'VIEW' },
      {
        userId: USER_UUID,
        eventId: EVENT_B,
        actionType: 'DWELL_TIME',
        dwellTimeSeconds: 30,
      },
    ]);

    const res = await app.inject({
      method: 'GET',
      url: `/internal/logs/actions?userId=${USER_UUID}&actionTypes=VIEW,DWELL_TIME`,
      headers: AI_HEADER,
    });

    expect(res.statusCode).toBe(200);
    const body = res.json();
    expect(body.userId).toBe(USER_UUID);
    expect(body.logs).toHaveLength(2);
  });

  it('다른 userId 로그 반환 안 함', async () => {
    await seed([{ userId: OTHER_USER_UUID, eventId: EVENT_A, actionType: 'VIEW' }]);

    const res = await app.inject({
      method: 'GET',
      url: `/internal/logs/actions?userId=${USER_UUID}&actionTypes=VIEW`,
      headers: AI_HEADER,
    });

    expect(res.statusCode).toBe(200);
    expect(res.json().logs).toEqual([]);
  });

  it('event_id=null row는 제외 (VIEW 중 목록 조회성)', async () => {
    await seed([
      { userId: USER_UUID, eventId: null, actionType: 'VIEW' },
      { userId: USER_UUID, eventId: EVENT_A, actionType: 'VIEW' },
    ]);

    const res = await app.inject({
      method: 'GET',
      url: `/internal/logs/actions?userId=${USER_UUID}&actionTypes=VIEW`,
      headers: AI_HEADER,
    });

    expect(res.statusCode).toBe(200);
    const body = res.json();
    expect(body.logs).toHaveLength(1);
    expect(body.logs[0].eventId).toBe(EVENT_A);
  });

  it('created_at DESC 정렬', async () => {
    await seed([
      {
        userId: USER_UUID,
        eventId: EVENT_A,
        actionType: 'VIEW',
        createdAt: '2026-01-01T00:00:00Z',
      },
      {
        userId: USER_UUID,
        eventId: EVENT_B,
        actionType: 'VIEW',
        createdAt: '2026-04-01T00:00:00Z',
      },
    ]);

    const res = await app.inject({
      method: 'GET',
      url: `/internal/logs/actions?userId=${USER_UUID}&actionTypes=VIEW`,
      headers: AI_HEADER,
    });

    const logs = res.json().logs;
    expect(logs[0].eventId).toBe(EVENT_B);
    expect(logs[1].eventId).toBe(EVENT_A);
  });

  it('days 범위 밖 (40일 전) 로그 제외', async () => {
    const fortyDaysAgo = new Date(
      Date.now() - 40 * 24 * 3600 * 1000,
    ).toISOString();
    await seed([
      {
        userId: USER_UUID,
        eventId: EVENT_A,
        actionType: 'VIEW',
        createdAt: fortyDaysAgo,
      },
      { userId: USER_UUID, eventId: EVENT_B, actionType: 'VIEW' },
    ]);

    const res = await app.inject({
      method: 'GET',
      url: `/internal/logs/actions?userId=${USER_UUID}&days=7&actionTypes=VIEW`,
      headers: AI_HEADER,
    });

    const logs = res.json().logs;
    expect(logs).toHaveLength(1);
    expect(logs[0].eventId).toBe(EVENT_B);
  });
});
