package com.devticket.commerce.presentation.dto.res;

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
