package com.devticket.commerce.presentation.dto.res;

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
