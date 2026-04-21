import { describe, it, expect, vi, beforeEach } from 'vitest';
import { KafkaMessage } from 'kafkajs';

const {
  mockActionLogSave,
  mockPaymentCompletedSave,
  mockCommitOffsets,
  mockConnect,
  mockSubscribe,
  mockRun,
  mockLoggerInfo,
  mockLoggerWarn,
  mockLoggerError,
  mockLoggerDebug,
} = vi.hoisted(() => ({
  mockActionLogSave: vi.fn(),
  mockPaymentCompletedSave: vi.fn(),
  mockCommitOffsets: vi.fn().mockResolvedValue(undefined),
  mockConnect: vi.fn().mockResolvedValue(undefined),
  mockSubscribe: vi.fn().mockResolvedValue(undefined),
  mockRun: vi.fn(),
  mockLoggerInfo: vi.fn(),
  mockLoggerWarn: vi.fn(),
  mockLoggerError: vi.fn(),
  mockLoggerDebug: vi.fn(),
}));

let capturedEachMessage: ((payload: {
  topic: string;
  partition: number;
  message: KafkaMessage;
}) => Promise<void>) | null = null;

vi.mock('../../src/config/kafka', () => ({
  getConsumer: () => ({
    connect: mockConnect,
    subscribe: mockSubscribe,
    run: mockRun,
    commitOffsets: mockCommitOffsets,
  }),
}));

vi.mock('../../src/service/action-log.service', () => ({
  actionLogService: { save: mockActionLogSave },
}));

vi.mock('../../src/service/payment-completed.service', () => ({
  paymentCompletedService: { save: mockPaymentCompletedSave },
}));

vi.mock('../../src/config/env', () => ({
  env: {
    KAFKA_GROUP_ID: 'log-group',
    KAFKA_TOPIC_ACTION_LOG: 'action.log',
    KAFKA_TOPIC_PAYMENT_COMPLETED: 'payment.completed',
    LOG_LEVEL: 'silent',
  },
}));

vi.mock('../../src/util/logger', () => ({
  logger: {
    info: mockLoggerInfo,
    warn: mockLoggerWarn,
    error: mockLoggerError,
    debug: mockLoggerDebug,
  },
}));

import { startActionLogConsumer } from '../../src/consumer/action-log.consumer';

function createMessage(value: string | null): KafkaMessage {
  return {
    key: null,
    value: value ? Buffer.from(value) : null,
    timestamp: Date.now().toString(),
    attributes: 0,
    offset: '42',
    size: value?.length ?? 0,
    headers: {},
  };
}

function createPayload(
  value: string | null,
  topic: string = 'action.log',
): {
  topic: string;
  partition: number;
  message: KafkaMessage;
} {
  return {
    topic,
    partition: 0,
    message: createMessage(value),
  };
}

