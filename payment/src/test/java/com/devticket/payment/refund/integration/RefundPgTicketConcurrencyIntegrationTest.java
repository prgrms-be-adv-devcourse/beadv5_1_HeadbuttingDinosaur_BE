package com.devticket.payment.refund.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.devticket.payment.payment.domain.enums.PaymentMethod;
import com.devticket.payment.payment.domain.model.Payment;
import com.devticket.payment.payment.domain.repository.PaymentRepository;
import com.devticket.payment.payment.infrastructure.client.CommerceInternalClient;
import com.devticket.payment.payment.infrastructure.client.dto.InternalOrderItemInfoResponse;
import com.devticket.payment.refund.application.service.RefundService;
import com.devticket.payment.refund.domain.exception.RefundErrorCode;
import com.devticket.payment.refund.domain.exception.RefundException;
import com.devticket.payment.refund.domain.model.OrderRefund;
import com.devticket.payment.refund.domain.enums.RefundTicketStatus;
import com.devticket.payment.refund.domain.repository.OrderRefundRepository;
import com.devticket.payment.refund.domain.repository.RefundRepository;
import com.devticket.payment.refund.domain.repository.RefundTicketRepository;
import com.devticket.payment.refund.infrastructure.client.EventInternalClient;
import com.devticket.payment.refund.infrastructure.client.dto.InternalEventInfoResponse;
import com.devticket.payment.refund.presentation.dto.PgRefundRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * refundPgTicket 동시성 통합 테스트
 *
 * 실제 PostgreSQL(Testcontainers) + partial unique index 검증.
 * H2는 partial unique index 미지원으로 PostgreSQL 컨테이너 사용.
 * CommerceInternalClient / EventInternalClient 만 Mock (외부 HTTP 호출 차단).
 */
