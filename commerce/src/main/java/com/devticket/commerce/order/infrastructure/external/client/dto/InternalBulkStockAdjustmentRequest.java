package com.devticket.commerce.order.infrastructure.external.client.dto;

import com.devticket.commerce.cart.domain.model.CartItem;
import java.util.List;

public record InternalBulkStockAdjustmentRequest(
    List<EventItem> eventItems
) {

    //inner record
    public record EventItem(
        Long eventId,
        int quantityDelta
    ) {

    }

    //정적팩토리 메서드
    public static InternalBulkStockAdjustmentRequest createForOrder(List<CartItem> cartItems) {
        List<EventItem> items = cartItems.stream()
            .map(item -> new EventItem(
                item.getEventId(),
                -item.getQuantity()
            ))
            .toList();

        return new InternalBulkStockAdjustmentRequest(items);
    }


    // 취소 시: 주문 아이템들을 재고 복구(양수) 요청으로 변환
    public static InternalBulkStockAdjustmentRequest createForCancel(List<CartItem> cartItems) {
        return new InternalBulkStockAdjustmentRequest(
            cartItems.stream()
                .map(item -> new EventItem(item.getEventId(), item.getQuantity()))
                .toList()
        );
    }

}


