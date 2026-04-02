package com.devticket.payment.refund.infrastructure.client.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record InternalEventInfoResponse(
    UUID eventId,
    UUID sellerId,
    String title,
    Integer price,
    String status,
    String category,
    Integer totalQuantity,
    Integer maxQuantity,
    Integer remainingQuantity,
    String eventDateTime,
    String saleStartAt,
    String saleEndAt
) {}
