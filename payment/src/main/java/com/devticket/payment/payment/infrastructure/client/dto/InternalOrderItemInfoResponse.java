package com.devticket.payment.payment.infrastructure.client.dto;

import java.util.UUID;

public record InternalOrderItemInfoResponse(
    Long orderItemId,
    Long orderId,
    UUID userId,
    Long eventId,        // 추가 — Event 조회에 필요
    Integer amount
) {}
