package com.devticket.payment.refund.application.saga.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RefundStockRestoreEvent(
    UUID refundId,
    UUID orderId,
    List<Item> items,
    Instant timestamp
) {
    public record Item(UUID eventId, int quantity) {}
}