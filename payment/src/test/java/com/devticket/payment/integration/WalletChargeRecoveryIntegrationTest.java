package com.devticket.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.devticket.payment.payment.infrastructure.external.PgPaymentClient;
import com.devticket.payment.payment.infrastructure.external.dto.TossPaymentStatusResponse;
import com.devticket.payment.wallet.application.scheduler.WalletChargeRecoveryScheduler;
import com.devticket.payment.wallet.application.service.WalletService;
import com.devticket.payment.wallet.domain.enums.WalletChargeStatus;
import com.devticket.payment.wallet.domain.model.Wallet;
import com.devticket.payment.wallet.domain.model.WalletCharge;
import com.devticket.payment.wallet.domain.model.WalletTransaction;
import com.devticket.payment.wallet.domain.repository.WalletChargeRepository;
import com.devticket.payment.wallet.domain.repository.WalletRepository;
import com.devticket.payment.wallet.domain.repository.WalletTransactionRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Wallet 사후 보정 통합 테스트.
 *
 * 실제 PostgreSQL(Testcontainers) 사용 — readOnly 트랜잭션에서의 SELECT FOR UPDATE 거부(SQLState 25006)는
 * H2 PostgreSQL 호환 모드가 정확히 흉내내지 않으므로 PG 컨테이너로만 회귀를 잡을 수 있다.
 *
 * 검증 항목:
 *  A1) readOnly TX 회귀 가드
 *  A2) 스케줄러 → DB end-to-end (PENDING 3건 → COMPLETED + 잔액 반영)
 *  A3) PG 조회 예외 시 PROCESSING → PENDING 원복
 *  A4) PG DONE + WalletTransaction 이미 존재 시 잔액 중복 반영 없음
 *  A5) 같은 chargeId 동시 처리 시 한쪽만 PROCESSING 선점 (FOR UPDATE 락)
 */
