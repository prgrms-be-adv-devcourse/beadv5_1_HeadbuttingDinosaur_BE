package com.devticket.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.devticket.payment.common.outbox.Outbox;
import com.devticket.payment.common.outbox.OutboxRepository;
import com.devticket.payment.common.outbox.OutboxStatus;
import com.devticket.payment.payment.domain.enums.PaymentMethod;
import com.devticket.payment.payment.domain.enums.PaymentStatus;
import com.devticket.payment.payment.domain.model.Payment;
import com.devticket.payment.payment.domain.repository.PaymentRepository;
import com.devticket.payment.wallet.domain.model.Wallet;
import com.devticket.payment.wallet.domain.model.WalletTransaction;
import com.devticket.payment.wallet.domain.repository.WalletRepository;
import com.devticket.payment.wallet.domain.repository.WalletTransactionRepository;
import com.devticket.payment.wallet.application.service.WalletService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payment 단독 통합 테스트 — Commerce/PG 의존 없이 Kafka + Outbox + 트랜잭션 + 스케줄러 검증.
 *
 * 검증 범위:
 * 1. PG 결제 승인 → Outbox INSERT → 스케줄러 → Kafka payment.completed 발행
 * 2. PG 결제 실패 → Outbox INSERT → 스케줄러 → Kafka payment.failed 발행
 * 3. Wallet 결제 → 원자적 잔액 차감 + Outbox INSERT → Kafka payment.completed 발행
 * 4. Wallet 충전 비관적 락 동시성 (일일 한도 체크 직렬화)
 * 5. Payment 상태 전이 가드 (canTransitionTo)
 */
