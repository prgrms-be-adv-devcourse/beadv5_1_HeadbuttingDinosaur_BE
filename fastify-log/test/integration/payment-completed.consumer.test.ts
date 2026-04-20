import { describe, it, expect, vi, beforeEach } from 'vitest';
import { KafkaMessage } from 'kafkajs';

const {
  mockPaymentCompletedSave,
  mockLoggerWarn,
  mockLoggerError,
  mockLoggerDebug,
} = vi.hoisted(() => ({
  mockPaymentCompletedSave: vi.fn(),
  mockLoggerWarn: vi.fn(),
  mockLoggerError: vi.fn(),
  mockLoggerDebug: vi.fn(),
}));

vi.mock('../../src/service/payment-completed.service', () => ({
  paymentCompletedService: { save: mockPaymentCompletedSave },
}));

vi.mock('../../src/util/logger', () => ({
  logger: {
    info: vi.fn(),
    warn: mockLoggerWarn,
    error: mockLoggerError,
    debug: mockLoggerDebug,
  },
}));

import { handlePaymentCompletedMessage } from '../../src/consumer/payment-completed.consumer';

function createPayload(value: string | null): {
  topic: string;
  partition: number;
  message: KafkaMessage;
} {
  return {
    topic: 'payment.completed',
    partition: 0,
    message: {
      key: null,
      value: value ? Buffer.from(value) : null,
      timestamp: Date.now().toString(),
      attributes: 0,
      offset: '100',
      size: value?.length ?? 0,
      headers: {},
    },
  };
}

/**
 * Payment OutboxEventProducer가 발행하는 실제 Kafka value 구조를 그대로 모사.
 * - Outbox wrapper: {messageId, eventType, payload(JSON string), timestamp}
 * - PaymentCompletedEvent는 `payload` 필드에 **문자열화된 JSON**으로 저장됨
 * - Jackson Instant는 WRITE_DATES_AS_TIMESTAMPS=false로 ISO-8601 문자열
 * - UUID는 소문자 hyphen 형식
 * - PaymentMethod enum은 name 문자열 ("PG" / "WALLET" / "WALLET_PG")
 */
function buildOutboxEnvelope(innerEvent: Record<string, unknown>): string {
  return JSON.stringify({
    messageId: '8b3e5a1c-4d2f-41a7-9c8e-6f1b2a3d4e5f',
    eventType: 'PaymentCompletedEvent',
    payload: JSON.stringify(innerEvent),
    timestamp: '2024-04-19T14:30:00.123456789Z',
  });
}

