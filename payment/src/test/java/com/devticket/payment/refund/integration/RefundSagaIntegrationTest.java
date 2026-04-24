package com.devticket.payment.refund.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.devticket.payment.common.messaging.KafkaTopics;
import com.devticket.payment.common.outbox.OutboxService;
import com.devticket.payment.payment.domain.enums.PaymentMethod;
import com.devticket.payment.payment.domain.model.Payment;
import com.devticket.payment.payment.domain.repository.PaymentRepository;
import com.devticket.payment.refund.application.saga.event.RefundOrderDoneEvent;
import com.devticket.payment.refund.application.saga.event.RefundRequestedEvent;
import com.devticket.payment.refund.application.saga.event.RefundStockDoneEvent;
import com.devticket.payment.refund.application.saga.event.RefundStockRestoreEvent;
import com.devticket.payment.refund.application.saga.event.RefundTicketCancelEvent;
import com.devticket.payment.refund.application.saga.event.RefundTicketDoneEvent;
import com.devticket.payment.refund.application.saga.event.RefundTicketFailedEvent;
import com.devticket.payment.refund.application.saga.event.RefundOrderCancelEvent;
import com.devticket.payment.refund.domain.enums.OrderRefundStatus;
import com.devticket.payment.refund.domain.model.OrderRefund;
import com.devticket.payment.refund.domain.model.Refund;
import com.devticket.payment.refund.domain.model.SagaState;
import com.devticket.payment.refund.domain.repository.OrderRefundRepository;
import com.devticket.payment.refund.domain.repository.RefundRepository;
import com.devticket.payment.refund.domain.repository.SagaStateRepository;
import com.devticket.payment.refund.domain.saga.SagaStatus;
import com.devticket.payment.refund.domain.saga.SagaStep;
import com.devticket.payment.wallet.domain.model.Wallet;
import com.devticket.payment.wallet.domain.repository.WalletRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Refund Saga E2E 통합 테스트 — Payment Orchestrator + Outbox + Kafka + Mock Commerce/Event Consumer 연동 검증.
 * <p>
 * Commerce/Event 서비스가 없으므로 같은 컨테이너 내부에서 Mock @KafkaListener 가 refund.order.cancel / refund.ticket.cancel /
 * refund.stock.restore 수신 시 대응하는 done / failed 이벤트를 Outbox 로 회신한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
    partitions = 1,
    topics = {
        "refund.requested",
        "refund.order.cancel", "refund.order.done", "refund.order.failed", "refund.order.compensate",
        "refund.ticket.cancel", "refund.ticket.done", "refund.ticket.failed", "refund.ticket.compensate",
        "refund.stock.restore", "refund.stock.done", "refund.stock.failed",
        "refund.completed"
    }
)
@Import(RefundSagaIntegrationTest.MockSagaPartnerConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RefundSagaIntegrationTest {

    @Autowired
    PaymentRepository paymentRepository;
    @Autowired
    RefundRepository refundRepository;
    @Autowired
    OrderRefundRepository orderRefundRepository;
    @Autowired
    SagaStateRepository sagaStateRepository;
    @Autowired
    OutboxService outboxService;
    @Autowired
    MockSagaPartner mockPartner;
    @Autowired
    WalletRepository walletRepository;
    @Autowired
    TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        mockPartner.reset();
    }

    @Test
    @DisplayName("Happy path — refund.requested → order.done → ticket.done → stock.done → refund.completed")
    void happyPath() {
        Payment payment = createAndSaveWalletPayment();
        walletRepository.save(Wallet.create(payment.getUserId()));
        OrderRefund ledger = orderRefundRepository.save(OrderRefund.create(
            payment.getOrderId(), payment.getUserId(), payment.getPaymentId(),
            PaymentMethod.WALLET, payment.getAmount(), 1
        ));
        Refund refund = refundRepository.save(Refund.create(
            ledger.getOrderRefundId(),
            payment.getOrderId(),
            payment.getPaymentId(),
            payment.getUserId(),
            payment.getAmount(),
            100
        ));

        RefundRequestedEvent requested = new RefundRequestedEvent(
            refund.getRefundId(),
            ledger.getOrderRefundId(),
            payment.getOrderId(),
            payment.getUserId(),
            payment.getPaymentId(),
            payment.getPaymentMethod(),
            List.of(UUID.randomUUID()),
            payment.getAmount(),
            100,
            false,
            "integration-test",
            Instant.now()
        );

        publishOutbox(
            refund.getRefundId().toString(),
            payment.getOrderId().toString(),
            KafkaTopics.REFUND_REQUESTED,
            requested
        );

        await().atMost(Duration.ofSeconds(90)).untilAsserted(() -> {
            SagaState state = sagaStateRepository.findByRefundId(refund.getRefundId()).orElse(null);
            assertThat(state).isNotNull();
            assertThat(state.getStatus()).isEqualTo(SagaStatus.COMPLETED);
            assertThat(state.getCurrentStep()).isEqualTo(SagaStep.COMPLETED);
        });

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            OrderRefund after = orderRefundRepository.findByOrderId(payment.getOrderId()).orElseThrow();
            assertThat(after.getStatus()).isEqualTo(OrderRefundStatus.FULL);
        });
    }

    @Test
    @DisplayName("보상 경로 — ticket.cancel 실패 주입 시 SagaState 가 COMPENSATING 로 전이")
    void compensatePath() {
        mockPartner.failOnTicketCancel.set(true);

        Payment payment = createAndSavePgPayment();
        OrderRefund ledger = orderRefundRepository.save(OrderRefund.create(
            payment.getOrderId(), payment.getUserId(), payment.getPaymentId(),
            PaymentMethod.PG, payment.getAmount(), 1
        ));
        Refund refund = refundRepository.save(Refund.create(
            ledger.getOrderRefundId(),
            payment.getOrderId(),
            payment.getPaymentId(),
            payment.getUserId(),
            payment.getAmount(),
            100
        ));

        RefundRequestedEvent requested = new RefundRequestedEvent(
            refund.getRefundId(), ledger.getOrderRefundId(), payment.getOrderId(),
            payment.getUserId(), payment.getPaymentId(), payment.getPaymentMethod(),
            List.of(UUID.randomUUID()), payment.getAmount(), 100, false,
            "integration-test", Instant.now()
        );
        publishOutbox(
            refund.getRefundId().toString(),
            payment.getOrderId().toString(),
            KafkaTopics.REFUND_REQUESTED,
            requested
        );

        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            SagaState state = sagaStateRepository.findByRefundId(refund.getRefundId()).orElse(null);
            assertThat(state).isNotNull();
            assertThat(state.getStatus()).isEqualTo(SagaStatus.COMPENSATING);
        });
    }

    // ---------- helpers ----------

    protected void publishOutbox(String aggregateId, String partitionKey, String topic, Object payload) {
        transactionTemplate.executeWithoutResult(status ->
            outboxService.save(aggregateId, partitionKey, topic, topic, payload)
        );
    }

    private Payment createAndSavePgPayment() {
        Payment p = Payment.create(UUID.randomUUID(), UUID.randomUUID(), PaymentMethod.PG, 10_000);
        p.approve("pk-test");
        return paymentRepository.save(p);
    }

    private Payment createAndSaveWalletPayment() {
        Payment p = Payment.create(UUID.randomUUID(), UUID.randomUUID(), PaymentMethod.WALLET, 10_000);
        p.approve("wallet-" + p.getPaymentId());
        return paymentRepository.save(p);
    }

    // =====================================================================
    // Mock Commerce/Event partner — TestConfiguration 으로 Bean 등록
    // =====================================================================
    @TestConfiguration
    static class MockSagaPartnerConfig {

        @Bean
        MockSagaPartner mockSagaPartner(
            OutboxService outboxService,
            ObjectMapper objectMapper,
            TransactionTemplate transactionTemplate
        ) {
            return new MockSagaPartner(outboxService, objectMapper, transactionTemplate);
        }
    }

    static class MockSagaPartner {

        final AtomicBoolean failOnTicketCancel = new AtomicBoolean(false);

        private final OutboxService outboxService;
        private final ObjectMapper objectMapper;
        private final TransactionTemplate transactionTemplate;

        MockSagaPartner(
            OutboxService outboxService,
            ObjectMapper objectMapper,
            TransactionTemplate transactionTemplate
        ) {
            this.outboxService = outboxService;
            this.objectMapper = objectMapper;
            this.transactionTemplate = transactionTemplate;
        }

        void reset() {
            failOnTicketCancel.set(false);
        }

        @KafkaListener(
            topics = KafkaTopics.REFUND_ORDER_CANCEL,
            groupId = "mock-partner-order-cancel",
            properties = "auto.offset.reset=earliest"
        )
        public void onOrderCancel(ConsumerRecord<String, String> record) throws Exception {
            RefundOrderCancelEvent event = extractPayload(record, RefundOrderCancelEvent.class);

            savePartnerOutbox(
                event.refundId(),
                event.orderId(),
                KafkaTopics.REFUND_ORDER_DONE,
                new RefundOrderDoneEvent(event.refundId(), event.orderId(), Instant.now())
            );
        }

        @KafkaListener(
            topics = KafkaTopics.REFUND_TICKET_CANCEL,
            groupId = "mock-partner-ticket-cancel",
            properties = "auto.offset.reset=earliest"
        )
        public void onTicketCancel(ConsumerRecord<String, String> record) throws Exception {
            RefundTicketCancelEvent event = extractPayload(record, RefundTicketCancelEvent.class);

            if (failOnTicketCancel.get()) {
                savePartnerOutbox(
                    event.refundId(),
                    event.orderId(),
                    KafkaTopics.REFUND_TICKET_FAILED,
                    new RefundTicketFailedEvent(event.refundId(), event.orderId(), "mock-fail", Instant.now())
                );
                return;
            }
            savePartnerOutbox(
                event.refundId(),
                event.orderId(),
                KafkaTopics.REFUND_TICKET_DONE,
                new RefundTicketDoneEvent(event.refundId(), event.orderId(),
                    List.of(UUID.randomUUID()),
                    List.of(new RefundTicketDoneEvent.Item(UUID.randomUUID(), 1)),
                    Instant.now())
            );
        }

        @KafkaListener(
            topics = KafkaTopics.REFUND_STOCK_RESTORE,
            groupId = "mock-partner-stock-restore",
            properties = "auto.offset.reset=earliest"
        )
        public void onStockRestore(ConsumerRecord<String, String> record) throws Exception {
            RefundStockRestoreEvent event = extractPayload(record, RefundStockRestoreEvent.class);
            savePartnerOutbox(
                event.refundId(),
                event.orderId(),
                KafkaTopics.REFUND_STOCK_DONE,
                new RefundStockDoneEvent(event.refundId(), event.orderId(), Instant.now())
            );
        }

        private void savePartnerOutbox(UUID refundId, UUID orderId, String topic, Object payload) {
            transactionTemplate.executeWithoutResult(status ->
                outboxService.save(
                    refundId.toString(),
                    orderId.toString(),
                    topic,
                    topic,
                    payload
                )
            );
        }

        private <T> T extractPayload(ConsumerRecord<String, String> record, Class<T> payloadType) throws Exception {
            String rootJson = record.value();
            String payloadJson = objectMapper.readTree(rootJson).get("payload").asText();
            return objectMapper.readValue(payloadJson, payloadType);
        }
    }
}
