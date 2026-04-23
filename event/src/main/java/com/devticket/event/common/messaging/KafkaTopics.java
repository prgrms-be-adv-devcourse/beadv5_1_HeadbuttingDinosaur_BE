package com.devticket.event.common.messaging;

public final class KafkaTopics {

    private KafkaTopics() {}

    // Event 서비스가 소비하는 토픽
    public static final String PAYMENT_FAILED = "payment.failed";
    public static final String REFUND_COMPLETED = "refund.completed";
    public static final String REFUND_STOCK_RESTORE = "refund.stock.restore";

    // Event 서비스가 발행하는 토픽
    public static final String EVENT_FORCE_CANCELLED = "event.force-cancelled";
    public static final String EVENT_SALE_STOPPED = "event.sale-stopped";
    public static final String REFUND_STOCK_DONE = "refund.stock.done";
    public static final String REFUND_STOCK_FAILED = "refund.stock.failed";

    // action.log 전용 토픽 (AGENTS.md §6.10 별도 정책 — fire-and-forget, Outbox 미사용)
    public static final String ACTION_LOG = "action.log";
}