@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
    partitions = 1,
    topics = {"payment.completed", "payment.failed", "refund.completed",
              "event.force-cancelled", "event.sale-stopped"}
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PaymentKafkaIntegrationTest {

    @Autowired private PaymentRepository paymentRepository;
    @Autowired private OutboxRepository outboxRepository;
    @Autowired private WalletRepository walletRepository;
    @Autowired private WalletTransactionRepository walletTransactionRepository;
    @Autowired private WalletService walletService;
    @Autowired private ObjectMapper objectMapper;

    private static final List<ConsumerRecord<String, String>> completedRecords =
        Collections.synchronizedList(new ArrayList<>());
    private static final List<ConsumerRecord<String, String>> failedRecords =
        Collections.synchronizedList(new ArrayList<>());

    @KafkaListener(topics = "payment.completed", groupId = "test-completed")
    void listenCompleted(ConsumerRecord<String, String> record) {
        completedRecords.add(record);
    }

    @KafkaListener(topics = "payment.failed", groupId = "test-failed")
    void listenFailed(ConsumerRecord<String, String> record) {
        failedRecords.add(record);
    }

    @BeforeEach
    void setUp() {
        completedRecords.clear();
        failedRecords.clear();
    }

    // =========================================================
    // 1. Outbox → 스케줄러 → Kafka 발행 E2E
    // =========================================================

    @Nested
    @DisplayName("Outbox → Kafka 발행 E2E")
    class OutboxToKafkaTest {

        @Test
        @DisplayName("payment.completed Outbox 레코드 → 스케줄러가 Kafka로 발행")
        void payment_completed_outbox_to_kafka() throws Exception {
            // given: Payment 생성 + 승인 + Outbox 직접 INSERT (Commerce/PG 우회)
            UUID orderId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            Payment payment = Payment.create(orderId, userId, PaymentMethod.PG, 50_000);
            payment.approve("test-payment-key");
            paymentRepository.save(payment);

            String payload = objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
                put("orderId", orderId.toString());
                put("userId", userId.toString());
                put("paymentId", payment.getPaymentId().toString());
                put("paymentMethod", "PG");
                put("totalAmount", 50_000);
                put("timestamp", java.time.Instant.now().toString());
            }});

            Outbox outbox = Outbox.create(
                payment.getPaymentId().toString(),
                "payment.completed",
                "payment.completed",
                orderId.toString(),
                payload
            );
            outboxRepository.save(outbox);

            // then: 스케줄러가 3초 내에 발행, Kafka에서 수신 확인
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                assertThat(completedRecords).isNotEmpty();
                String value = completedRecords.get(completedRecords.size() - 1).value();
                JsonNode node = objectMapper.readTree(value);
                JsonNode payloadNode = objectMapper.readTree(node.get("payload").asText());
                assertThat(payloadNode.get("orderId").asText()).isEqualTo(orderId.toString());
                assertThat(payloadNode.get("paymentMethod").asText()).isEqualTo("PG");
            });

            // Outbox 상태 SENT 확인
            Outbox sent = outboxRepository.findById(outbox.getId()).orElseThrow();
            assertThat(sent.getStatus()).isEqualTo(OutboxStatus.SENT);
        }

        @Test
        @DisplayName("payment.failed Outbox 레코드 → 스케줄러가 Kafka로 발행")
        void payment_failed_outbox_to_kafka() throws Exception {
            // given
            UUID orderId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            Payment payment = Payment.create(orderId, userId, PaymentMethod.PG, 30_000);
            payment.fail("잔액 부족");
            paymentRepository.save(payment);

            String payload = objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
                put("orderId", orderId.toString());
                put("userId", userId.toString());
                put("reason", "잔액 부족");
                put("timestamp", java.time.Instant.now().toString());
            }});

            Outbox outbox = Outbox.create(
                payment.getPaymentId().toString(),
                "payment.failed",
                "payment.failed",
                orderId.toString(),
                payload
            );
            outboxRepository.save(outbox);

            // then
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                assertThat(failedRecords).isNotEmpty();
                String value = failedRecords.get(failedRecords.size() - 1).value();
                JsonNode node = objectMapper.readTree(value);
                JsonNode payloadNode = objectMapper.readTree(node.get("payload").asText());
                assertThat(payloadNode.get("reason").asText()).isEqualTo("잔액 부족");
            });

            Outbox sent = outboxRepository.findById(outbox.getId()).orElseThrow();
            assertThat(sent.getStatus()).isEqualTo(OutboxStatus.SENT);
        }
    }

    // =========================================================
    // 2. Wallet 결제 → 원자적 차감 + Outbox + Kafka
    // =========================================================

    @Nested
    @DisplayName("Wallet 결제 → Outbox → Kafka")
    class WalletPaymentTest {

        @Test
        @DisplayName("Wallet 결제 성공 → 잔액 차감 + Outbox INSERT + Kafka 발행")
        void wallet_payment_outbox_to_kafka() throws Exception {
            // given: Wallet + Payment(READY) 준비
            UUID userId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();

            Wallet wallet = Wallet.create(userId);
            wallet.charge(100_000);
            walletRepository.save(wallet);

            Payment payment = Payment.create(orderId, userId, PaymentMethod.WALLET, 30_000);
            paymentRepository.save(payment);

            // when: WalletService.processWalletPayment 호출 (Commerce 우회, 직접 호출)
            walletService.processWalletPayment(userId, orderId, 30_000);

            // then: 잔액 차감 확인
            Wallet updated = walletRepository.findByUserId(userId).orElseThrow();
            assertThat(updated.getBalance()).isEqualTo(70_000);

            // Payment 상태 확인
            Payment completedPayment = paymentRepository.findByOrderId(orderId).orElseThrow();
            assertThat(completedPayment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);

            // Outbox INSERT 확인
            List<Outbox> outboxes = outboxRepository.findPendingForRetry(
                OutboxStatus.PENDING, java.time.Instant.now().plusSeconds(60));
            assertThat(outboxes).anyMatch(o ->
                "payment.completed".equals(o.getEventType())
                && o.getPartitionKey().equals(orderId.toString())
            );

            // Kafka 발행 확인 (스케줄러 대기)
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                assertThat(completedRecords).anyMatch(r -> {
                    try {
                        JsonNode node = objectMapper.readTree(r.value());
                        JsonNode p = objectMapper.readTree(node.get("payload").asText());
                        return orderId.toString().equals(p.get("orderId").asText())
                            && "WALLET".equals(p.get("paymentMethod").asText());
                    } catch (Exception e) { return false; }
                });
            });
        }
    }

    // =========================================================
    // 3. Payment 상태 전이 가드
    // =========================================================

    @Nested
    @DisplayName("Payment 상태 전이 가드")
    class StatusTransitionTest {

        @Test
        @DisplayName("READY → SUCCESS 허용")
        void ready_to_success() {
            Payment payment = Payment.create(UUID.randomUUID(), UUID.randomUUID(), PaymentMethod.PG, 10_000);
            assertThat(payment.canTransitionTo(PaymentStatus.SUCCESS)).isTrue();
            payment.approve("key");
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        }

        @Test
        @DisplayName("SUCCESS → REFUNDED 허용")
        void success_to_refunded() {
            Payment payment = Payment.create(UUID.randomUUID(), UUID.randomUUID(), PaymentMethod.PG, 10_000);
            payment.approve("key");
            assertThat(payment.canTransitionTo(PaymentStatus.REFUNDED)).isTrue();
            payment.refund();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        }

        @Test
        @DisplayName("FAILED → SUCCESS 금지 (종단 상태)")
        void failed_to_success_blocked() {
            Payment payment = Payment.create(UUID.randomUUID(), UUID.randomUUID(), PaymentMethod.PG, 10_000);
            payment.fail("test");
            assertThat(payment.canTransitionTo(PaymentStatus.SUCCESS)).isFalse();
            org.junit.jupiter.api.Assertions.assertThrows(
                com.devticket.payment.payment.domain.exception.PaymentException.class,
                () -> payment.approve("key")
            );
        }

        @Test
        @DisplayName("SUCCESS → FAILED 금지")
        void success_to_failed_blocked() {
            Payment payment = Payment.create(UUID.randomUUID(), UUID.randomUUID(), PaymentMethod.PG, 10_000);
            payment.approve("key");
            assertThat(payment.canTransitionTo(PaymentStatus.FAILED)).isFalse();
            org.junit.jupiter.api.Assertions.assertThrows(
                com.devticket.payment.payment.domain.exception.PaymentException.class,
                () -> payment.fail("test")
            );
        }
    }

    // =========================================================
    // 4. Wallet 충전 비관적 락 동시성
    // =========================================================

    @Nested
    @DisplayName("Wallet 충전 비관적 락 동시성")
    class WalletChargeConcurrencyTest {

        @Test
        @DisplayName("동시 충전 요청 시 일일 한도 초과 방지 (비관적 락 직렬화)")
        void concurrent_charge_daily_limit() throws Exception {
            // given: 일일 한도 1,000,000원, 50,000원씩 25번 동시 요청 = 1,250,000원
            // 20번만 성공해야 함 (20 * 50,000 = 1,000,000)
            UUID userId = UUID.randomUUID();
            Wallet wallet = Wallet.create(userId);
            walletRepository.save(wallet);

            int threadCount = 25;
            int chargeAmount = 50_000;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            // when: 동시 충전 요청
            for (int i = 0; i < threadCount; i++) {
                final String idempotencyKey = "concurrent-charge-" + i;
                executor.submit(() -> {
                    try {
                        walletService.charge(
                            userId,
                            new com.devticket.payment.wallet.presentation.dto.WalletChargeRequest(chargeAmount),
                            idempotencyKey
                        );
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
            executor.shutdown();

            // then: 일일 한도(1,000,000) 이내만 성공
            assertThat(successCount.get()).isLessThanOrEqualTo(20);
            assertThat(failCount.get()).isGreaterThanOrEqualTo(5);
        }
    }
}
