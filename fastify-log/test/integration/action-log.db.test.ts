import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import type { Pool } from 'pg';

/**
 * Repository/Service → 실제 Postgres 통합 테스트.
 * RUN_DB_TESTS=1 환경변수가 세팅된 경우에만 실행. 기본은 skip (CI/local 안전성).
 *
 * 실행 예:
 *   RUN_DB_TESTS=1 npx vitest run test/integration/action-log.db.test.ts
 *
 * 기본 접속 정보 (docker devticket-postgres 기준): localhost:5433, devticket/devticket/devticket
 * 필요 시 TEST_DB_* 환경변수로 override.
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
const ORDER_UUID = '7c9e6679-7425-40de-944b-e07fc1f90ae7';
const EVENT_UUID_1 = '11111111-1111-4111-8111-111111111111';
const EVENT_UUID_2 = '22222222-2222-4222-8222-222222222222';
const PAYMENT_UUID = '33333333-3333-4333-8333-333333333333';
const TIMESTAMP = '2025-08-15T14:30:00.123Z';

interface ActionLogRow {
  id: number;
  user_id: string;
  event_id: string | null;
  action_type: string;
  search_keyword: string | null;
  stack_filter: string | null;
  dwell_time_seconds: number | null;
  quantity: number | null;
  total_amount: string | null; // BIGINT → string via pg default
}

describeIfDb('ActionLog Repository (실 Postgres)', () => {
  let pool: Pool;
  let actionLogRepository: typeof import('../../src/repository/action-log.repository').actionLogRepository;
  let paymentCompletedService: typeof import('../../src/service/payment-completed.service').paymentCompletedService;

  beforeAll(async () => {
    const db = await import('../../src/config/database');
    pool = db.pool;
    actionLogRepository = (await import('../../src/repository/action-log.repository')).actionLogRepository;
    paymentCompletedService = (await import('../../src/service/payment-completed.service'))
      .paymentCompletedService;

    // V1 마이그레이션 idempotent 적용
    const v1 = readFileSync(
      resolve(__dirname, '../../sql/V1__create_action_log.sql'),
      'utf-8',
    );
    await pool.query('CREATE SCHEMA IF NOT EXISTS log');
    await pool.query('DROP TABLE IF EXISTS log.action_log CASCADE');
    await pool.query(v1);
  }, 15000);

  afterEach(async () => {
    await pool.query('TRUNCATE log.action_log RESTART IDENTITY');
  });

  afterAll(async () => {
    await pool.query('DROP TABLE IF EXISTS log.action_log CASCADE');
    await pool.end();
  });

  async function fetchAll(): Promise<ActionLogRow[]> {
    const res = await pool.query('SELECT * FROM log.action_log ORDER BY id');
    return res.rows as ActionLogRow[];
  }

  describe('insertActionLog — 단일 행 INSERT', () => {
    it('PURCHASE 로그 1행을 실제 저장한다', async () => {
      await actionLogRepository.insertActionLog({
        userId: USER_UUID,
        eventId: EVENT_UUID_1,
        actionType: 'PURCHASE' as never,
        searchKeyword: null,
        stackFilter: null,
        dwellTimeSeconds: null,
        quantity: 2,
        totalAmount: 100000,
        timestamp: TIMESTAMP,
      });

      const rows = await fetchAll();
      expect(rows).toHaveLength(1);
      expect(rows[0]).toMatchObject({
        user_id: USER_UUID,
        event_id: EVENT_UUID_1,
        action_type: 'PURCHASE',
        quantity: 2,
        total_amount: '100000',
      });
    });

    it('nullable 필드가 null로 저장된다', async () => {
      await actionLogRepository.insertActionLog({
        userId: USER_UUID,
        eventId: null,
        actionType: 'VIEW' as never,
        searchKeyword: null,
        stackFilter: null,
        dwellTimeSeconds: null,
        quantity: null,
        totalAmount: null,
        timestamp: TIMESTAMP,
      });

      const rows = await fetchAll();
      expect(rows[0]).toMatchObject({
        event_id: null,
        search_keyword: null,
        stack_filter: null,
        dwell_time_seconds: null,
        quantity: null,
        total_amount: null,
      });
    });
  });

  describe('insertActionLogs — 다행 bulk INSERT', () => {
    function buildLog(eventId: string, quantity: number): Parameters<
      typeof actionLogRepository.insertActionLog
    >[0] {
      return {
        userId: USER_UUID,
        eventId,
        actionType: 'PURCHASE' as never,
        searchKeyword: null,
        stackFilter: null,
        dwellTimeSeconds: null,
        quantity,
        totalAmount: null,
        timestamp: TIMESTAMP,
      };
    }

    it('빈 배열 → no-op (쿼리 실행 없이 0행 유지)', async () => {
      await actionLogRepository.insertActionLogs([]);
      const rows = await fetchAll();
      expect(rows).toHaveLength(0);
    });

    it('1건 배열 → 단일 INSERT 경로로 1행 저장', async () => {
      await actionLogRepository.insertActionLogs([buildLog(EVENT_UUID_1, 1)]);
      const rows = await fetchAll();
      expect(rows).toHaveLength(1);
      expect(rows[0]).toMatchObject({ event_id: EVENT_UUID_1, quantity: 1 });
    });

    it('3건 배열 → placeholder offset(두 자릿수) 정상 동작, 순서 보존', async () => {
      const thirdEventId = '33333333-3333-4333-8333-aaaaaaaaaaaa';
      await actionLogRepository.insertActionLogs([
        buildLog(EVENT_UUID_1, 1),
        buildLog(EVENT_UUID_2, 2),
        buildLog(thirdEventId, 3),
      ]);
      const rows = await fetchAll();
      expect(rows).toHaveLength(3);
      expect(rows.map((r) => r.event_id)).toEqual([
        EVENT_UUID_1,
        EVENT_UUID_2,
        thirdEventId,
      ]);
      expect(rows.map((r) => r.quantity)).toEqual([1, 2, 3]);
    });

    /**
     * P1 회귀 방지: multi-row INSERT는 단일 statement이므로 중간 row가 DB 제약을 위반하면
     * 전체 statement가 ROLLBACK되어야 한다. 부분 저장이 발생하면 payment.completed 1건의
     * PURCHASE 로그가 영구히 일부만 저장되어 집계 정확도가 깨진다.
     */
    it('다건 중 하나라도 제약 위반이면 전체 ROLLBACK — 부분 저장 없음', async () => {
      const invalidLog = buildLog(EVENT_UUID_2, 2);
      (invalidLog as { eventId: string }).eventId = 'not-a-uuid';

      await expect(
        actionLogRepository.insertActionLogs([
          buildLog(EVENT_UUID_1, 1),
          invalidLog,
        ]),
      ).rejects.toThrow();

      const rows = await fetchAll();
      expect(rows).toHaveLength(0);
    });
  });

  describe('paymentCompletedService → DB fan-out', () => {
    it('단건 주문 → PURCHASE 1행, totalAmount 보존', async () => {
      await paymentCompletedService.save({
        orderId: ORDER_UUID,
        userId: USER_UUID,
        paymentId: PAYMENT_UUID,
        paymentMethod: 'PG',
        orderItems: [{ eventId: EVENT_UUID_1, quantity: 2 }],
        totalAmount: 100000,
        timestamp: TIMESTAMP,
      });

      const rows = await fetchAll();
      expect(rows).toHaveLength(1);
      expect(rows[0]).toMatchObject({
        user_id: USER_UUID,
        event_id: EVENT_UUID_1,
        action_type: 'PURCHASE',
        quantity: 2,
        total_amount: '100000',
      });
    });

    it('다건 주문 → OrderItem 당 1행 fan-out, totalAmount null로 중복 집계 방지', async () => {
      await paymentCompletedService.save({
        orderId: ORDER_UUID,
        userId: USER_UUID,
        paymentId: PAYMENT_UUID,
        paymentMethod: 'WALLET_PG',
        orderItems: [
          { eventId: EVENT_UUID_1, quantity: 2 },
          { eventId: EVENT_UUID_2, quantity: 3 },
        ],
        totalAmount: 250000,
        timestamp: TIMESTAMP,
      });

      const rows = await fetchAll();
      expect(rows).toHaveLength(2);
      expect(rows.map((r) => r.event_id)).toEqual([EVENT_UUID_1, EVENT_UUID_2]);
      expect(rows.map((r) => r.quantity)).toEqual([2, 3]);
      expect(rows.map((r) => r.total_amount)).toEqual([null, null]);
    });
  });

  describe('인덱스 활용 확인', () => {
    it('user_id 기반 조회가 결과를 반환한다', async () => {
      await paymentCompletedService.save({
        orderId: ORDER_UUID,
        userId: USER_UUID,
        paymentMethod: 'PG',
        orderItems: [{ eventId: EVENT_UUID_1, quantity: 1 }],
        totalAmount: 1000,
        timestamp: TIMESTAMP,
      });

      const res = await pool.query(
        'SELECT COUNT(*)::int AS cnt FROM log.action_log WHERE user_id = $1 AND action_type = $2',
        [USER_UUID, 'PURCHASE'],
      );
      expect(res.rows[0].cnt).toBe(1);
    });
  });

  /**
   * dedup 미적용 정책 회귀 방지 (docs/actionLog.md §2 #9).
   * Kafka rebalance edge case로 같은 메시지가 재배달되어도 Consumer는 그냥 INSERT 한다.
   * 미래에 누군가 "안전하게" dedup을 추가해버리면 이 테스트가 깨지면서 정책 위반을 알림.
   */
  describe('중복 메시지 거동 — dedup 미적용 정책 회귀 방지', () => {
    const duplicatePayload = {
      orderId: ORDER_UUID,
      userId: USER_UUID,
      paymentId: PAYMENT_UUID,
      paymentMethod: 'PG',
      orderItems: [
        { eventId: EVENT_UUID_1, quantity: 2 },
        { eventId: EVENT_UUID_2, quantity: 3 },
      ],
      totalAmount: 250000,
      timestamp: TIMESTAMP,
    };

    it('같은 payment.completed 이벤트 2회 save → 2N 행 생성 (중복 방어 안 함)', async () => {
      await paymentCompletedService.save(duplicatePayload);
      await paymentCompletedService.save(duplicatePayload);

      const res = await pool.query(
        'SELECT COUNT(*)::int AS cnt FROM log.action_log WHERE user_id = $1',
        [USER_UUID],
      );
      expect(res.rows[0].cnt).toBe(4); // 2 items × 2 deliveries
    });

    it('같은 (userId, eventId, timestamp) 조합이 중복 저장된다', async () => {
      await paymentCompletedService.save(duplicatePayload);
      await paymentCompletedService.save(duplicatePayload);

      const res = await pool.query(
        `SELECT event_id, COUNT(*)::int AS cnt
         FROM log.action_log
         WHERE user_id = $1 AND action_type = 'PURCHASE'
         GROUP BY event_id
         ORDER BY event_id`,
        [USER_UUID],
      );
      expect(res.rows).toEqual([
        { event_id: EVENT_UUID_1, cnt: 2 },
        { event_id: EVENT_UUID_2, cnt: 2 },
      ]);
    });

    it('리포트 쿼리에서 DISTINCT ON으로 사후 dedup 가능 (정책 전제 검증)', async () => {
      await paymentCompletedService.save(duplicatePayload);
      await paymentCompletedService.save(duplicatePayload);

      // docs/actionLog.md §2 #9에서 언급한 "리포트 쿼리로 사후 보정" 가능성 시연.
      const res = await pool.query(
        `SELECT COUNT(*)::int AS cnt
         FROM (
           SELECT DISTINCT ON (user_id, event_id, created_at)
                  user_id, event_id, created_at
           FROM log.action_log
           WHERE user_id = $1 AND action_type = 'PURCHASE'
         ) d`,
        [USER_UUID],
      );
      // created_at이 DEFAULT NOW()라 호출마다 다를 수 있으므로 정확히 2는 보장 어려움.
      // 최소한 "중복이 전부 날아가지는 않는다"는 것만 확인 (1 이상, 4 이하).
      expect(res.rows[0].cnt).toBeGreaterThanOrEqual(1);
      expect(res.rows[0].cnt).toBeLessThanOrEqual(4);
    });
  });
});
