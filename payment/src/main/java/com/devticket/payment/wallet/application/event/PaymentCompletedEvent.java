package com.devticket.payment.wallet.application.event;

import com.devticket.payment.payment.domain.enums.PaymentMethod;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentCompletedEvent(
    UUID orderId,
    UUID userId,
    UUID paymentId,
    PaymentMethod paymentMethod,
    int totalAmount,
    List<OrderItem> orderItems,
    Instant timestamp
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OrderItem(
        UUID eventId,
        int quantity
    ) {}
}
