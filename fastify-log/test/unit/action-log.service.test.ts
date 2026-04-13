import { describe, it, expect, vi, beforeEach } from 'vitest';

import { actionLogService } from '../../src/service/action-log.service';
import { actionLogRepository } from '../../src/repository/action-log.repository';

// repository mock
vi.mock('../../src/repository/action-log.repository', () => ({
  actionLogRepository: {
    insertActionLog: vi.fn().mockResolvedValue(undefined),
  },
}));

// database mock (repository에서 import하는 pool)
vi.mock('../../src/config/database', () => ({
  pool: { query: vi.fn() },
}));

const mockInsert = vi.mocked(actionLogRepository.insertActionLog);

describe('ActionLogService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  // ========== 정상 케이스 ==========

  describe('정상 저장', () => {
    it('DETAIL_VIEW 메시지를 저장한다', async () => {
      // given
      const message = {
        userId: '550e8400-e29b-41d4-a716-446655440000',
        eventId: '7c9e6679-7425-40de-944b-e07fc1f90ae7',
        actionType: 'DETAIL_VIEW',
        timestamp: '2025-08-15T14:30:00',
      };

      // when
      await actionLogService.save(message);

      // then
      expect(mockInsert).toHaveBeenCalledOnce();
      expect(mockInsert).toHaveBeenCalledWith(
        expect.objectContaining({
          userId: message.userId,
          eventId: message.eventId,
          actionType: 'DETAIL_VIEW',
        }),
      );
    });

    it('PURCHASE 메시지 — quantity, totalAmount 포함', async () => {
      // given
      const message = {
        userId: 'user-uuid',
        eventId: 'event-uuid',
        actionType: 'PURCHASE',
        quantity: 2,
        totalAmount: 100000,
        timestamp: '2025-08-15T14:30:00',
      };

      // when
      await actionLogService.save(message);

      // then
      expect(mockInsert).toHaveBeenCalledWith(
        expect.objectContaining({
          actionType: 'PURCHASE',
          quantity: 2,
          totalAmount: 100000,
        }),
      );
    });

    it('REFUND 메시지 — totalAmount 포함', async () => {
      // given
      const message = {
        userId: 'user-uuid',
        eventId: 'event-uuid',
        actionType: 'REFUND',
        totalAmount: 50000,
        timestamp: '2025-08-15T14:30:00',
      };

      // when
      await actionLogService.save(message);

      // then
      expect(mockInsert).toHaveBeenCalledWith(
        expect.objectContaining({
          actionType: 'REFUND',
          totalAmount: 50000,
        }),
      );
    });

    it('CART_ADD 메시지 — quantity 포함', async () => {
      // given
      const message = {
        userId: 'user-uuid',
        eventId: 'event-uuid',
        actionType: 'CART_ADD',
        quantity: 3,
        timestamp: '2025-08-15T14:30:00',
      };

      // when
      await actionLogService.save(message);

      // then
      expect(mockInsert).toHaveBeenCalledWith(
        expect.objectContaining({
          actionType: 'CART_ADD',
          quantity: 3,
        }),
      );
    });

    it('DWELL_TIME 메시지 — dwellTimeSeconds 포함', async () => {
      // given
      const message = {
        userId: 'user-uuid',
        eventId: 'event-uuid',
        actionType: 'DWELL_TIME',
        dwellTimeSeconds: 45,
        timestamp: '2025-08-15T14:30:00',
      };

      // when
      await actionLogService.save(message);

      // then
      expect(mockInsert).toHaveBeenCalledWith(
        expect.objectContaining({
          actionType: 'DWELL_TIME',
          dwellTimeSeconds: 45,
        }),
      );
    });

    it('VIEW 메시지 — eventId null 허용', async () => {
      // given
      const message = {
        userId: 'user-uuid',
        actionType: 'VIEW',
        searchKeyword: 'Spring Boot',
        stackFilter: 'BACKEND',
        timestamp: '2025-08-15T14:30:00',
      };

      // when
      await actionLogService.save(message);

      // then
      expect(mockInsert).toHaveBeenCalledWith(
        expect.objectContaining({
          actionType: 'VIEW',
          eventId: null,
          searchKeyword: 'Spring Boot',
          stackFilter: 'BACKEND',
        }),
      );
    });

    it('CART_REMOVE 메시지 — 최소 필드만', async () => {
      // given
      const message = {
        userId: 'user-uuid',
        eventId: 'event-uuid',
        actionType: 'CART_REMOVE',
        timestamp: '2025-08-15T14:30:00',
      };

      // when
      await actionLogService.save(message);

      // then
      expect(mockInsert).toHaveBeenCalledWith(
        expect.objectContaining({
          actionType: 'CART_REMOVE',
          quantity: null,
          totalAmount: null,
          dwellTimeSeconds: null,
          searchKeyword: null,
          stackFilter: null,
        }),
      );
    });
  });

  // ========== nullable 필드 처리 ==========

  describe('nullable 필드 처리', () => {
    it('선택 필드가 없으면 null로 변환한다', async () => {
      // given
      const message = {
        userId: 'user-uuid',
        eventId: 'event-uuid',
        actionType: 'DETAIL_VIEW',
        timestamp: '2025-08-15T14:30:00',
      };

      // when
      await actionLogService.save(message);

      // then
      expect(mockInsert).toHaveBeenCalledWith(
        expect.objectContaining({
          searchKeyword: null,
          stackFilter: null,
          dwellTimeSeconds: null,
          quantity: null,
          totalAmount: null,
        }),
      );
    });

    it('eventId가 명시적 null이면 null로 저장한다', async () => {
      // given
      const message = {
        userId: 'user-uuid',
        eventId: null,
        actionType: 'VIEW',
        timestamp: '2025-08-15T14:30:00',
      };

      // when
      await actionLogService.save(message);

      // then
      expect(mockInsert).toHaveBeenCalledWith(
        expect.objectContaining({ eventId: null }),
      );
    });
  });

  // ========== 검증 실패 ==========

  describe('검증 실패', () => {
    it('null 입력 → 에러', async () => {
      await expect(actionLogService.save(null)).rejects.toThrow('유효하지 않은 메시지 형식');
      expect(mockInsert).not.toHaveBeenCalled();
    });

    it('undefined 입력 → 에러', async () => {
      await expect(actionLogService.save(undefined)).rejects.toThrow('유효하지 않은 메시지 형식');
      expect(mockInsert).not.toHaveBeenCalled();
    });

    it('문자열 입력 → 에러', async () => {
      await expect(actionLogService.save('not an object')).rejects.toThrow();
      expect(mockInsert).not.toHaveBeenCalled();
    });

    it('userId 누락 → 에러', async () => {
      // given
      const message = {
        eventId: 'event-uuid',
        actionType: 'DETAIL_VIEW',
        timestamp: '2025-08-15T14:30:00',
      };

      // when & then
      await expect(actionLogService.save(message)).rejects.toThrow('userId');
      expect(mockInsert).not.toHaveBeenCalled();
    });

    it('actionType 누락 → 에러', async () => {
      // given
      const message = {
        userId: 'user-uuid',
        timestamp: '2025-08-15T14:30:00',
      };

      // when & then
      await expect(actionLogService.save(message)).rejects.toThrow('actionType');
      expect(mockInsert).not.toHaveBeenCalled();
    });

    it('유효하지 않은 actionType → 에러', async () => {
      // given
      const message = {
        userId: 'user-uuid',
        actionType: 'INVALID_TYPE',
        timestamp: '2025-08-15T14:30:00',
      };

      // when & then
      await expect(actionLogService.save(message)).rejects.toThrow('유효하지 않은 actionType');
      expect(mockInsert).not.toHaveBeenCalled();
    });

    it('소문자 actionType → 에러', async () => {
      // given
      const message = {
        userId: 'user-uuid',
        actionType: 'detail_view',
        timestamp: '2025-08-15T14:30:00',
      };

      // when & then
      await expect(actionLogService.save(message)).rejects.toThrow('유효하지 않은 actionType');
      expect(mockInsert).not.toHaveBeenCalled();
    });

    it('timestamp 누락 → 에러', async () => {
      // given
      const message = {
        userId: 'user-uuid',
        actionType: 'DETAIL_VIEW',
      };

      // when & then
      await expect(actionLogService.save(message)).rejects.toThrow('timestamp');
      expect(mockInsert).not.toHaveBeenCalled();
    });
  });

  // ========== repository 에러 전파 ==========

  describe('repository 에러 전파', () => {
    it('DB INSERT 실패 시 에러가 전파된다', async () => {
      // given
      mockInsert.mockRejectedValueOnce(new Error('DB connection failed'));
      const message = {
        userId: 'user-uuid',
        eventId: 'event-uuid',
        actionType: 'DETAIL_VIEW',
        timestamp: '2025-08-15T14:30:00',
      };

      // when & then
      await expect(actionLogService.save(message)).rejects.toThrow('DB connection failed');
    });
  });
});
