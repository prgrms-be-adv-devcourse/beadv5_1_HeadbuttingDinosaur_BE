package com.devticket.payment.refund.domain.saga;

public enum SagaStep {
    ORDER_CANCELLING,
    TICKET_CANCELLING,
    STOCK_RESTORING,
    PG_CANCELLING,
    WALLET_RESTORING,
    COMPLETED,
    FAILED,
    COMPENSATING
}
