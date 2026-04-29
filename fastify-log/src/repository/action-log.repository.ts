import { pool } from '../config/database';
import { ActionLog } from '../model/action-log.model';
import { ActionType } from '../model/action-type.enum';

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

// AI recentVector 배치 조회 — (user_id, created_at DESC) 복합 인덱스 활용 (V2)
// WHERE event_id IS NOT NULL: VIEW 중 eventId null row는 분석 대상 외 선제 제외
const FIND_RECENT_ACTIONS = `
  SELECT event_id AS "eventId",
         action_type AS "actionType",
         dwell_time_seconds AS "dwellTimeSeconds"
  FROM log.action_log
  WHERE user_id = $1
    AND action_type = ANY($2::text[])
    AND created_at >= NOW() - ($3::int || ' days')::INTERVAL
    AND event_id IS NOT NULL
  ORDER BY created_at DESC
  LIMIT $4
`;

export interface RecentActionRow {
  eventId: string;
  actionType: ActionType;
  dwellTimeSeconds: number | null;
}

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

export async function findRecentActions(
  userId: string,
  actionTypes: string[],
  days: number,
  limit: number,
): Promise<RecentActionRow[]> {
  const res = await pool.query(FIND_RECENT_ACTIONS, [userId, actionTypes, days, limit]);
  return res.rows as RecentActionRow[];
}

export const actionLogRepository = {
  insertActionLog,
  insertActionLogs,
  findRecentActions,
};
