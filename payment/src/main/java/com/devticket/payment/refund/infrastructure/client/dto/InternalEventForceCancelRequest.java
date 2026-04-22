package com.devticket.payment.refund.infrastructure.client.dto;

public record InternalEventForceCancelRequest(
    String reason
) {}
