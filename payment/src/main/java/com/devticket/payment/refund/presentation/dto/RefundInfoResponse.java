package com.devticket.payment.refund.presentation.dto;

import java.time.LocalDateTime;

public record RefundInfoResponse(
    String ticketId,
    String eventTitle,
    LocalDateTime eventDate,
    Integer originalAmount,
    Integer refundAmount,
    Integer refundRate,
    long dDay,
    boolean refundable,
    String paymentMethod  // "PG" or "WALLET"
) {}
