package com.devticket.commerce.order.presentation.dto.res;

import com.devticket.commerce.common.enums.OrderStatus;
import com.devticket.commerce.common.enums.PaymentMethod;
import com.devticket.commerce.order.domain.model.Order;
import com.devticket.commerce.order.domain.model.OrderItem;
import com.devticket.commerce.order.presentation.dto.res.OrderDetailItemResponse.TicketSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Builder;

@Builder
@Schema(description = "주문 상세 조회 응답 데이터")
public record OrderDetailResponse(
    UUID orderId,
    OrderStatus status,
    int totalAmount,
    List<OrderDetailItemResponse> orderItems,
    PaymentMethod paymentMethod,
    LocalDateTime createdAt
) {

    public static OrderDetailResponse of(
        Order order,
        List<OrderItem> orderItems,
        Map<UUID, String> eventTitles,
        Map<UUID, List<TicketSummary>> ticketsByOrderItemId
    ) {
        List<OrderDetailItemResponse> itemResponses = orderItems.stream()
            .map(item -> {
                String title = eventTitles.getOrDefault(item.getEventId(), "알 수 없는 이벤트");
                List<TicketSummary> tickets = ticketsByOrderItemId.getOrDefault(item.getOrderItemId(), List.of());
                return OrderDetailItemResponse.of(item, title, tickets);
            })
            .toList();

        return OrderDetailResponse.builder()
            .orderId(order.getOrderId())
            .status(order.getStatus())
            .totalAmount(order.getTotalAmount())
            .orderItems(itemResponses)
            .paymentMethod(order.getPaymentMethod())
            .createdAt(order.getCreatedAt())
            .build();
    }
}
