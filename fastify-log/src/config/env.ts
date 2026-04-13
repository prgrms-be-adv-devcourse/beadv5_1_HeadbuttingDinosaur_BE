import dotenv from 'dotenv';

dotenv.config();

function requireEnv(key: string): string {
  const value = process.env[key];
  if (!value) {
    // eslint-disable-next-line no-console -- 로거 초기화 전 fatal 에러
    console.error(`[FATAL] 환경변수 ${key}가 설정되지 않았습니다.`);
    process.exit(1);
  }
  return value;
}

function optionalEnv(key: string, defaultValue: string): string {
  return process.env[key] ?? defaultValue;
}

export const env = {
  // Database
  DB_HOST: requireEnv('DB_HOST'),
  DB_PORT: parseInt(optionalEnv('DB_PORT', '5432'), 10),
  DB_NAME: requireEnv('DB_NAME'),
  DB_USER: requireEnv('DB_USER'),
  DB_PASSWORD: requireEnv('DB_PASSWORD'),
  DB_SCHEMA: optionalEnv('DB_SCHEMA', 'log'),

  // Kafka
  KAFKA_BROKERS: requireEnv('KAFKA_BROKERS').split(','),
  KAFKA_GROUP_ID: optionalEnv('KAFKA_GROUP_ID', 'log-group'),
  KAFKA_TOPIC: optionalEnv('KAFKA_TOPIC', 'action.log'),

  // Server
  PORT: parseInt(optionalEnv('PORT', '8085'), 10),
  NODE_ENV: optionalEnv('NODE_ENV', 'development'),
  LOG_LEVEL: optionalEnv('LOG_LEVEL', 'info'),
} as const;
