package com.devticket.commerce.common.messaging.event;

/**
 * 사용자 행동 로그 action.log 토픽의 actionType 7종.
 * BE_log/fastify-log/src/enum/action-type.enum.ts와 문자열 일치 (AGENTS.md §11).
 */
public enum ActionType {
    VIEW,
    DETAIL_VIEW,
    CART_ADD,
    CART_REMOVE,
    PURCHASE,
    DWELL_TIME,
    REFUND
}
