package com.devticket.payment.refund.domain.saga;

public enum SagaStatus {
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    COMPENSATING
}
