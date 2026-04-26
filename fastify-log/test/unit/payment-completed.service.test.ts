import { describe, it, expect, vi, beforeEach } from 'vitest';

import { paymentCompletedService } from '../../src/service/payment-completed.service';
import { actionLogRepository } from '../../src/repository/action-log.repository';

vi.mock('../../src/repository/action-log.repository', () => ({
  actionLogRepository: {
    insertActionLog: vi.fn().mockResolvedValue(undefined),
    insertActionLogs: vi.fn().mockResolvedValue(undefined),
  },
}));

vi.mock('../../src/config/database', () => ({
  pool: { query: vi.fn() },
}));

const mockInsert = vi.mocked(actionLogRepository.insertActionLogs);

const USER_UUID = '550e8400-e29b-41d4-a716-446655440000';
const ORDER_UUID = '7c9e6679-7425-40de-944b-e07fc1f90ae7';
const EVENT_UUID_1 = '11111111-1111-4111-8111-111111111111';
const EVENT_UUID_2 = '22222222-2222-4222-8222-222222222222';
const PAYMENT_UUID = '33333333-3333-4333-8333-333333333333';
const TIMESTAMP = '2025-08-15T14:30:00Z';

function baseEvent(overrides: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    orderId: ORDER_UUID,
    userId: USER_UUID,
    paymentId: PAYMENT_UUID,
    paymentMethod: 'PG',
    orderItems: [{ eventId: EVENT_UUID_1, quantity: 2 }],
    totalAmount: 100000,
    timestamp: TIMESTAMP,
    ...overrides,
  };
}

