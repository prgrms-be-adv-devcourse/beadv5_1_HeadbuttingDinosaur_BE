package com.devticket.payment.payment.infrastructure.client.dto;

public record InternalOrderInfoResponse(
    Long id,
    Long userId,
    String orderNumber,// ex) "Spring Boot 심화 밋업 외 1건"
    int totalAmount,
    String status,
    String orderedAt
) {}
