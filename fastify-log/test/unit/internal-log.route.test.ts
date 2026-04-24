import { describe, it, expect, vi, beforeEach } from 'vitest';
import Fastify, { FastifyInstance } from 'fastify';

const { mockGetRecentLogs } = vi.hoisted(() => ({
  mockGetRecentLogs: vi.fn(),
}));

vi.mock('../../src/service/action-log.service', () => ({
  actionLogService: {
    save: vi.fn(),
    getRecentLogs: mockGetRecentLogs,
  },
}));

vi.mock('../../src/config/database', () => ({
  pool: { query: vi.fn() },
}));

import { internalLogRoutes } from '../../src/route/internal-log.route';

const USER_UUID = '550e8400-e29b-41d4-a716-446655440000';

async function buildApp(): Promise<FastifyInstance> {
  const app = Fastify({ logger: false });
  await app.register(internalLogRoutes);
  return app;
}

describe('InternalLogRoutes — GET /internal/logs/actions', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  // ========== X-Internal-Service 헤더 가드 ==========

  describe('X-Internal-Service 헤더 가드', () => {
    it('헤더 누락 → 401', async () => {
      // given
      const app = await buildApp();

      // when
      const res = await app.inject({
        method: 'GET',
        url: `/internal/logs/actions?userId=${USER_UUID}&actionTypes=VIEW`,
      });

      // then
      expect(res.statusCode).toBe(401);
      expect(res.json()).toMatchObject({ statusCode: 401, error: 'Unauthorized' });
      expect(mockGetRecentLogs).not.toHaveBeenCalled();
    });

    it('allowlist 외 값 → 403', async () => {
      // given
      const app = await buildApp();

      // when
      const res = await app.inject({
        method: 'GET',
        url: `/internal/logs/actions?userId=${USER_UUID}&actionTypes=VIEW`,
        headers: { 'x-internal-service': 'attacker' },
      });

      // then
      expect(res.statusCode).toBe(403);
      expect(res.json()).toMatchObject({ statusCode: 403, error: 'Forbidden' });
      expect(mockGetRecentLogs).not.toHaveBeenCalled();
    });

    it('allowlist 값 (ai) → 통과', async () => {
      // given
      mockGetRecentLogs.mockResolvedValueOnce([]);
      const app = await buildApp();

      // when
      const res = await app.inject({
        method: 'GET',
        url: `/internal/logs/actions?userId=${USER_UUID}&actionTypes=VIEW`,
        headers: { 'x-internal-service': 'ai' },
      });

      // then
      expect(res.statusCode).toBe(200);
    });
  });

  // ========== 쿼리 파라미터 validation ==========

  describe('쿼리 파라미터 validation', () => {
    const ok = { 'x-internal-service': 'ai' };

    it('userId 미UUID → 400', async () => {
      const app = await buildApp();
      const res = await app.inject({
        method: 'GET',
        url: '/internal/logs/actions?userId=not-a-uuid&actionTypes=VIEW',
        headers: ok,
      });
      expect(res.statusCode).toBe(400);
      expect(mockGetRecentLogs).not.toHaveBeenCalled();
    });

    it('userId 누락 → 400', async () => {
      const app = await buildApp();
      const res = await app.inject({
        method: 'GET',
        url: '/internal/logs/actions?actionTypes=VIEW',
        headers: ok,
      });
      expect(res.statusCode).toBe(400);
    });

    it('actionTypes 누락 → 400', async () => {
      const app = await buildApp();
      const res = await app.inject({
        method: 'GET',
        url: `/internal/logs/actions?userId=${USER_UUID}`,
        headers: ok,
      });
      expect(res.statusCode).toBe(400);
    });

    it('days 상한 초과 (31) → 400', async () => {
      const app = await buildApp();
      const res = await app.inject({
        method: 'GET',
        url: `/internal/logs/actions?userId=${USER_UUID}&days=31&actionTypes=VIEW`,
        headers: ok,
      });
      expect(res.statusCode).toBe(400);
    });

    it('days 하한 미만 (0) → 400', async () => {
      const app = await buildApp();
      const res = await app.inject({
        method: 'GET',
        url: `/internal/logs/actions?userId=${USER_UUID}&days=0&actionTypes=VIEW`,
        headers: ok,
      });
      expect(res.statusCode).toBe(400);
    });

    it('days 누락 → 기본 7 사용', async () => {
      mockGetRecentLogs.mockResolvedValueOnce([]);
      const app = await buildApp();
      await app.inject({
        method: 'GET',
        url: `/internal/logs/actions?userId=${USER_UUID}&actionTypes=VIEW`,
        headers: ok,
      });
      expect(mockGetRecentLogs).toHaveBeenCalledWith(USER_UUID, 7, ['VIEW']);
    });

    it('actionTypes 무효 토큰 포함 (UNKNOWN) → 400', async () => {
      const app = await buildApp();
      const res = await app.inject({
        method: 'GET',
        url: `/internal/logs/actions?userId=${USER_UUID}&actionTypes=VIEW,UNKNOWN`,
        headers: ok,
      });
      expect(res.statusCode).toBe(400);
      expect(res.json().message).toContain('actionTypes');
      expect(mockGetRecentLogs).not.toHaveBeenCalled();
    });

    it('actionTypes CSV 공백 트림', async () => {
      mockGetRecentLogs.mockResolvedValueOnce([]);
      const app = await buildApp();
      await app.inject({
        method: 'GET',
        url: `/internal/logs/actions?userId=${USER_UUID}&actionTypes=VIEW,%20DETAIL_VIEW`,
        headers: ok,
      });
      expect(mockGetRecentLogs).toHaveBeenCalledWith(
        USER_UUID,
        7,
        ['VIEW', 'DETAIL_VIEW'],
      );
    });

    it('URL 인코딩된 actionTypes (VIEW%2CDETAIL_VIEW) → 자동 디코딩', async () => {
      mockGetRecentLogs.mockResolvedValueOnce([]);
      const app = await buildApp();
      await app.inject({
        method: 'GET',
        url: `/internal/logs/actions?userId=${USER_UUID}&actionTypes=VIEW%2CDETAIL_VIEW`,
        headers: ok,
      });
      expect(mockGetRecentLogs).toHaveBeenCalledWith(
        USER_UUID,
        7,
        ['VIEW', 'DETAIL_VIEW'],
      );
    });

    it('actionTypes가 comma 단독 → 400 (빈 토큰만)', async () => {
      const app = await buildApp();
      const res = await app.inject({
        method: 'GET',
        url: `/internal/logs/actions?userId=${USER_UUID}&actionTypes=%2C`,
        headers: ok,
      });
      expect(res.statusCode).toBe(400);
    });

    it('소문자 actionType → 400', async () => {
      const app = await buildApp();
      const res = await app.inject({
        method: 'GET',
        url: `/internal/logs/actions?userId=${USER_UUID}&actionTypes=view`,
        headers: ok,
      });
      expect(res.statusCode).toBe(400);
    });
  });

  // ========== 정상 응답 ==========

  describe('정상 응답', () => {
    const ok = { 'x-internal-service': 'ai' };

    it('로그 존재 → 200 + logs 배열 + service 호출 인자 확인', async () => {
      // given
      mockGetRecentLogs.mockResolvedValueOnce([
        {
          eventId: '11111111-1111-4111-8111-111111111111',
          actionType: 'VIEW',
          dwellTimeSeconds: null,
        },
        {
          eventId: '22222222-2222-4222-8222-222222222222',
          actionType: 'DWELL_TIME',
          dwellTimeSeconds: 45,
        },
      ]);
      const app = await buildApp();

      // when
      const res = await app.inject({
        method: 'GET',
        url: `/internal/logs/actions?userId=${USER_UUID}&days=7&actionTypes=VIEW,DWELL_TIME`,
        headers: ok,
      });

      // then
      expect(res.statusCode).toBe(200);
      expect(res.json()).toEqual({
        userId: USER_UUID,
        logs: [
          {
            eventId: '11111111-1111-4111-8111-111111111111',
            actionType: 'VIEW',
            dwellTimeSeconds: null,
          },
          {
            eventId: '22222222-2222-4222-8222-222222222222',
            actionType: 'DWELL_TIME',
            dwellTimeSeconds: 45,
          },
        ],
      });
      expect(mockGetRecentLogs).toHaveBeenCalledWith(
        USER_UUID,
        7,
        ['VIEW', 'DWELL_TIME'],
      );
    });

    it('로그 없음 → 200 + 빈 배열 (404 아님)', async () => {
      mockGetRecentLogs.mockResolvedValueOnce([]);
      const app = await buildApp();
      const res = await app.inject({
        method: 'GET',
        url: `/internal/logs/actions?userId=${USER_UUID}&actionTypes=VIEW`,
        headers: ok,
      });
      expect(res.statusCode).toBe(200);
      expect(res.json()).toEqual({ userId: USER_UUID, logs: [] });
    });
  });
});
