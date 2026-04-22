import { Kafka, Consumer, logLevel } from 'kafkajs';

import { env } from './env';
import { logger } from '../util/logger';

const kafka = new Kafka({
  clientId: 'devticket-log',
  brokers: env.KAFKA_BROKERS,
  logLevel: logLevel.WARN,
  retry: {
    initialRetryTime: 1000,
    retries: 5,
  },
});

let consumer: Consumer | null = null;

export function getConsumer(): Consumer {
  if (!consumer) {
    consumer = kafka.consumer({
      groupId: env.KAFKA_GROUP_ID,
      sessionTimeout: 30000,
      heartbeatInterval: 3000,
    });
  }
  return consumer;
}

export async function disconnectConsumer(): Promise<void> {
  if (consumer) {
    await consumer.disconnect();
    logger.info('Kafka consumer 연결 종료');
    consumer = null;
  }
}
