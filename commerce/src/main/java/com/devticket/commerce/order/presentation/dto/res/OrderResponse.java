package com.devticket.commerce.order.presentation.dto.res;

import com.devticket.commerce.common.enums.OrderStatus;
import com.devticket.commerce.order.domain.model.Order;
import com.devticket.commerce.order.domain.model.OrderItem;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

@Builder
@Schema(description = "주문하기 응답 데이터")
public record OrderResponse(
    UUID orderId,
    Long totalAmount,
    OrderStatus orderStatus,
    List<OrderItems> orderItems,
    LocalDateTime createdAt
) {

    public static OrderResponse of(Order order, OrderItem orderItem, String eventTitle) {

        OrderItems orderItems = OrderItems.of(orderItem, eventTitle);

        return OrderResponse.builder()
            .orderId(order.getOrderId())
            .totalAmount(order.getTotalAmount())
            .orderStatus(order.getStatus())
            .orderItems(List.of(orderItems))
            .createdAt(order.getCreatedAt())
            .build();
    }
}


//inner record
@Builder
record OrderItems(
    Long eventId,
    String eventTitle,
    int quantity,
    int price
) {

    static OrderItems of(OrderItem orderItem, String eventTitle) {
        return OrderItems.builder()
            .eventId(orderItem.getEventId())
            .eventTitle(eventTitle)
            .quantity(orderItem.getQuantity())
            .price(orderItem.getPrice())
            .build();
    }

}
