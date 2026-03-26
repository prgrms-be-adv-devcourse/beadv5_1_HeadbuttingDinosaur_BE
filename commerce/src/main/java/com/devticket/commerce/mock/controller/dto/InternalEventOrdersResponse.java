package com.devticket.commerce.mock.controller.dto;

import java.util.List;

public record InternalEventOrdersResponse(
    Long eventId,
    List<InternalEventOrdersResponse.Orders> orders
) {

    public record Orders(
        Long id,
        Long userId,
        String orderNumber,
        String paymentMethod,
        Integer totalAmount,
        String status,
        String orderedAt
    ) {

    }

}
