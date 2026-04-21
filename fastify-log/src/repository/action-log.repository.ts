import { pool } from '../config/database';
import { ActionLog } from '../model/action-log.model';

const COLUMN_COUNT = 9;

const INSERT_ACTION_LOG = `
  INSERT INTO log.action_log
    (user_id, event_id, action_type, search_keyword, stack_filter,
     dwell_time_seconds, quantity, total_amount, created_at)
  VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
`;

const INSERT_ACTION_LOG_PREFIX = `
  INSERT INTO log.action_log
    (user_id, event_id, action_type, search_keyword, stack_filter,
     dwell_time_seconds, quantity, total_amount, created_at)
  VALUES
`;

export async function insertActionLog(log: ActionLog): Promise<void> {
  await pool.query(INSERT_ACTION_LOG, toParams(log));
}

export async function insertActionLogs(logs: ActionLog[]): Promise<void> {
  if (logs.length === 0) return;
  if (logs.length === 1) {
    await insertActionLog(logs[0]);
    return;
  }

  const placeholders: string[] = [];
  const params: unknown[] = [];

  logs.forEach((log, index) => {
    const base = index * COLUMN_COUNT;
    const row = Array.from({ length: COLUMN_COUNT }, (_, i) => `$${base + i + 1}`).join(', ');
    placeholders.push(`(${row})`);
    params.push(...toParams(log));
  });

  await pool.query(INSERT_ACTION_LOG_PREFIX + placeholders.join(', '), params);
}

function toParams(log: ActionLog): unknown[] {
  return [
    log.userId,
    log.eventId,
    log.actionType,
    log.searchKeyword,
    log.stackFilter,
    log.dwellTimeSeconds,
    log.quantity,
    log.totalAmount,
    log.timestamp,
  ];
}

export const actionLogRepository = {
  insertActionLog,
  insertActionLogs,
};
