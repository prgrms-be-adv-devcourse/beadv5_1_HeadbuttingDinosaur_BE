package com.devticket.commerce.mock.controller.dto;

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
