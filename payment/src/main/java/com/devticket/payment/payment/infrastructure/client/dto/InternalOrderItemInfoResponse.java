package com.devticket.payment.payment.infrastructure.client.dto;

import java.util.UUID;

public record InternalOrderItemInfoResponse(
    UUID orderItemId,
    UUID orderId,
    UUID userId,
    UUID eventId,        // 추가 — Event 조회에 필요
    Integer amount
) {}
