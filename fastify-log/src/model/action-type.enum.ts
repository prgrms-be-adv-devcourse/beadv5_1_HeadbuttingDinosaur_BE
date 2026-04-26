export enum ActionType {
  VIEW = 'VIEW',
  DETAIL_VIEW = 'DETAIL_VIEW',
  CART_ADD = 'CART_ADD',
  CART_REMOVE = 'CART_REMOVE',
  PURCHASE = 'PURCHASE',
  DWELL_TIME = 'DWELL_TIME',
  REFUND = 'REFUND',
}

const ACTION_TYPE_VALUES = new Set<string>(Object.values(ActionType));

export function isValidActionType(value: string): value is ActionType {
  return ACTION_TYPE_VALUES.has(value);
}
