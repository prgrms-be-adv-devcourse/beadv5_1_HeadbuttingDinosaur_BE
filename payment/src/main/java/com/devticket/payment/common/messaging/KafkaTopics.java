package com.devticket.payment.common.messaging;

public final class KafkaTopics {

    public static final String PAYMENT_COMPLETED = "payment.completed";
    public static final String REFUND_COMPLETED = "refund.completed";
    public static final String EVENT_FORCE_CANCELLED = "event.force-cancelled";
    public static final String EVENT_SALE_STOPPED = "event.sale-stopped";
    public static final String MEMBER_SUSPENDED = "member.suspended";
    public static final String ACTION_LOG = "action.log";

    private KafkaTopics() {
    }
}