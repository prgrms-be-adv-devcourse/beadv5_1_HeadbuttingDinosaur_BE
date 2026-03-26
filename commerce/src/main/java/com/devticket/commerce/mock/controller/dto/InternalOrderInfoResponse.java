package com.devticket.commerce.mock.controller.dto;

public record InternalOrderInfoResponse(
    Long id,
    Long userId,
    String orderNumber,
    String paymentMethod,
    Integer totalAmount,
    String status,
    String orderedAt
) {


}
