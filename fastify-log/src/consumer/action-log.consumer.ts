import { EachMessagePayload } from 'kafkajs';

import { env } from '../config/env';
import { getConsumer } from '../config/kafka';
import { actionLogService } from '../service/action-log.service';
import { logger } from '../util/logger';

export async function startActionLogConsumer(): Promise<void> {
  const consumer = getConsumer();

  await consumer.connect();
  logger.info({ groupId: env.KAFKA_GROUP_ID }, 'Kafka consumer 연결 완료');

  await consumer.subscribe({
    topic: env.KAFKA_TOPIC,
    fromBeginning: false,
  });
  logger.info({ topic: env.KAFKA_TOPIC }, 'Kafka 토픽 구독 시작');

  await consumer.run({
    autoCommit: false,
    eachMessage: async (payload: EachMessagePayload) => {
      await handleMessage(payload);
    },
  });
}

async function handleMessage(payload: EachMessagePayload): Promise<void> {
  const { topic, partition, message } = payload;

  try {
    const raw = message.value?.toString();
    if (!raw) {
      logger.warn({ topic, partition, offset: message.offset }, '빈 메시지 수신 — skip');
      await commitOffset(payload);
      return;
    }

    let parsed: unknown;
    try {
      parsed = JSON.parse(raw);
    } catch {
      logger.warn({ topic, partition, offset: message.offset }, 'JSON 파싱 실패 — skip');
      await commitOffset(payload);
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

  try {
    await commitOffset(payload);
  } catch (error) {
    logger.error(
      { error, topic, partition, offset: message.offset },
      'Kafka offset commit 실패 — skip',
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
