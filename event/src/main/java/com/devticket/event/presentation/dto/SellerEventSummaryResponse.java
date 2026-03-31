package com.devticket.event.presentation.dto;

import com.devticket.event.domain.enums.EventStatus;
import com.devticket.event.domain.model.Event;
import java.time.LocalDateTime;
import java.util.UUID;

public record SellerEventSummaryResponse(
    UUID eventId,
    String title,
    EventStatus status,
    LocalDateTime saleEndAt,
    Integer totalQuantity,
    Integer remainingQuantity,
    Integer soldQuantity,
    Integer cancelledQuantity,
    Integer price,
    Long totalSalesAmount
) {
    public static SellerEventSummaryResponse from(Event event) {
        int soldQuantity = event.getTotalQuantity() - event.getRemainingQuantity();
        return new SellerEventSummaryResponse(
            event.getEventId(),
            event.getTitle(),
            event.getStatus(),
            event.getSaleEndAt(),
            event.getTotalQuantity(),
            event.getRemainingQuantity(),
            soldQuantity,
            event.getCancelledQuantity(),
            event.getPrice(),
            (long) soldQuantity * event.getPrice()
        );
    }
}
