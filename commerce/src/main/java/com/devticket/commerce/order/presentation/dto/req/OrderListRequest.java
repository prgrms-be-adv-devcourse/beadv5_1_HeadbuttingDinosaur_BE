package com.devticket.commerce.order.presentation.dto.req;

public record OrderListRequest(
    int page,
    int size,
    String status
) {
    public OrderListRequest {
        if (page < 1) page = 1;
        if (size <= 0) size = 10;
    }
}