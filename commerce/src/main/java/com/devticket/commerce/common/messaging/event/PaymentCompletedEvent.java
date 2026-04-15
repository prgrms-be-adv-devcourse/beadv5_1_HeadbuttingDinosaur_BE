package com.devticket.commerce.common.messaging.event;

import com.devticket.commerce.common.enums.PaymentMethod;
import java.time.Instant;
import java.util.UUID;

public record PaymentCompletedEvent(
        UUID orderId,
        UUID userId,
        UUID paymentId,
        PaymentMethod paymentMethod,
        int totalAmount,
        Instant timestamp
) {}