@SpringBootTest
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class WalletChargeRecoveryIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
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

    @Autowired private WalletChargeRecoveryScheduler scheduler;
    @Autowired private WalletService walletService;
    @Autowired private WalletRepository walletRepository;
    @Autowired private WalletChargeRepository walletChargeRepository;
    @Autowired private WalletTransactionRepository walletTransactionRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean private PgPaymentClient pgPaymentClient;

    private UUID userId;
    private Wallet wallet;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        wallet = walletRepository.save(Wallet.create(userId));
    }

    // =====================================================================
    // A1: readOnly TX 회귀 가드
    //
    // WalletServiceImpl 클래스 레벨 @Transactional(readOnly=true) 컨텍스트가 새어
    // SELECT FOR NO KEY UPDATE 가 거부되면 SQLState 25006 으로 InvalidDataAccessApiUsageException 발생.
    // 본 테스트는 호출이 정상 종료되고 상태가 COMPLETED 로 전이됨을 검증한다.
    // =====================================================================
    @Test
    @DisplayName("[A1] readOnly TX 컨텍스트 누수 없음 — recoverStalePendingCharge 가 SQLState 25006 없이 성공")
    void readOnly_TX_회귀_가드() {
        // given
        WalletCharge pending = walletChargeRepository.save(
            WalletCharge.create(wallet.getId(), userId, 10_000, "key-" + UUID.randomUUID()));
        given(pgPaymentClient.findPaymentByOrderId(anyString()))
            .willReturn(Optional.of(new TossPaymentStatusResponse(
                "pk-a1", pending.getChargeId().toString(), "DONE", 10_000, "2024-01-01T00:00:00")));

        // when & then: 회귀 발생 시 InvalidDataAccessApiUsageException 던짐
        assertThatCode(() -> walletService.recoverStalePendingCharge(pending.getChargeId()))
            .doesNotThrowAnyException();

        WalletCharge after = walletChargeRepository.findByChargeId(pending.getChargeId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(WalletChargeStatus.COMPLETED);
    }

    // =====================================================================
    // A2: 스케줄러 → DB end-to-end
    // =====================================================================
    @Test
    @DisplayName("[A2] 스케줄러 → 3건 모두 COMPLETED + 잔액 반영 + WalletTransaction 3건 생성")
    void 스케줄러_E2E_DONE_반영() {
        // given: 35분 전 PENDING 3건
        List<UUID> chargeIds = create3StalePending(10_000);
        chargeIds.forEach(id ->
            given(pgPaymentClient.findPaymentByOrderId(id.toString()))
                .willReturn(Optional.of(new TossPaymentStatusResponse(
                    "pk-" + id, id.toString(), "DONE", 10_000, "2024-01-01T00:00:00"))));

        // when
        scheduler.recoverStalePendingCharges();

        // then
        chargeIds.forEach(id -> assertThat(
            walletChargeRepository.findByChargeId(id).orElseThrow().getStatus())
            .isEqualTo(WalletChargeStatus.COMPLETED));

        Wallet w = walletRepository.findByUserId(userId).orElseThrow();
        assertThat(w.getBalance()).isEqualTo(30_000);

        chargeIds.forEach(id -> assertThat(
            walletTransactionRepository.existsByTransactionKey("CHARGE:pk-" + id)).isTrue());
    }

    // =====================================================================
    // A3: PG 조회 예외 → 원복
    // =====================================================================
    @Test
    @DisplayName("[A3] PG 조회 예외 시 PENDING 으로 원복하여 다음 주기 재시도 가능")
    void PG_조회_예외_원복() {
        // given
        WalletCharge pending = walletChargeRepository.save(
            WalletCharge.create(wallet.getId(), userId, 10_000, "key-" + UUID.randomUUID()));
        given(pgPaymentClient.findPaymentByOrderId(anyString()))
            .willThrow(new RuntimeException("PG timeout"));

        // when
        walletService.recoverStalePendingCharge(pending.getChargeId());

        // then: 잔액 변동 없음, 상태 PENDING
        WalletCharge after = walletChargeRepository.findByChargeId(pending.getChargeId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(WalletChargeStatus.PENDING);

        Wallet w = walletRepository.findByUserId(userId).orElseThrow();
        assertThat(w.getBalance()).isZero();
    }

    // =====================================================================
    // A4: PG DONE + WalletTransaction 이미 존재 → 잔액 중복 반영 방지
    // =====================================================================
    @Test
    @DisplayName("[A4] PG DONE 인데 거래기록 이미 존재 시 잔액 중복 반영 없음")
    void DONE_거래기록_존재_중복_미반영() {
        // given: 이전 부분 실패로 거래기록은 이미 존재 + 잔액도 이미 반영된 상태
        String paymentKey = "pk-already-recorded";
        String txKey = "CHARGE:" + paymentKey;
        jdbcTemplate.update("UPDATE payment.wallet SET balance = 60000 WHERE user_id = ?", userId);
        WalletTransaction preExist = WalletTransaction.createCharge(
            wallet.getId(), userId, txKey, 10_000, 60_000);
        walletTransactionRepository.saveAndFlush(preExist);

        WalletCharge pending = walletChargeRepository.save(
            WalletCharge.create(wallet.getId(), userId, 10_000, "key-" + UUID.randomUUID()));
        given(pgPaymentClient.findPaymentByOrderId(anyString()))
            .willReturn(Optional.of(new TossPaymentStatusResponse(
                paymentKey, pending.getChargeId().toString(), "DONE", 10_000, "2024-01-01T00:00:00")));

        // when
        walletService.recoverStalePendingCharge(pending.getChargeId());

        // then: 상태는 COMPLETED, 잔액 변동 없음(60_000 그대로)
        WalletCharge after = walletChargeRepository.findByChargeId(pending.getChargeId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(WalletChargeStatus.COMPLETED);

        Wallet w = walletRepository.findByUserId(userId).orElseThrow();
        assertThat(w.getBalance()).isEqualTo(60_000);
    }

    // =====================================================================
    // A5: 동시 두 인스턴스가 같은 chargeId 처리 → 한쪽만 PROCESSING 선점
    // =====================================================================
    @Test
    @DisplayName("[A5] 같은 chargeId 동시 두 호출 → 한쪽만 PG 호출, COMPLETED 1회만 반영")
    void 동시_recoverStale_한쪽만_선점() throws InterruptedException {
        // given: PENDING 1건 + PG 응답에 의도적 지연을 주어 두 스레드 경합 유도
        WalletCharge pending = walletChargeRepository.save(
            WalletCharge.create(wallet.getId(), userId, 10_000, "key-" + UUID.randomUUID()));

        AtomicInteger pgCallCount = new AtomicInteger(0);
        given(pgPaymentClient.findPaymentByOrderId(anyString())).willAnswer(inv -> {
            pgCallCount.incrementAndGet();
            Thread.sleep(300); // 다른 스레드가 진입해 락 경합하도록 시간 확보
            return Optional.of(new TossPaymentStatusResponse(
                "pk-a5", pending.getChargeId().toString(), "DONE", 10_000, "2024-01-01T00:00:00"));
        });

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    start.await();
                    walletService.recoverStalePendingCharge(pending.getChargeId());
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        // when
        start.countDown();
        boolean finished = done.await(15, TimeUnit.SECONDS);

        // then
        assertThat(finished).isTrue();
        assertThat(errorCount.get()).isZero();
        // PG 호출은 단 1회 — 한쪽이 PENDING→PROCESSING 선점하면 다른 쪽은 isPending()=false 로 false 반환
        assertThat(pgCallCount.get()).isEqualTo(1);

        WalletCharge after = walletChargeRepository.findByChargeId(pending.getChargeId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(WalletChargeStatus.COMPLETED);

        Wallet w = walletRepository.findByUserId(userId).orElseThrow();
        // 한 번만 잔액 반영
        assertThat(w.getBalance()).isEqualTo(10_000);

        executor.shutdown();
    }

    // =====================================================================
    // helpers
    // =====================================================================

    private List<UUID> create3StalePending(int amount) {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            WalletCharge wc = walletChargeRepository.save(
                WalletCharge.create(wallet.getId(), userId, amount, "key-" + UUID.randomUUID()));
            ids.add(wc.getChargeId());
        }
        // STALE_THRESHOLD_MINUTES(30) 통과하도록 35분 전으로 backdating
        jdbcTemplate.update(
            "UPDATE payment.wallet_charge SET created_at = ? WHERE user_id = ?",
            LocalDateTime.now().minusMinutes(35), userId);
        return Collections.unmodifiableList(ids);
    }
}
