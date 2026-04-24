package com.devticket.commerce.common.messaging;

public final class KafkaTopics {

    private KafkaTopics() {}

    // Saga 흐름
    public static final String ORDER_CREATED       = "order.created";
    public static final String ORDER_CANCELLED     = "order.cancelled";
    public static final String STOCK_DEDUCTED      = "stock.deducted";
    public static final String STOCK_FAILED        = "stock.failed";
    public static final String PAYMENT_COMPLETED   = "payment.completed";
    public static final String PAYMENT_FAILED      = "payment.failed";
    public static final String TICKET_ISSUE_FAILED = "ticket.issue-failed";

    // 환불
    public static final String REFUND_COMPLETED = "refund.completed";

    // 이벤트 관리
    public static final String EVENT_FORCE_CANCELLED = "event.force-cancelled";
    public static final String EVENT_SALE_STOPPED    = "event.sale-stopped";

    // action.log (analytics — fire-and-forget, Bean 격리)
    public static final String ACTION_LOG = "action.log";

    // Refund Saga Orchestration
    public static final String REFUND_REQUESTED         = "refund.requested";
    public static final String REFUND_ORDER_CANCEL      = "refund.order.cancel";
    public static final String REFUND_ORDER_DONE        = "refund.order.done";
    public static final String REFUND_ORDER_FAILED      = "refund.order.failed";
    public static final String REFUND_TICKET_CANCEL     = "refund.ticket.cancel";
    public static final String REFUND_TICKET_DONE       = "refund.ticket.done";
    public static final String REFUND_TICKET_FAILED     = "refund.ticket.failed";
    public static final String REFUND_STOCK_RESTORE     = "refund.stock.restore";
    public static final String REFUND_STOCK_DONE        = "refund.stock.done";
    public static final String REFUND_STOCK_FAILED      = "refund.stock.failed";
    public static final String REFUND_ORDER_COMPENSATE  = "refund.order.compensate";
    public static final String REFUND_TICKET_COMPENSATE = "refund.ticket.compensate";
}
