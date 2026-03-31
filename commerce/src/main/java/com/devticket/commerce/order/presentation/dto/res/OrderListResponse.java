package com.devticket.commerce.order.presentation.dto.res;

import com.devticket.commerce.common.enums.OrderStatus;
import com.devticket.commerce.order.domain.model.Order;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import org.springframework.data.domain.Page;

@Builder
@Schema(description = "주문 목록 조회 응답 데이터")
public record OrderListResponse(
    List<OrderSummary> orders,
    int totalPages,
    long totalElements
) {

    public static OrderListResponse of(Page<Order> orderPage) {
        List<OrderSummary> orders = orderPage.getContent().stream()
            .map(OrderSummary::of)
            .toList();

        return OrderListResponse.builder()
            .orders(orders)
            .totalPages(orderPage.getTotalPages())
            .totalElements(orderPage.getTotalElements())
            .build();
    }
}


@Builder
record OrderSummary(
    UUID orderId,
    int totalAmount,
    OrderStatus status,
    LocalDateTime createdAt
) {

    static OrderSummary of(Order order) {
        return OrderSummary.builder()
            .orderId(order.getOrderId())
            .totalAmount(order.getTotalAmount())
            .status(order.getStatus())
            .createdAt(order.getCreatedAt())
            .build();
    }
}