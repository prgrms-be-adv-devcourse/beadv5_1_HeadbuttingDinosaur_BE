package com.devticket.commerce.order.infrastructure.external.client.dto;

import com.devticket.commerce.cart.domain.model.CartItem;
import com.devticket.commerce.order.domain.model.OrderItem;
import java.util.List;
import java.util.UUID;

public record InternalBulkStockAdjustmentRequest(
    List<EventItem> items  // eventItems → items
) {

    public record EventItem(
        UUID id,           // eventId → id
        int delta          // quantityDelta → delta
    ) {

    }

    public static InternalBulkStockAdjustmentRequest createForOrder(List<CartItem> cartItems) {
        return new InternalBulkStockAdjustmentRequest(
            cartItems.stream()
                .map(item -> new EventItem(item.getEventId(), item.getQuantity()))
                .toList()
        );
    }

    public static InternalBulkStockAdjustmentRequest createForCancel(List<CartItem> cartItems) {
        return new InternalBulkStockAdjustmentRequest(
            cartItems.stream()
                .map(item -> new EventItem(item.getEventId(), -item.getQuantity()))
                .toList()
        );
    }

    public static InternalBulkStockAdjustmentRequest createForCancelByOrderItems(List<OrderItem> orderItems) {
        return new InternalBulkStockAdjustmentRequest(
            orderItems.stream()
                .map(item -> new EventItem(item.getEventId(), -item.getQuantity()))
                .toList()
        );
    }
}