@SpringBootTest
@Testcontainers
@Disabled("ci 통과 시간이 오래 걸림으로 테스트 임시 비활성화")
class RefundPgTicketConcurrencyIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // 컨테이너 시작 후, Spring 컨텍스트 초기화 전에 스키마 생성 (withInitScript가 TC 2.x에서 깨짐)
        try (java.sql.Connection conn = java.sql.DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE SCHEMA IF NOT EXISTS payment");
            stmt.execute("CREATE SCHEMA IF NOT EXISTS refund");
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS payment.shedlock (
                    name       VARCHAR(64)  NOT NULL PRIMARY KEY,
                    lock_until TIMESTAMP    NOT NULL,
                    locked_at  TIMESTAMP    NOT NULL,
                    locked_by  VARCHAR(255) NOT NULL
                )""");
        } catch (Exception e) {
            throw new RuntimeException("PostgreSQL 스키마 초기화 실패", e);
        }

        // URL에 이미 ?loggerLevel=OFF 가 붙어 있으므로 currentSchema는 & 로 연결
        registry.add("spring.datasource.url",
            () -> postgres.getJdbcUrl() + "&currentSchema=payment");
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "30");
        registry.add("spring.datasource.hikari.connection-init-sql",
            () -> "SET search_path TO payment");
        registry.add("spring.jpa.database-platform",
            () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.properties.hibernate.dialect",
            () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "payment");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.show-sql", () -> "false");
        registry.add("spring.main.allow-bean-definition-overriding", () -> "true");
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9093");
        registry.add("spring.kafka.consumer.group-id", () -> "devticket-payment");
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        registry.add("spring.kafka.consumer.key-deserializer",
            () -> "org.apache.kafka.common.serialization.StringDeserializer");
        registry.add("spring.kafka.consumer.value-deserializer",
            () -> "org.apache.kafka.common.serialization.StringDeserializer");
        registry.add("spring.kafka.producer.key-serializer",
            () -> "org.apache.kafka.common.serialization.StringSerializer");
        registry.add("spring.kafka.producer.value-serializer",
            () -> "org.apache.kafka.common.serialization.StringSerializer");
        registry.add("kafka-producer.max-block-ms", () -> "3000");
        registry.add("kafka-producer.request-timeout-ms", () -> "5000");
        registry.add("kafka-producer.delivery-timeout-ms", () -> "8000");
        registry.add("kafka-producer.send-timeout-ms", () -> "10000");
        registry.add("jwt.secret-key", () -> "test-jwt-secret-key");
        registry.add("jwt.access-token-ttl", () -> "1800000");
        registry.add("jwt.refresh-token-ttl", () -> "604800000");
        registry.add("internal.commerce.base-url", () -> "http://localhost:8085");
        registry.add("internal.event.base-url", () -> "http://localhost:8085");
        registry.add("pg.toss.base-url", () -> "https://api.tosspayments.com");
        registry.add("pg.toss.secret-key", () -> "secret-key-dummy");
        registry.add("server.port", () -> "8085");
    }

    @Autowired private RefundService refundService;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private OrderRefundRepository orderRefundRepository;
    @Autowired private RefundRepository refundRepository;
    @Autowired private RefundTicketRepository refundTicketRepository;

    @MockitoBean private CommerceInternalClient commerceInternalClient;
    @MockitoBean private EventInternalClient eventInternalClient;

    private UUID userId;
    private String ticketId;
    private Payment payment;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        ticketId = UUID.randomUUID().toString();

        payment = Payment.create(UUID.randomUUID(), userId, PaymentMethod.PG, 50_000);
        payment.approve("payment-key-test");
        paymentRepository.save(payment);

        orderRefundRepository.save(OrderRefund.create(
            payment.getOrderId(), userId, payment.getPaymentId(),
            PaymentMethod.PG, 50_000, 1
        ));

        InternalEventInfoResponse eventInfo = new InternalEventInfoResponse(
            UUID.randomUUID(), UUID.randomUUID(), "테스트 이벤트", 50_000,
            "ACTIVE", "CONCERT", 100, 4, 50,
            LocalDateTime.now().plusDays(10).toString(),
            LocalDateTime.now().minusDays(5).toString(),
            LocalDateTime.now().plusDays(5).toString()
        );

        given(commerceInternalClient.getOrderItemInfoByTicketId(ticketId))
            .willReturn(new InternalOrderItemInfoResponse(
                UUID.fromString(ticketId), payment.getOrderId(), userId,
                eventInfo.eventId(), 50_000
            ));
        given(eventInternalClient.getEventInfo(any())).willReturn(eventInfo);
    }

    // =========================================================
    // 테스트 1: 동일 ticketId 동시 10건 — RefundTicket 1건만 생성
    //
    // 공격: 환불 버튼 연타 / 네트워크 재전송 시뮬레이션
    // 방어 1차: existsByTicketIdAndStatusIn(ACTIVE, COMPLETED) 선행 체크 → REFUND_ALREADY_IN_PROGRESS(409)
    // 방어 2차: uk_refund_ticket_active partial unique index + DataIntegrityViolationException catch
    // 검증: 성공 1건, 나머지 REFUND_ALREADY_IN_PROGRESS, DB Refund·RefundTicket 각 1건
    // =========================================================
    @Test
    @DisplayName("동일 ticketId로 동시 10건 요청 시 RefundTicket·Refund는 각 1건만 생성된다")
    void 동일_ticketId_동시요청_1건만_생성() throws InterruptedException {
        int threadCount = 10;
        PgRefundRequest request = new PgRefundRequest("단순 변심");

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger alreadyInProgressCount = new AtomicInteger(0);
        AtomicInteger otherErrorCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    refundService.refundPgTicket(userId, ticketId, request);
                    successCount.incrementAndGet();
                } catch (RefundException e) {
                    if (e.getErrorCode() == RefundErrorCode.REFUND_ALREADY_IN_PROGRESS) {
                        alreadyInProgressCount.incrementAndGet();
                    } else {
                        otherErrorCount.incrementAndGet();
                        System.out.println("  기타 RefundException: " + e.getErrorCode());
                    }
                } catch (Exception e) {
                    otherErrorCount.incrementAndGet();
                    System.out.println("  기타 에러: " + e.getClass().getSimpleName() + " — " + e.getMessage());
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        done.await();
        executor.shutdown();

        long refundCount = refundRepository
            .findByUserId(userId, PageRequest.of(0, 100))
            .getTotalElements();
        boolean ticketExists = refundTicketRepository.existsByTicketIdAndStatusIn(
            UUID.fromString(ticketId),
            List.of(RefundTicketStatus.ACTIVE, RefundTicketStatus.COMPLETED));

        System.out.println("========== refundPgTicket 동시성 테스트 결과 ==========");
        System.out.println("성공: " + successCount.get() + "건");
        System.out.println("REFUND_ALREADY_IN_PROGRESS: " + alreadyInProgressCount.get() + "건");
        System.out.println("기타 에러: " + otherErrorCount.get() + "건");
        System.out.println("DB Refund 수: " + refundCount + "건");
        System.out.println("DB RefundTicket 존재: " + ticketExists);

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(alreadyInProgressCount.get()).isEqualTo(threadCount - 1);
        assertThat(otherErrorCount.get()).isEqualTo(0);
        assertThat(refundCount).isEqualTo(1);
        assertThat(ticketExists).isTrue();
    }
}
