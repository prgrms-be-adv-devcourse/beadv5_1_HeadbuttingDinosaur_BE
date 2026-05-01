import { describe, it, expect } from 'vitest';

import { ActionType, isValidActionType } from '../../src/model/action-type.enum';

describe('ActionType', () => {
  describe('enum 값', () => {
    it('7개의 actionType이 정의되어 있다', () => {
      const values = Object.values(ActionType);
      expect(values).toHaveLength(7);
    });

    it.each([
      'VIEW',
      'DETAIL_VIEW',
      'CART_ADD',
      'CART_REMOVE',
      'PURCHASE',
      'DWELL_TIME',
      'REFUND',
    ])('%s가 정의되어 있다', (type) => {
      expect(Object.values(ActionType)).toContain(type);
    });
  });

  describe('isValidActionType', () => {
    it.each([
      'VIEW',
      'DETAIL_VIEW',
      'CART_ADD',
      'CART_REMOVE',
      'PURCHASE',
      'DWELL_TIME',
      'REFUND',
    ])('%s → true', (type) => {
      expect(isValidActionType(type)).toBe(true);
    });

    it.each([
      'INVALID',
      'view',
      'detail_view',
      '',
      'SEARCH',
      'LOGIN',
    ])('%s → false', (type) => {
      expect(isValidActionType(type)).toBe(false);
    });
  });
});
