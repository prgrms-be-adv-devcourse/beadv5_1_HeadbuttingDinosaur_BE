package com.devticket.commerce.order.presentation.dto.res;

import static org.assertj.core.api.Assertions.assertThat;

import com.devticket.commerce.order.domain.model.OrderItem;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Payment 의 /internal/order-items/by-ticket/{ticketId} 응답 매핑 회귀 테스트.
 *
 * <p>amount 필드는 "티켓 1장 단가" 여야 한다. OrderItem 전체 합계(subtotalAmount)로
 * 매핑되면 단건 환불이 해당 OrderItem 의 전량 환불로 확대되어 복합결제의 wallet·pg 분배가 파손된다.
 */
class InternalOrderItemResponseTest {

    @Test
    void amount_is_per_ticket_price_not_subtotal() {
        // 티켓 1장 단가 10_000, 2장 주문 → subtotalAmount = 20_000
        OrderItem item = OrderItem.create(
            1L, UUID.randomUUID(), UUID.randomUUID(),
            10_000, 2, 100
        );
        assertThat(item.getPrice()).isEqualTo(10_000);
        assertThat(item.getSubtotalAmount()).isEqualTo(20_000);

        InternalOrderItemResponse dto = InternalOrderItemResponse.from(item, UUID.randomUUID());

        // amount 는 subtotal(20_000) 이 아닌 price(10_000) 여야 함 — 단건 환불 기준
        assertThat(dto.amount()).isEqualTo(10_000);
    }
}