describe('ActionLogConsumer (topic dispatch)', () => {
  beforeEach(async () => {
    vi.clearAllMocks();
    capturedEachMessage = null;

    mockRun.mockImplementation(async (config: { eachMessage: typeof capturedEachMessage }) => {
      capturedEachMessage = config.eachMessage;
    });

    await startActionLogConsumer();
  });

  describe('초기화', () => {
    it('Kafka consumer에 연결한다', () => {
      expect(mockConnect).toHaveBeenCalledOnce();
    });

    it('action.log + payment.completed 토픽을 구독한다', () => {
      expect(mockSubscribe).toHaveBeenCalledWith({
        topics: ['action.log', 'payment.completed'],
        fromBeginning: false,
      });
    });

    it('autoCommit false로 실행한다', () => {
      expect(mockRun).toHaveBeenCalledWith(
        expect.objectContaining({ autoCommit: false }),
      );
    });

    it('eachMessage 핸들러가 등록된다', () => {
      expect(capturedEachMessage).toBeDefined();
      expect(typeof capturedEachMessage).toBe('function');
    });
  });

  describe('action.log 토픽 정상 처리', () => {
    it('action.log 메시지 → actionLogService.save 호출 + commit', async () => {
      mockActionLogSave.mockResolvedValueOnce(undefined);
      const payload = createPayload(
        JSON.stringify({
          userId: 'user-uuid',
          eventId: 'event-uuid',
          actionType: 'DETAIL_VIEW',
          timestamp: '2025-08-15T14:30:00',
        }),
        'action.log',
      );

      await capturedEachMessage!(payload);

      expect(mockActionLogSave).toHaveBeenCalledOnce();
      expect(mockPaymentCompletedSave).not.toHaveBeenCalled();
      expect(mockCommitOffsets).toHaveBeenCalledWith([
        { topic: 'action.log', partition: 0, offset: '43' },
      ]);
    });
  });

  describe('payment.completed 토픽 정상 처리', () => {
    it('payment.completed 메시지 → paymentCompletedService.save 호출 + commit', async () => {
      mockPaymentCompletedSave.mockResolvedValueOnce(undefined);
      const payload = createPayload(
        JSON.stringify({
          orderId: 'order-uuid',
          userId: 'user-uuid',
          orderItems: [{ eventId: 'event-uuid', quantity: 1 }],
          totalAmount: 50000,
          timestamp: '2025-08-15T14:30:00Z',
        }),
        'payment.completed',
      );

      await capturedEachMessage!(payload);

      expect(mockPaymentCompletedSave).toHaveBeenCalledOnce();
      expect(mockActionLogSave).not.toHaveBeenCalled();
      expect(mockCommitOffsets).toHaveBeenCalledWith([
        { topic: 'payment.completed', partition: 0, offset: '43' },
      ]);
    });

    it('payment.completed 처리 실패 → 로그 + skip + commit (throw 안 함)', async () => {
      mockPaymentCompletedSave.mockRejectedValueOnce(new Error('UUID 형식 오류'));
      const payload = createPayload(
        JSON.stringify({ userId: 'user-uuid' }),
        'payment.completed',
      );

      await expect(capturedEachMessage!(payload)).resolves.toBeUndefined();

      expect(mockLoggerError).toHaveBeenCalled();
      expect(mockCommitOffsets).toHaveBeenCalled();
    });
  });

  describe('비정상 메시지 — skip 처리', () => {
    it('action.log 빈 메시지 → skip + commit', async () => {
      const payload = createPayload(null, 'action.log');

      await capturedEachMessage!(payload);

      expect(mockActionLogSave).not.toHaveBeenCalled();
      expect(mockCommitOffsets).toHaveBeenCalled();
      expect(mockLoggerWarn).toHaveBeenCalled();
    });

    it('payment.completed 빈 메시지 → skip + commit', async () => {
      const payload = createPayload(null, 'payment.completed');

      await capturedEachMessage!(payload);

      expect(mockPaymentCompletedSave).not.toHaveBeenCalled();
      expect(mockCommitOffsets).toHaveBeenCalled();
      expect(mockLoggerWarn).toHaveBeenCalled();
    });

    it('action.log JSON 파싱 불가 → skip + commit', async () => {
      const payload = createPayload('not-a-json{{{', 'action.log');

      await capturedEachMessage!(payload);

      expect(mockActionLogSave).not.toHaveBeenCalled();
      expect(mockCommitOffsets).toHaveBeenCalled();
      expect(mockLoggerWarn).toHaveBeenCalled();
    });

    it('payment.completed JSON 파싱 불가 → skip + commit', async () => {
      const payload = createPayload('not-a-json{{{', 'payment.completed');

      await capturedEachMessage!(payload);

      expect(mockPaymentCompletedSave).not.toHaveBeenCalled();
      expect(mockCommitOffsets).toHaveBeenCalled();
      expect(mockLoggerWarn).toHaveBeenCalled();
    });

    it('actionLogService.save 실패 → 로그 + skip + commit', async () => {
      mockActionLogSave.mockRejectedValueOnce(new Error('유효하지 않은 actionType'));
      const payload = createPayload(
        JSON.stringify({ userId: 'user-uuid', actionType: 'INVALID', timestamp: '2025-08-15T14:30:00' }),
        'action.log',
      );

      await expect(capturedEachMessage!(payload)).resolves.toBeUndefined();

      expect(mockLoggerError).toHaveBeenCalled();
      expect(mockCommitOffsets).toHaveBeenCalled();
    });
  });

  describe('알 수 없는 토픽', () => {
    it('등록되지 않은 토픽 → skip + commit', async () => {
      const payload = createPayload(
        JSON.stringify({ foo: 'bar' }),
        'unknown.topic',
      );

      await capturedEachMessage!(payload);

      expect(mockActionLogSave).not.toHaveBeenCalled();
      expect(mockPaymentCompletedSave).not.toHaveBeenCalled();
      expect(mockCommitOffsets).toHaveBeenCalled();
      expect(mockLoggerWarn).toHaveBeenCalled();
    });
  });

  describe('consumer는 절대 throw하지 않는다', () => {
    it('action.log 예외도 전파되지 않는다', async () => {
      mockActionLogSave.mockRejectedValueOnce(new Error('catastrophic'));
      const payload = createPayload(
        JSON.stringify({ userId: 'u', actionType: 'DETAIL_VIEW', timestamp: 't' }),
        'action.log',
      );

      await expect(capturedEachMessage!(payload)).resolves.toBeUndefined();
    });

    it('payment.completed 예외도 전파되지 않는다', async () => {
      mockPaymentCompletedSave.mockRejectedValueOnce(new Error('catastrophic'));
      const payload = createPayload(
        JSON.stringify({ userId: 'u' }),
        'payment.completed',
      );

      await expect(capturedEachMessage!(payload)).resolves.toBeUndefined();
    });
  });
});
