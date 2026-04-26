import { ActionLog, ActionLogMessage } from '../model/action-log.model';
import { ActionType, isValidActionType } from '../model/action-type.enum';
import { actionLogRepository } from '../repository/action-log.repository';

export async function save(raw: unknown): Promise<void> {
  const message = validateAndParse(raw);
  const actionLog = toActionLog(message);
  await actionLogRepository.insertActionLog(actionLog);
}

function validateAndParse(raw: unknown): ActionLogMessage {
  if (!raw || typeof raw !== 'object') {
    throw new Error('유효하지 않은 메시지 형식');
  }

  const msg = raw as Record<string, unknown>;

  if (!msg.userId || typeof msg.userId !== 'string') {
    throw new Error('userId 누락 또는 잘못된 타입');
  }

  if (!msg.actionType || typeof msg.actionType !== 'string') {
    throw new Error('actionType 누락 또는 잘못된 타입');
  }

  if (!isValidActionType(msg.actionType)) {
    throw new Error(`유효하지 않은 actionType: ${msg.actionType}`);
  }

  if (!msg.timestamp || typeof msg.timestamp !== 'string') {
    throw new Error('timestamp 누락 또는 잘못된 타입');
  }

  return msg as unknown as ActionLogMessage;
}

function toActionLog(message: ActionLogMessage): ActionLog {
  return {
    userId: message.userId,
    eventId: message.eventId ?? null,
    actionType: message.actionType as ActionType,
    searchKeyword: message.searchKeyword ?? null,
    stackFilter: message.stackFilter ?? null,
    dwellTimeSeconds: message.dwellTimeSeconds ?? null,
    quantity: message.quantity ?? null,
    totalAmount: message.totalAmount ?? null,
    timestamp: message.timestamp,
  };
}

export const actionLogService = {
  save,
};