describe('PaymentCompletedConsumer — Outbox wrapper unwrap', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('실제 Payment Outbox 구조 파싱', () => {
    it('단건 주문 wrapper → payload 추출 후 service.save 호출', async () => {
      mockPaymentCompletedSave.mockResolvedValueOnce(undefined);

      const innerEvent = {
        orderId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
        userId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
        paymentId: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
        paymentMethod: 'PG',
        totalAmount: 100000,
        orderItems: [
          { eventId: '11111111-1111-4111-8111-111111111111', quantity: 2 },
        ],
        timestamp: '2024-04-19T14:30:00Z',
      };
      const payload = createPayload(buildOutboxEnvelope(innerEvent));

      await handlePaymentCompletedMessage(payload);

      expect(mockPaymentCompletedSave).toHaveBeenCalledOnce();
      expect(mockPaymentCompletedSave).toHaveBeenCalledWith(
        expect.objectContaining({
          orderId: innerEvent.orderId,
          userId: innerEvent.userId,
          totalAmount: 100000,
          orderItems: [
            { eventId: '11111111-1111-4111-8111-111111111111', quantity: 2 },
          ],
        }),
      );
    });

    it('다건 주문 wrapper → orderItems 구조 유지', async () => {
      mockPaymentCompletedSave.mockResolvedValueOnce(undefined);

      const innerEvent = {
        orderId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
        userId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
        paymentId: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
        paymentMethod: 'WALLET_PG',
        totalAmount: 250000,
        orderItems: [
          { eventId: '11111111-1111-4111-8111-111111111111', quantity: 2 },
          { eventId: '22222222-2222-4222-8222-222222222222', quantity: 3 },
        ],
        timestamp: '2024-04-19T14:30:00.987654321Z',
      };
      const payload = createPayload(buildOutboxEnvelope(innerEvent));

      await handlePaymentCompletedMessage(payload);

      expect(mockPaymentCompletedSave).toHaveBeenCalledWith(
        expect.objectContaining({
          orderItems: expect.arrayContaining([
            expect.objectContaining({ quantity: 2 }),
            expect.objectContaining({ quantity: 3 }),
          ]),
          timestamp: '2024-04-19T14:30:00.987654321Z',
        }),
      );
    });

    it('Jackson 나노초 포함 Instant 포맷도 그대로 전달된다', async () => {
      mockPaymentCompletedSave.mockResolvedValueOnce(undefined);

      const innerEvent = {
        orderId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
        userId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
        paymentMethod: 'WALLET',
        totalAmount: 10000,
        orderItems: [{ eventId: '11111111-1111-4111-8111-111111111111', quantity: 1 }],
        timestamp: '2024-04-19T14:30:00.123456789Z',
      };
      const payload = createPayload(buildOutboxEnvelope(innerEvent));

      await handlePaymentCompletedMessage(payload);

      expect(mockPaymentCompletedSave).toHaveBeenCalledWith(
        expect.objectContaining({ timestamp: '2024-04-19T14:30:00.123456789Z' }),
      );
    });
  });

  describe('wrapper 없는 원본 payload (backward compat)', () => {
    it('payload 필드 없는 raw 이벤트는 그대로 전달된다', async () => {
      mockPaymentCompletedSave.mockResolvedValueOnce(undefined);

      const raw = JSON.stringify({
        orderId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
        userId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
        totalAmount: 10000,
        orderItems: [{ eventId: '11111111-1111-4111-8111-111111111111', quantity: 1 }],
        timestamp: '2024-04-19T14:30:00Z',
      });
      const payload = createPayload(raw);

      await handlePaymentCompletedMessage(payload);

      expect(mockPaymentCompletedSave).toHaveBeenCalledOnce();
    });
  });

  describe('실패 케이스', () => {
    it('payload 필드가 문자열이 아니면 unwrap 불가 → skip', async () => {
      // payload가 object로 들어오는 비정상 케이스 — unwrap 스킵하고 outer 그대로 service에 전달
      // service에서 orderItems 검증 실패로 rejects → consumer 수준에서는 error log + skip
      mockPaymentCompletedSave.mockRejectedValueOnce(new Error('orderItems 누락'));

      const malformed = JSON.stringify({
        messageId: '8b3e5a1c-4d2f-41a7-9c8e-6f1b2a3d4e5f',
        payload: { not: 'a string' },
      });
      const payload = createPayload(malformed);

      await expect(handlePaymentCompletedMessage(payload)).resolves.toBeUndefined();
      expect(mockLoggerError).toHaveBeenCalled();
    });

    it('payload 문자열이 JSON 아님 → unwrap 실패 warn + skip', async () => {
      const malformed = JSON.stringify({
        messageId: '8b3e5a1c-4d2f-41a7-9c8e-6f1b2a3d4e5f',
        eventType: 'PaymentCompletedEvent',
        payload: 'not-a-json{{{',
        timestamp: '2024-04-19T14:30:00Z',
      });
      const payload = createPayload(malformed);

      await handlePaymentCompletedMessage(payload);

      expect(mockPaymentCompletedSave).not.toHaveBeenCalled();
      expect(mockLoggerWarn).toHaveBeenCalled();
    });

    it('service.save 실패 → 로그 + skip (throw 안 함)', async () => {
      mockPaymentCompletedSave.mockRejectedValueOnce(new Error('UUID 형식 오류'));

      const innerEvent = {
        userId: 'not-a-uuid',
        totalAmount: 100,
        orderItems: [{ eventId: 'also-not-uuid', quantity: 1 }],
        timestamp: '2024-04-19T14:30:00Z',
      };
      const payload = createPayload(buildOutboxEnvelope(innerEvent));

      await expect(handlePaymentCompletedMessage(payload)).resolves.toBeUndefined();
      expect(mockLoggerError).toHaveBeenCalled();
    });
  });
});
