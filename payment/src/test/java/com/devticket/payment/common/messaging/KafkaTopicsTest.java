package com.devticket.payment.common.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("KafkaTopics 상수 검증")
class KafkaTopicsTest {

    @Nested
    @DisplayName("Saga 흐름 토픽")
    class SagaTopics {

        @Test
        void payment_completed_토픽명() {
            assertThat(KafkaTopics.PAYMENT_COMPLETED).isEqualTo("payment.completed");
        }

        @Test
        void payment_failed_토픽명() {
            assertThat(KafkaTopics.PAYMENT_FAILED).isEqualTo("payment.failed");
        }

        @Test
        void ticket_issue_failed_토픽명() {
            assertThat(KafkaTopics.TICKET_ISSUE_FAILED).isEqualTo("ticket.issue-failed");
        }
    }

    @Nested
    @DisplayName("환불 Orchestration 토픽")
    class RefundSagaTopics {

        @Test
        void refund_completed_토픽명() {
            assertThat(KafkaTopics.REFUND_COMPLETED).isEqualTo("refund.completed");
        }

        @Test
        void refund_requested_토픽명() {
            assertThat(KafkaTopics.REFUND_REQUESTED).isEqualTo("refund.requested");
        }

        @Test
        void refund_order_cancel_done_failed_토픽명() {
            assertThat(KafkaTopics.REFUND_ORDER_CANCEL).isEqualTo("refund.order.cancel");
            assertThat(KafkaTopics.REFUND_ORDER_DONE).isEqualTo("refund.order.done");
            assertThat(KafkaTopics.REFUND_ORDER_FAILED).isEqualTo("refund.order.failed");
        }

        @Test
        void refund_ticket_cancel_done_failed_토픽명() {
            assertThat(KafkaTopics.REFUND_TICKET_CANCEL).isEqualTo("refund.ticket.cancel");
            assertThat(KafkaTopics.REFUND_TICKET_DONE).isEqualTo("refund.ticket.done");
            assertThat(KafkaTopics.REFUND_TICKET_FAILED).isEqualTo("refund.ticket.failed");
        }

        @Test
        void refund_stock_restore_done_failed_토픽명() {
            assertThat(KafkaTopics.REFUND_STOCK_RESTORE).isEqualTo("refund.stock.restore");
            assertThat(KafkaTopics.REFUND_STOCK_DONE).isEqualTo("refund.stock.done");
            assertThat(KafkaTopics.REFUND_STOCK_FAILED).isEqualTo("refund.stock.failed");
        }

        @Test
        void refund_보상_토픽명() {
            assertThat(KafkaTopics.REFUND_ORDER_COMPENSATE).isEqualTo("refund.order.compensate");
            assertThat(KafkaTopics.REFUND_TICKET_COMPENSATE).isEqualTo("refund.ticket.compensate");
        }
    }

    @Nested
    @DisplayName("이벤트 관리 토픽")
    class EventTopics {

        @Test
        void event_force_cancelled_토픽명() {
            assertThat(KafkaTopics.EVENT_FORCE_CANCELLED).isEqualTo("event.force-cancelled");
        }

        @Test
        void event_sale_stopped_토픽명() {
            assertThat(KafkaTopics.EVENT_SALE_STOPPED).isEqualTo("event.sale-stopped");
        }
    }
}
