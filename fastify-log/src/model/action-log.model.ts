import { ActionType } from './action-type.enum';

export interface ActionLog {
  userId: string;
  eventId: string | null;
  actionType: ActionType;
  searchKeyword: string | null;
  stackFilter: string | null;
  dwellTimeSeconds: number | null;
  quantity: number | null;
  totalAmount: number | null;
  timestamp: string;
}

export interface ActionLogMessage {
  userId: string;
  eventId?: string | null;
  actionType: string;
  searchKeyword?: string | null;
  stackFilter?: string | null;
  dwellTimeSeconds?: number | null;
  quantity?: number | null;
  totalAmount?: number | null;
  timestamp: string;
}
