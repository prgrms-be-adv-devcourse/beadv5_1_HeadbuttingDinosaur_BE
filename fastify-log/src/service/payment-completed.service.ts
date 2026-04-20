import { ActionLog } from '../model/action-log.model';
import { ActionType } from '../model/action-type.enum';
import {
  PaymentCompletedEvent,
  PaymentCompletedOrderItem,
} from '../model/payment-completed.model';
import { actionLogRepository } from '../repository/action-log.repository';

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export async function save(raw: unknown): Promise<void> {
  const event = validateAndParse(raw);
  const logs = toActionLogs(event);
  for (const log of logs) {
    await actionLogRepository.insertActionLog(log);
  }
}

function validateAndParse(raw: unknown): PaymentCompletedEvent {
  if (!raw || typeof raw !== 'object') {
    throw new Error('유효하지 않은 메시지 형식');
  }

  const msg = raw as Record<string, unknown>;

  if (!msg.userId || typeof msg.userId !== 'string') {
    throw new Error('userId 누락 또는 잘못된 타입');
  }
  if (!UUID_PATTERN.test(msg.userId)) {
    throw new Error(`userId가 UUID 형식이 아님: ${msg.userId}`);
  }

  if (!msg.timestamp || typeof msg.timestamp !== 'string') {
    throw new Error('timestamp 누락 또는 잘못된 타입');
  }

  if (typeof msg.totalAmount !== 'number' || !Number.isFinite(msg.totalAmount)) {
    throw new Error('totalAmount 누락 또는 잘못된 타입');
  }
  if (msg.totalAmount < 0) {
    throw new Error(`totalAmount 음수: ${msg.totalAmount}`);
  }

  if (!Array.isArray(msg.orderItems) || msg.orderItems.length === 0) {
    throw new Error('orderItems 누락 또는 빈 배열');
  }

  const orderItems = msg.orderItems.map(parseOrderItem);

  return {
    orderId: typeof msg.orderId === 'string' ? msg.orderId : '',
    userId: msg.userId,
    paymentId: typeof msg.paymentId === 'string' ? msg.paymentId : undefined,
    paymentMethod: typeof msg.paymentMethod === 'string' ? msg.paymentMethod : undefined,
    orderItems,
    totalAmount: msg.totalAmount,
    timestamp: msg.timestamp,
  };
}

function parseOrderItem(raw: unknown, index: number): PaymentCompletedOrderItem {
  if (!raw || typeof raw !== 'object') {
    throw new Error(`orderItems[${index}] 형식 오류`);
  }
  const item = raw as Record<string, unknown>;

  if (!item.eventId || typeof item.eventId !== 'string') {
    throw new Error(`orderItems[${index}].eventId 누락 또는 잘못된 타입`);
  }
  if (!UUID_PATTERN.test(item.eventId)) {
    throw new Error(`orderItems[${index}].eventId가 UUID 형식이 아님: ${item.eventId}`);
  }

  if (typeof item.quantity !== 'number' || !Number.isInteger(item.quantity)) {
    throw new Error(`orderItems[${index}].quantity 누락 또는 잘못된 타입`);
  }
  if (item.quantity <= 0) {
    throw new Error(`orderItems[${index}].quantity가 1 미만: ${item.quantity}`);
  }

  return { eventId: item.eventId, quantity: item.quantity };
}

function toActionLogs(event: PaymentCompletedEvent): ActionLog[] {
  // 단건 주문은 totalAmount 그대로 보존, 다건 주문은 합산 중복 방지를 위해 null 처리.
  const distributedTotal = event.orderItems.length === 1 ? event.totalAmount : null;

  return event.orderItems.map((item) => ({
    userId: event.userId,
    eventId: item.eventId,
    actionType: ActionType.PURCHASE,
    searchKeyword: null,
    stackFilter: null,
    dwellTimeSeconds: null,
    quantity: item.quantity,
    totalAmount: distributedTotal,
    timestamp: event.timestamp,
  }));
}

export const paymentCompletedService = {
  save,
};
