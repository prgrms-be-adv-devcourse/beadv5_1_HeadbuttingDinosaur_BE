package com.devticket.commerce.order.presentation.dto.res;

import com.devticket.commerce.order.domain.model.OrderItem;
import java.util.UUID;
import lombok.Builder;

// Payment 의 /internal/order-items/by-ticket/{ticketId} 응답.
// amount 필드는 "해당 티켓 1장의 단가" 를 의미 — Refund 단건 환불 계산 기준.
// OrderItem.subtotalAmount(= price × quantity) 을 넘기면 단건 환불이 전량 환불로 잘못 계산되므로
// 반드시 getPrice() 를 사용한다.
@Builder
public record InternalOrderItemResponse(
    UUID orderItemId,
    UUID orderId,
    UUID userId,
    UUID eventId,
    Integer amount
) {

    public static InternalOrderItemResponse from(OrderItem orderItem, UUID orderUuid) {
        return new InternalOrderItemResponse(
            orderItem.getOrderItemId(),
            orderUuid,
            orderItem.getUserId(),
            orderItem.getEventId(),
            orderItem.getPrice()
        );
    }
}