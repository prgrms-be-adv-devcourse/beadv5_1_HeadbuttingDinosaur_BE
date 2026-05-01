import type { FastifyInstance, FastifyReply, FastifyRequest } from 'fastify';

import { ActionType, isValidActionType } from '../model/action-type.enum';
import { actionLogService } from '../service/action-log.service';

const INTERNAL_SERVICE_ALLOWLIST = new Set(['ai']);

const DEFAULT_DAYS = 7;

interface RecentLogsQuery {
  userId: string;
  days?: number;
  actionTypes: string;
}

const recentLogsSchema = {
  querystring: {
    type: 'object',
    required: ['userId', 'actionTypes'],
    properties: {
      userId: {
        type: 'string',
        pattern:
          '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$',
      },
      days: { type: 'integer', minimum: 1, maximum: 30, default: DEFAULT_DAYS },
      actionTypes: { type: 'string', minLength: 1 },
    },
  },
} as const;

function parseActionTypes(raw: string): ActionType[] | null {
  const tokens = raw
    .split(',')
    .map((t) => t.trim())
    .filter((t) => t.length > 0);
  if (tokens.length === 0) return null;
  const validated: ActionType[] = [];
  for (const t of tokens) {
    if (!isValidActionType(t)) return null;
    validated.push(t);
  }
  return validated;
}

export async function internalLogRoutes(app: FastifyInstance): Promise<void> {
  app.addHook('onRequest', async (request: FastifyRequest, reply: FastifyReply) => {
    const header = request.headers['x-internal-service'];
    const value = Array.isArray(header) ? header[0] : header;
    if (!value) {
      return reply.status(401).send({
        statusCode: 401,
        error: 'Unauthorized',
        message: 'X-Internal-Service 헤더가 필요합니다.',
      });
    }
    if (!INTERNAL_SERVICE_ALLOWLIST.has(value)) {
      return reply.status(403).send({
        statusCode: 403,
        error: 'Forbidden',
        message: 'X-Internal-Service 값이 허용 목록에 없습니다.',
      });
    }
  });

  app.get<{ Querystring: RecentLogsQuery }>(
    '/internal/logs/actions',
    { schema: recentLogsSchema },
    async (request, reply) => {
      const { userId, days = DEFAULT_DAYS, actionTypes: rawActionTypes } = request.query;
      const actionTypes = parseActionTypes(rawActionTypes);
      if (!actionTypes) {
        return reply.status(400).send({
          statusCode: 400,
          error: 'Bad Request',
          message:
            'actionTypes는 CSV 형식의 ActionType enum 값이어야 합니다. 예: VIEW,DETAIL_VIEW,DWELL_TIME',
        });
      }

      const logs = await actionLogService.getRecentLogs(userId, days, actionTypes);
      return reply.status(200).send({ userId, logs });
    },
  );
}
