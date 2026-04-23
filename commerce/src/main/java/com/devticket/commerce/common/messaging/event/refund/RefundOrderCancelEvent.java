package com.devticket.commerce.common.messaging.event.refund;

import java.time.Instant;
import java.util.UUID;

// Payment → Commerce: Order PAID → REFUND_PENDING 전이 요청.
// fullRefund=true 면 오더 전체 환불, false 면 부분 환불 (현재 REFUND_PENDING 은 full 만 사용).
public record RefundOrderCancelEvent(
    UUID refundId,
    UUID orderId,
    boolean wholeOrder,
    Instant timestamp
) {

}
