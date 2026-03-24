package com.devticket.commerce.cart.presentation.dto.res;

import java.util.List;

public record InternalOrderItemsResponse(
    Long orderId,
    List<InternalOrderItemsResponse.Item> items
) {

    public record Item(
        Long orderItemId,
        Long eventId,
        Integer price,
        Integer quantity,
        Integer subtotalAmount
    ) {

    }

}
