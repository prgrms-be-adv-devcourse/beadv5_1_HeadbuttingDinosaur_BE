package com.devticket.payment.refund.domain.saga;

public enum SagaStep {
    ORDER_CANCELLING,
    TICKET_CANCELLING,
    STOCK_RESTORING,
    COMPLETING,
    COMPLETED,
    COMPENSATING_TICKET,
    COMPENSATING_ORDER,
    FAILED
}
