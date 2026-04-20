import { EachMessagePayload } from 'kafkajs';

import { paymentCompletedService } from '../service/payment-completed.service';
import { logger } from '../util/logger';

export async function handlePaymentCompletedMessage(payload: EachMessagePayload): Promise<void> {
  const { topic, partition, message } = payload;

  try {
    const raw = message.value?.toString();
    if (!raw) {
      logger.warn({ topic, partition, offset: message.offset }, '빈 메시지 수신 — skip');
      return;
    }

    let outer: unknown;
    try {
      outer = JSON.parse(raw);
    } catch {
      logger.warn({ topic, partition, offset: message.offset }, 'JSON 파싱 실패 — skip');
      return;
    }

    const inner = unwrapOutboxPayload(outer);
    if (inner === null) {
      logger.warn({ topic, partition, offset: message.offset }, 'Outbox payload 추출 실패 — skip');
      return;
    }

    await paymentCompletedService.save(inner);
    logger.debug({ offset: message.offset }, 'payment.completed → PURCHASE 저장 완료');
  } catch (error) {
    logger.error(
      { error, topic, partition, offset: message.offset },
      'payment.completed 처리 실패 — skip',
    );
  }
}

/**
 * Payment OutboxEventProducer는 {messageId, eventType, payload(JSON string), timestamp} 래퍼로 발행한다.
 * wrapper면 payload를 한번 더 JSON.parse 해서 실제 PaymentCompletedEvent로 복원한다.
 * wrapper가 아니면 (예: 테스트 raw 발행) 원본 그대로 반환한다.
 */
function unwrapOutboxPayload(outer: unknown): unknown {
  if (!outer || typeof outer !== 'object') return outer;
  const obj = outer as Record<string, unknown>;

  if (typeof obj.payload !== 'string') {
    return outer;
  }

  try {
    return JSON.parse(obj.payload);
  } catch {
    return null;
  }
}
