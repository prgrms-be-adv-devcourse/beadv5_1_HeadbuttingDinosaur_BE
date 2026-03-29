package com.devticket.commerce.order.presentation.dto.res;

import com.devticket.commerce.common.enums.OrderStatus;
import com.devticket.commerce.order.domain.model.Order;
import com.devticket.commerce.order.domain.model.OrderItem;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Builder;

@Builder
@Schema(description = "주문하기 응답 데이터")
public record OrderResponse(
    UUID orderId,
    Long totalAmount,
    OrderStatus orderStatus,
    List<OrderItemsResponse> orderItems,
    LocalDateTime createdAt
) {

    public static OrderResponse of(Order order, List<OrderItem> orderItems, Map<Long, String> eventTitles) {

        List<OrderItemsResponse> itemResponses = orderItems.stream()
            .map(item -> {
                String title = eventTitles.getOrDefault(item.getEventId(), "알 수 없는 이벤트");
                return OrderItemsResponse.of(item, title);
            })
            .toList();

        return OrderResponse.builder()
            .orderId(order.getOrderId())
            .totalAmount((long) order.getTotalAmount())
            .orderStatus(order.getStatus())
            .orderItems(itemResponses)
            .createdAt(order.getCreatedAt())
            .build();
    }
}


//inner record
@Builder
record OrderItemsResponse(
    Long eventId,
    String eventTitle,
    int quantity,
    int price
) {

    static OrderItemsResponse of(OrderItem orderItem, String eventTitle) {
        return OrderItemsResponse.builder()
            .eventId(orderItem.getEventId())
            .eventTitle(eventTitle)
            .quantity(orderItem.getQuantity())
            .price(orderItem.getPrice())
            .build();
    }

}
