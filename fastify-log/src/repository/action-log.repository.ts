import { pool } from '../config/database';
import { ActionLog } from '../model/action-log.model';

const INSERT_ACTION_LOG = `
  INSERT INTO log.action_log
    (user_id, event_id, action_type, search_keyword, stack_filter,
     dwell_time_seconds, quantity, total_amount, created_at)
  VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
`;

export async function insertActionLog(log: ActionLog): Promise<void> {
  await pool.query(INSERT_ACTION_LOG, [
    log.userId,
    log.eventId,
    log.actionType,
    log.searchKeyword,
    log.stackFilter,
    log.dwellTimeSeconds,
    log.quantity,
    log.totalAmount,
    log.timestamp,
  ]);
}

export const actionLogRepository = {
  insertActionLog,
};
