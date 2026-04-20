import { EachMessagePayload } from 'kafkajs';

import { env } from '../config/env';
import { getConsumer } from '../config/kafka';
import { actionLogService } from '../service/action-log.service';
import { logger } from '../util/logger';
import { handlePaymentCompletedMessage } from './payment-completed.consumer';

export async function startActionLogConsumer(): Promise<void> {
  const consumer = getConsumer();

  await consumer.connect();
  logger.info({ groupId: env.KAFKA_GROUP_ID }, 'Kafka consumer 연결 완료');

  const topics = [env.KAFKA_TOPIC_ACTION_LOG, env.KAFKA_TOPIC_PAYMENT_COMPLETED];
  await consumer.subscribe({
    topics,
    fromBeginning: false,
  });
  logger.info({ topics }, 'Kafka 토픽 구독 시작');

  await consumer.run({
    autoCommit: false,
    eachMessage: async (payload: EachMessagePayload) => {
      await dispatchMessage(payload);
    },
  });
}

async function dispatchMessage(payload: EachMessagePayload): Promise<void> {
  const { topic, partition, message } = payload;

  try {
    if (topic === env.KAFKA_TOPIC_PAYMENT_COMPLETED) {
      await handlePaymentCompletedMessage(payload);
    } else if (topic === env.KAFKA_TOPIC_ACTION_LOG) {
      await handleActionLogMessage(payload);
    } else {
      logger.warn({ topic, partition, offset: message.offset }, '알 수 없는 토픽 — skip');
    }
  } catch (error) {
    logger.error(
      { error, topic, partition, offset: message.offset },
      'dispatch 예외 — skip',
    );
  }

  try {
    await commitOffset(payload);
  } catch (error) {
    logger.error(
      { error, topic, partition, offset: message.offset },
      'Kafka offset commit 실패 — skip',
    );
  }
}

async function handleActionLogMessage(payload: EachMessagePayload): Promise<void> {
  const { topic, partition, message } = payload;

  try {
    const raw = message.value?.toString();
    if (!raw) {
      logger.warn({ topic, partition, offset: message.offset }, '빈 메시지 수신 — skip');
      return;
    }

    let parsed: unknown;
    try {
      parsed = JSON.parse(raw);
    } catch {
      logger.warn({ topic, partition, offset: message.offset }, 'JSON 파싱 실패 — skip');
      return;
    }

    await actionLogService.save(parsed);
    logger.debug({ offset: message.offset }, 'action log 저장 완료');
  } catch (error) {
    logger.error(
      { error, topic, partition, offset: message.offset },
      'action log 처리 실패 — skip',
    );
  }
}

async function commitOffset(payload: EachMessagePayload): Promise<void> {
  const { topic, partition, message } = payload;
  const consumer = getConsumer();

  await consumer.commitOffsets([
    {
      topic,
      partition,
      offset: (BigInt(message.offset) + 1n).toString(),
    },
  ]);
}
