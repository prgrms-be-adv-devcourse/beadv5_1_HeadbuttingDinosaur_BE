import { describe, it, expect, vi, beforeEach } from 'vitest';
import { KafkaMessage } from 'kafkajs';

// vi.hoisted: vi.mock 호이스팅보다 먼저 실행 보장
const {
  mockSave,
  mockCommitOffsets,
  mockConnect,
  mockSubscribe,
  mockRun,
  mockLoggerInfo,
  mockLoggerWarn,
  mockLoggerError,
  mockLoggerDebug,
} = vi.hoisted(() => ({
  mockSave: vi.fn(),
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
  actionLogService: { save: mockSave },
}));

vi.mock('../../src/config/env', () => ({
  env: {
    KAFKA_GROUP_ID: 'log-group',
    KAFKA_TOPIC: 'action.log',
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

function createPayload(value: string | null): {
  topic: string;
  partition: number;
  message: KafkaMessage;
} {
  return {
    topic: 'action.log',
    partition: 0,
    message: createMessage(value),
  };
}

describe('ActionLogConsumer', () => {
  beforeEach(async () => {
    vi.clearAllMocks();
    capturedEachMessage = null;

    mockRun.mockImplementation(async (config: { eachMessage: typeof capturedEachMessage }) => {
      capturedEachMessage = config.eachMessage;
    });

    await startActionLogConsumer();
  });

  // ========== 초기화 ==========

  describe('초기화', () => {
    it('Kafka consumer에 연결한다', () => {
      expect(mockConnect).toHaveBeenCalledOnce();
    });

    it('action.log 토픽을 구독한다', () => {
      expect(mockSubscribe).toHaveBeenCalledWith({
        topic: 'action.log',
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

  // ========== 정상 메시지 처리 ==========

  describe('정상 메시지 처리', () => {
    it('유효한 메시지 → service.save 호출 + commit', async () => {
      // given
      mockSave.mockResolvedValueOnce(undefined);
      const payload = createPayload(JSON.stringify({
        userId: 'user-uuid',
        eventId: 'event-uuid',
        actionType: 'DETAIL_VIEW',
        timestamp: '2025-08-15T14:30:00',
      }));

      // when
      await capturedEachMessage!(payload);

      // then
      expect(mockSave).toHaveBeenCalledOnce();
      expect(mockSave).toHaveBeenCalledWith(
        expect.objectContaining({ actionType: 'DETAIL_VIEW' }),
      );
      expect(mockCommitOffsets).toHaveBeenCalledWith([
        { topic: 'action.log', partition: 0, offset: '43' },
      ]);
    });

    it('PURCHASE 메시지 — quantity, totalAmount 전달', async () => {
      // given
      mockSave.mockResolvedValueOnce(undefined);
      const payload = createPayload(JSON.stringify({
        userId: 'user-uuid',
        eventId: 'event-uuid',
        actionType: 'PURCHASE',
        quantity: 2,
        totalAmount: 100000,
        timestamp: '2025-08-15T14:30:00',
      }));

      // when
      await capturedEachMessage!(payload);

      // then
      expect(mockSave).toHaveBeenCalledWith(
        expect.objectContaining({ quantity: 2, totalAmount: 100000 }),
      );
      expect(mockCommitOffsets).toHaveBeenCalled();
    });

    it('REFUND 메시지 — 정상 처리', async () => {
      // given
      mockSave.mockResolvedValueOnce(undefined);
      const payload = createPayload(JSON.stringify({
        userId: 'user-uuid',
        eventId: 'event-uuid',
        actionType: 'REFUND',
        totalAmount: 50000,
        timestamp: '2025-08-15T14:30:00',
      }));

      // when
      await capturedEachMessage!(payload);

      // then
      expect(mockSave).toHaveBeenCalledWith(
        expect.objectContaining({ actionType: 'REFUND' }),
      );
      expect(mockCommitOffsets).toHaveBeenCalled();
    });
  });

  // ========== 비정상 메시지 — skip + commit ==========

  describe('비정상 메시지 — skip 처리', () => {
    it('빈 메시지 (null value) → skip + commit', async () => {
      // given
      const payload = createPayload(null);

      // when
      await capturedEachMessage!(payload);

      // then
      expect(mockSave).not.toHaveBeenCalled();
      expect(mockCommitOffsets).toHaveBeenCalled();
      expect(mockLoggerWarn).toHaveBeenCalled();
    });

    it('JSON 파싱 불가 → skip + commit', async () => {
      // given
      const payload = createPayload('not-a-json{{{');

      // when
      await capturedEachMessage!(payload);

      // then
      expect(mockSave).not.toHaveBeenCalled();
      expect(mockCommitOffsets).toHaveBeenCalled();
      expect(mockLoggerWarn).toHaveBeenCalled();
    });

    it('service.save 실패 → 로그 + skip + commit (throw 안 함)', async () => {
      // given
      mockSave.mockRejectedValueOnce(new Error('유효하지 않은 actionType: INVALID'));
      const payload = createPayload(JSON.stringify({
        userId: 'user-uuid',
        actionType: 'INVALID',
        timestamp: '2025-08-15T14:30:00',
      }));

      // when — throw가 밖으로 전파되지 않아야 함
      await expect(capturedEachMessage!(payload)).resolves.toBeUndefined();

      // then
      expect(mockLoggerError).toHaveBeenCalled();
      expect(mockCommitOffsets).toHaveBeenCalled();
    });

    it('DB INSERT 실패 → 로그 + skip + commit (throw 안 함)', async () => {
      // given
      mockSave.mockRejectedValueOnce(new Error('DB connection failed'));
      const payload = createPayload(JSON.stringify({
        userId: 'user-uuid',
        eventId: 'event-uuid',
        actionType: 'DETAIL_VIEW',
        timestamp: '2025-08-15T14:30:00',
      }));

      // when
      await expect(capturedEachMessage!(payload)).resolves.toBeUndefined();

      // then
      expect(mockLoggerError).toHaveBeenCalled();
      expect(mockCommitOffsets).toHaveBeenCalled();
    });
  });

  // ========== 핵심 원칙: 절대 throw 안 함 ==========

  describe('consumer는 절대 throw하지 않는다', () => {
    it('어떤 에러든 밖으로 전파되지 않는다', async () => {
      // given
      mockSave.mockRejectedValueOnce(new Error('unexpected catastrophic error'));
      const payload = createPayload(JSON.stringify({
        userId: 'user-uuid',
        actionType: 'DETAIL_VIEW',
        timestamp: '2025-08-15T14:30:00',
      }));

      // when & then — resolves, not rejects
      await expect(capturedEachMessage!(payload)).resolves.toBeUndefined();
    });
  });
});
