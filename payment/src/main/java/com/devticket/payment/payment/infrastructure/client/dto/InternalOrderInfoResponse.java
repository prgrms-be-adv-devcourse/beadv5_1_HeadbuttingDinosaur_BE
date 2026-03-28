package com.devticket.payment.payment.infrastructure.client.dto;

import java.util.UUID;

public record InternalOrderInfoResponse(
    Long id,
    UUID userId,
    String orderNumber,// ex) "Spring Boot 심화 밋업 외 1건"
    int totalAmount,
    String status,
    String orderedAt
) {}