describe('PaymentCompletedService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('정상 fan-out', () => {
    it('단건 주문 → PURCHASE 레코드 1건, totalAmount 보존', async () => {
      await paymentCompletedService.save(baseEvent());

      expect(mockInsert).toHaveBeenCalledOnce();
      expect(mockInsert).toHaveBeenCalledWith([
        expect.objectContaining({
          userId: USER_UUID,
          eventId: EVENT_UUID_1,
          actionType: 'PURCHASE',
          quantity: 2,
          totalAmount: 100000,
          timestamp: TIMESTAMP,
        }),
      ]);
    });

    it('다건 주문 → 단일 호출에 OrderItem 당 1건 fan-out, totalAmount 중복 방지 위해 null', async () => {
      const event = baseEvent({
        orderItems: [
          { eventId: EVENT_UUID_1, quantity: 2 },
          { eventId: EVENT_UUID_2, quantity: 3 },
        ],
        totalAmount: 250000,
      });

      await paymentCompletedService.save(event);

      expect(mockInsert).toHaveBeenCalledOnce();
      expect(mockInsert).toHaveBeenCalledWith([
        expect.objectContaining({
          eventId: EVENT_UUID_1,
          quantity: 2,
          totalAmount: null,
        }),
        expect.objectContaining({
          eventId: EVENT_UUID_2,
          quantity: 3,
          totalAmount: null,
        }),
      ]);
    });

    it('PURCHASE 레코드의 unused 필드는 null로 채운다', async () => {
      await paymentCompletedService.save(baseEvent());

      expect(mockInsert).toHaveBeenCalledWith([
        expect.objectContaining({
          searchKeyword: null,
          stackFilter: null,
          dwellTimeSeconds: null,
        }),
      ]);
    });
  });

  describe('검증 실패', () => {
    it('null 입력 → 에러', async () => {
      await expect(paymentCompletedService.save(null)).rejects.toThrow('유효하지 않은 메시지');
      expect(mockInsert).not.toHaveBeenCalled();
    });

    it('userId 누락 → 에러', async () => {
      const event = baseEvent();
      delete event.userId;

      await expect(paymentCompletedService.save(event)).rejects.toThrow('userId');
      expect(mockInsert).not.toHaveBeenCalled();
    });

    it('userId가 UUID 형식이 아니면 에러', async () => {
      await expect(
        paymentCompletedService.save(baseEvent({ userId: 'not-a-uuid' })),
      ).rejects.toThrow('UUID');
      expect(mockInsert).not.toHaveBeenCalled();
    });

    it('orderItems 빈 배열 → 에러', async () => {
      await expect(
        paymentCompletedService.save(baseEvent({ orderItems: [] })),
      ).rejects.toThrow('orderItems');
      expect(mockInsert).not.toHaveBeenCalled();
    });

    it('orderItems 누락 → 에러', async () => {
      const event = baseEvent();
      delete event.orderItems;

      await expect(paymentCompletedService.save(event)).rejects.toThrow('orderItems');
      expect(mockInsert).not.toHaveBeenCalled();
    });

    it('OrderItem eventId가 UUID 형식이 아니면 에러', async () => {
      const event = baseEvent({
        orderItems: [{ eventId: 'invalid-uuid', quantity: 1 }],
      });

      await expect(paymentCompletedService.save(event)).rejects.toThrow('UUID');
      expect(mockInsert).not.toHaveBeenCalled();
    });

    it('OrderItem quantity가 0 이하면 에러', async () => {
      const event = baseEvent({
        orderItems: [{ eventId: EVENT_UUID_1, quantity: 0 }],
      });

      await expect(paymentCompletedService.save(event)).rejects.toThrow('quantity');
      expect(mockInsert).not.toHaveBeenCalled();
    });

    it('timestamp 누락 → 에러', async () => {
      const event = baseEvent();
      delete event.timestamp;

      await expect(paymentCompletedService.save(event)).rejects.toThrow('timestamp');
      expect(mockInsert).not.toHaveBeenCalled();
    });
  });

  describe('경계값', () => {
    it('totalAmount 0도 정상 처리 (무료 이벤트 케이스 대응)', async () => {
      await paymentCompletedService.save(baseEvent({ totalAmount: 0 }));

      expect(mockInsert).toHaveBeenCalledOnce();
      expect(mockInsert).toHaveBeenCalledWith([
        expect.objectContaining({ totalAmount: 0 }),
      ]);
    });

    it('totalAmount 음수 → 에러', async () => {
      await expect(
        paymentCompletedService.save(baseEvent({ totalAmount: -1 })),
      ).rejects.toThrow('totalAmount');
      expect(mockInsert).not.toHaveBeenCalled();
    });
  });

  describe('Outbox wrapper unwrap', () => {
    function buildOutboxEnvelope(innerEvent: Record<string, unknown>): Record<string, unknown> {
      return {
        messageId: '8b3e5a1c-4d2f-41a7-9c8e-6f1b2a3d4e5f',
        eventType: 'PaymentCompletedEvent',
        payload: JSON.stringify(innerEvent),
        timestamp: '2024-04-19T14:30:00.123456789Z',
      };
    }

    it('Outbox wrapper → payload 추출 후 INSERT', async () => {
      const inner = baseEvent();
      await paymentCompletedService.save(buildOutboxEnvelope(inner));

      expect(mockInsert).toHaveBeenCalledOnce();
      expect(mockInsert).toHaveBeenCalledWith([
        expect.objectContaining({
          userId: USER_UUID,
          eventId: EVENT_UUID_1,
          actionType: 'PURCHASE',
          quantity: 2,
          totalAmount: 100000,
          timestamp: TIMESTAMP,
        }),
      ]);
    });

    it('Outbox wrapper 다건 주문 → orderItems 수만큼 fan-out', async () => {
      const inner = baseEvent({
        orderItems: [
          { eventId: EVENT_UUID_1, quantity: 2 },
          { eventId: EVENT_UUID_2, quantity: 3 },
        ],
        totalAmount: 250000,
      });

      await paymentCompletedService.save(buildOutboxEnvelope(inner));

      expect(mockInsert).toHaveBeenCalledOnce();
      expect(mockInsert).toHaveBeenCalledWith([
        expect.objectContaining({ eventId: EVENT_UUID_1, quantity: 2, totalAmount: null }),
        expect.objectContaining({ eventId: EVENT_UUID_2, quantity: 3, totalAmount: null }),
      ]);
    });

    it('wrapper 없는 raw 이벤트는 그대로 처리된다 (backward compat)', async () => {
      await paymentCompletedService.save(baseEvent());

      expect(mockInsert).toHaveBeenCalledOnce();
    });

    it('payload 필드가 문자열 아니면 unwrap 스킵 → outer를 validate → 검증 실패', async () => {
      const malformed = {
        messageId: '8b3e5a1c-4d2f-41a7-9c8e-6f1b2a3d4e5f',
        payload: { not: 'a string' },
      };

      await expect(paymentCompletedService.save(malformed)).rejects.toThrow();
      expect(mockInsert).not.toHaveBeenCalled();
    });

    it('payload 문자열이 JSON 아님 → throw', async () => {
      const malformed = {
        messageId: '8b3e5a1c-4d2f-41a7-9c8e-6f1b2a3d4e5f',
        eventType: 'PaymentCompletedEvent',
        payload: 'not-a-json{{{',
        timestamp: '2024-04-19T14:30:00Z',
      };

      await expect(paymentCompletedService.save(malformed)).rejects.toThrow('Outbox payload');
      expect(mockInsert).not.toHaveBeenCalled();
    });
  });

  describe('repository 에러 전파', () => {
    it('DB INSERT 실패 시 에러가 전파된다', async () => {
      mockInsert.mockRejectedValueOnce(new Error('DB connection failed'));

      await expect(paymentCompletedService.save(baseEvent())).rejects.toThrow(
        'DB connection failed',
      );
    });

    it('다건 주문 저장은 단일 호출 — 부분 저장 가능성 없음', async () => {
      mockInsert.mockRejectedValueOnce(new Error('DB connection failed'));

      const event = baseEvent({
        orderItems: [
          { eventId: EVENT_UUID_1, quantity: 2 },
          { eventId: EVENT_UUID_2, quantity: 3 },
        ],
        totalAmount: 250000,
      });

      await expect(paymentCompletedService.save(event)).rejects.toThrow(
        'DB connection failed',
      );
      expect(mockInsert).toHaveBeenCalledOnce();
    });
  });
});
