package com.devticket.commerce.order.presentation.dto.res;

import com.devticket.commerce.common.enums.OrderStatus;
import com.devticket.commerce.common.enums.PaymentMethod;
import com.devticket.commerce.order.domain.model.Order;
import com.devticket.commerce.order.domain.model.OrderItem;
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

    public static OrderDetailResponse of(Order order, List<OrderItem> orderItems, Map<UUID, String> eventTitles) {
        List<OrderDetailItemResponse> itemResponses = orderItems.stream()
            .map(item -> {
                String title = eventTitles.getOrDefault(item.getEventId(), "알 수 없는 이벤트");
                return OrderDetailItemResponse.of(item, title);
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

