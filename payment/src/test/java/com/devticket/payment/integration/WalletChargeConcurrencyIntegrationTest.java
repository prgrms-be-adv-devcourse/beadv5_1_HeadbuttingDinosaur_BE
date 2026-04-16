package com.devticket.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.devticket.payment.payment.application.dto.PgPaymentConfirmResult;
import com.devticket.payment.payment.infrastructure.external.PgPaymentClient;
import com.devticket.payment.wallet.application.service.WalletService;
import com.devticket.payment.wallet.domain.model.Wallet;
import com.devticket.payment.wallet.domain.repository.WalletChargeRepository;
import com.devticket.payment.wallet.domain.repository.WalletRepository;
import com.devticket.payment.wallet.domain.repository.WalletTransactionRepository;
import com.devticket.payment.wallet.presentation.dto.WalletChargeConfirmRequest;
import com.devticket.payment.wallet.presentation.dto.WalletChargeConfirmResponse;
import com.devticket.payment.wallet.presentation.dto.WalletChargeRequest;
import com.devticket.payment.wallet.presentation.dto.WalletChargeResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 예치금 충전 멱등성 & 동시성 통합 테스트
 *
 * 실제 DB(PostgreSQL) 비관적 락 + UNIQUE 제약 + atomic update 검증.
 * PgPaymentClient만 Mock (외부 PG 호출 차단).
 */
@SpringBootTest
class WalletChargeConcurrencyIntegrationTest {

    @Autowired
    private WalletService walletService;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private WalletChargeRepository walletChargeRepository;

    @Autowired
    private WalletTransactionRepository walletTransactionRepository;

    @MockitoBean
    private PgPaymentClient pgPaymentClient;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        walletRepository.save(Wallet.create(userId));
    }

    // =========================================================================
    // 테스트 1: 멱등성 — 같은 idempotencyKey로 동시 10건
    //
    // 공격: 프론트 더블클릭 / 네트워크 재전송 시뮬레이션
    // 방어: findByUserIdAndIdempotencyKey + UNIQUE(user_id, idempotency_key) + DataIntegrityViolationException catch
    // 검증: 모든 응답의 chargeId 동일 + DB 충전 총액 = 1건 금액
    // =========================================================================
    @Test
    @DisplayName("같은 멱등성 키로 동시 10건 요청 시 WalletCharge는 1건만 생성된다")
    void 동일_멱등성키_동시요청_1건만_생성() throws InterruptedException {
        // given
        int threadCount = 10;
        int amount = 10_000;
        String idempotencyKey = "idem-" + UUID.randomUUID();
        WalletChargeRequest request = new WalletChargeRequest(amount);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        List<String> chargeIds = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    WalletChargeResponse response = walletService.charge(userId, request, idempotencyKey);
                    chargeIds.add(response.chargeId());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    System.out.println("  에러: " + e.getMessage());
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        done.await();

        // then
        long uniqueChargeIds = chargeIds.stream().distinct().count();
        int todayTotal = walletChargeRepository.sumTodayChargeAmount(
            userId, LocalDate.now().atStartOfDay()
        );
        boolean chargeExists = walletChargeRepository
            .findByUserIdAndIdempotencyKey(userId, idempotencyKey)
            .isPresent();

        System.out.println("========== 멱등성 테스트 결과 ==========");
        System.out.println("성공 응답: " + successCount.get() + " / " + threadCount);
        System.out.println("실패 응답: " + failCount.get());
        System.out.println("고유 chargeId 수: " + uniqueChargeIds);
        System.out.println("DB 오늘 충전 총액: " + todayTotal + "원");

        assertThat(uniqueChargeIds).isEqualTo(1);
        assertThat(successCount.get()).isEqualTo(threadCount);
        assertThat(todayTotal).isEqualTo(amount);
        assertThat(chargeExists).isTrue();

        executor.shutdown();
    }

    // =========================================================================
    // 테스트 2: 일일 한도 동시성 — 5만원 × 30건 (한도 100만원)
    //
    // 공격: 30개 스레드가 동시에 charge() 진입 → 모두 todayTotal=0 읽으면 한도 뚫림
    // 방어: findByUserIdForUpdate 비관적 락 → 한 번에 1개만 한도 체크 통과
    // 검증: DB 충전 총액 = 정확히 1,000,000원
    // =========================================================================
    @Test
    @DisplayName("서로 다른 충전 요청 동시 30건 시 일일 한도(100만원)를 정확히 지킨다")
    void 동시_충전요청_일일한도_정확히_100만원() throws InterruptedException {
        // given
        int threadCount = 30;
        int amountPerRequest = 50_000;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger limitExceededCount = new AtomicInteger(0);
        AtomicInteger otherErrorCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    String uniqueKey = "limit-" + UUID.randomUUID();
                    walletService.charge(userId, new WalletChargeRequest(amountPerRequest), uniqueKey);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    if (e.getMessage() != null && e.getMessage().contains("한도")) {
                        limitExceededCount.incrementAndGet();
                    } else {
                        otherErrorCount.incrementAndGet();
                        System.out.println("  기타 에러: " + e.getMessage());
                    }
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        done.await();

        // then
        int todayTotal = walletChargeRepository.sumTodayChargeAmount(
            userId, LocalDate.now().atStartOfDay()
        );

        System.out.println("========== 일일 한도 동시성 테스트 결과 ==========");
        System.out.println("성공: " + successCount.get() + "건");
        System.out.println("한도 초과 거부: " + limitExceededCount.get() + "건");
        System.out.println("기타 에러: " + otherErrorCount.get() + "건");
        System.out.println("DB 오늘 충전 총액: " + todayTotal + "원");

        assertThat(todayTotal).isEqualTo(1_000_000);
        assertThat(successCount.get()).isEqualTo(20);
        assertThat(limitExceededCount.get()).isEqualTo(10);

        executor.shutdown();
    }

    // =========================================================================
    // 테스트 3: 신규 유저 — 지갑 미생성 상태에서 다기기 동시 충전 (각기 다른 멱등성 키)
    //
    // 시나리오: 신규 사용자가 여러 기기에서 동시에 첫 충전 시도
    // 공격: Wallet이 없는 상태에서 N개 스레드가 동시에 charge() 진입
    //        → 모두 findByUserIdForUpdate 빈값 → 모두 Wallet INSERT 시도 → DIVE 발생 가능
    // 방어: REQUIRES_NEW로 Wallet INSERT 격리 → 외부 세션 유지 → 재조회로 복구
    // 검증: Wallet 1개만 생성 + 에러 없이 N건 모두 성공 + WalletCharge N건 생성
    // =========================================================================
    @Test
    @DisplayName("신규 유저: 지갑 없는 상태에서 다기기 동시 충전(다른 멱등성 키) — Wallet 1개, WalletCharge N건 생성")
    void 신규유저_지갑미생성_다기기_동시충전_각기다른멱등성키() throws InterruptedException {
        // given — 지갑이 없는 신규 유저 (setUp의 walletRepository.save 적용 안 됨)
        UUID newUserId = UUID.randomUUID();
        int threadCount = 5;
        int amount = 10_000;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        List<String> chargeIds = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when — 각 스레드는 서로 다른 멱등성 키 사용 (다기기 시나리오)
        for (int i = 0; i < threadCount; i++) {
            String uniqueKey = "new-user-device-" + i + "-" + UUID.randomUUID();
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    WalletChargeResponse response = walletService.charge(
                        newUserId, new WalletChargeRequest(amount), uniqueKey);
                    chargeIds.add(response.chargeId());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    System.out.println("  에러: " + e.getMessage());
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        done.await();

        // then
        long walletCount = walletRepository.findByUserId(newUserId).stream().count();
        long uniqueChargeIds = chargeIds.stream().distinct().count();
        int todayTotal = walletChargeRepository.sumTodayChargeAmount(
            newUserId, LocalDate.now().atStartOfDay()
        );

        System.out.println("========== 신규 유저 다기기 동시 충전 테스트 결과 ==========");
        System.out.println("성공 응답: " + successCount.get() + " / " + threadCount);
        System.out.println("실패 응답: " + failCount.get());
        System.out.println("생성된 Wallet 수: " + walletCount);
        System.out.println("고유 chargeId 수: " + uniqueChargeIds);
        System.out.println("DB 오늘 충전 총액: " + todayTotal + "원");

        assertThat(failCount.get()).isEqualTo(0);
        assertThat(successCount.get()).isEqualTo(threadCount);
        assertThat(walletCount).isEqualTo(1);                           // Wallet은 1개만 생성
        assertThat(uniqueChargeIds).isEqualTo(threadCount);             // 각기 다른 chargeId
        assertThat(todayTotal).isEqualTo(amount * threadCount);         // 전체 충전액 합산

        executor.shutdown();
    }

    // =========================================================================
    // 테스트 5: 기존 유저 — 동일 멱등성 키로 동시 100건
    //
    // 공격: 기존 Wallet이 있는 사용자가 네트워크 재전송 등으로 같은 키로 100건 동시 요청
    // 방어: 1차 멱등성 체크 + SELECT FOR UPDATE 후 2차 멱등성 체크 + UNIQUE 제약
    // 검증: 모든 응답의 chargeId 동일 + DB 충전 총액 = 1건 금액 + 성공 100건
    // =========================================================================
    @Test
    @DisplayName("기존 유저: 동일 멱등성 키로 동시 100건 요청 시 WalletCharge는 1건만 생성된다")
    void 기존유저_동일_멱등성키_동시100건_1건만_생성() throws InterruptedException {
        // given
        int threadCount = 100;
        int amount = 10_000;
        String idempotencyKey = "idem-existing-" + UUID.randomUUID();
        WalletChargeRequest request = new WalletChargeRequest(amount);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        List<String> chargeIds = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    WalletChargeResponse response = walletService.charge(userId, request, idempotencyKey);
                    chargeIds.add(response.chargeId());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    System.out.println("  에러: " + e.getMessage());
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        done.await();

        // then
        long uniqueChargeIds = chargeIds.stream().distinct().count();
        int todayTotal = walletChargeRepository.sumTodayChargeAmount(
            userId, LocalDate.now().atStartOfDay()
        );
        boolean chargeExists = walletChargeRepository
            .findByUserIdAndIdempotencyKey(userId, idempotencyKey)
            .isPresent();

        System.out.println("========== 기존 유저 동일 멱등성 키 100건 테스트 결과 ==========");
        System.out.println("성공 응답: " + successCount.get() + " / " + threadCount);
        System.out.println("실패 응답: " + failCount.get());
        System.out.println("고유 chargeId 수: " + uniqueChargeIds);
        System.out.println("DB 오늘 충전 총액: " + todayTotal + "원");

        assertThat(uniqueChargeIds).isEqualTo(1);
        assertThat(successCount.get()).isEqualTo(threadCount);
        assertThat(todayTotal).isEqualTo(amount);
        assertThat(chargeExists).isTrue();

        executor.shutdown();
    }

    // =========================================================================
    // 테스트 6: 기존 유저 — 다기기(다른 멱등성 키)로 동시 100건
    //
    // 시나리오: 기존 Wallet이 있는 사용자가 100개 기기에서 동시에 충전 요청
    // 방어: SELECT FOR UPDATE 비관적 락으로 한도 체크 직렬화
    // 검증: 에러 없이 100건 전부 성공 + DB 충전 총액 = 100 × 5,000원
    // =========================================================================
    @Test
    @DisplayName("기존 유저: 다기기(다른 멱등성 키)로 동시 100건 요청 시 모두 성공하고 총액이 정확하다")
    void 기존유저_다기기_동시100건_모두_성공() throws InterruptedException {
        // given — 100건 × 5,000원 = 500,000원 (일일 한도 100만원 이내)
        int threadCount = 100;
        int amountPerRequest = 5_000;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        List<String> chargeIds = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when — 각 스레드는 서로 다른 멱등성 키 사용
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    String uniqueKey = "multi-device-" + UUID.randomUUID();
                    WalletChargeResponse response = walletService.charge(
                        userId, new WalletChargeRequest(amountPerRequest), uniqueKey);
                    chargeIds.add(response.chargeId());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    System.out.println("  에러: " + e.getMessage());
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        done.await();

        // then
        long uniqueChargeIds = chargeIds.stream().distinct().count();
        int todayTotal = walletChargeRepository.sumTodayChargeAmount(
            userId, LocalDate.now().atStartOfDay()
        );

        System.out.println("========== 기존 유저 다기기 동시 100건 테스트 결과 ==========");
        System.out.println("성공 응답: " + successCount.get() + " / " + threadCount);
        System.out.println("실패 응답: " + failCount.get());
        System.out.println("고유 chargeId 수: " + uniqueChargeIds);
        System.out.println("DB 오늘 충전 총액: " + todayTotal + "원");

        assertThat(failCount.get()).isEqualTo(0);
        assertThat(successCount.get()).isEqualTo(threadCount);
        assertThat(uniqueChargeIds).isEqualTo(threadCount);          // 각기 다른 chargeId
        assertThat(todayTotal).isEqualTo(amountPerRequest * threadCount); // 500,000원

        executor.shutdown();
    }

    // =========================================================================
    // 테스트 4: confirm 중복 — 같은 chargeId로 동시 confirm 10건
    //
    // 공격: 결제 성공 콜백이 네트워크 이슈로 중복 도달
    // 방어: findByChargeIdForUpdate 비관적 락 → 첫 요청이 COMPLETED로 변경 → 나머지 isPending() false
    //       + chargeBalanceAtomic + existsByTransactionKey
    // 검증: 잔액 1회분만 증가 + WalletTransaction 1건
    // =========================================================================
    @Test
    @DisplayName("같은 chargeId로 동시 confirm 10건 시 잔액은 1회분만 증가한다")
    void 동시_confirm_요청_1건만_반영() throws InterruptedException {
        // given — PENDING 상태 WalletCharge 생성
        String idempotencyKey = "confirm-test-" + UUID.randomUUID();
        int chargeAmount = 30_000;
        WalletChargeResponse chargeResponse = walletService.charge(
            userId, new WalletChargeRequest(chargeAmount), idempotencyKey
        );
        String chargeId = chargeResponse.chargeId();

        // PG Mock: confirm 항상 성공
        String fakePaymentKey = "test_pk_" + UUID.randomUUID();
        Mockito.when(pgPaymentClient.confirm(Mockito.any()))
            .thenReturn(new PgPaymentConfirmResult(
                fakePaymentKey, chargeId, "카드", "DONE", chargeAmount, "2026-04-15T15:00:00"
            ));

        // 충전 전 잔액
        int beforeBalance = walletRepository.findByUserId(userId).orElseThrow().getBalance();

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        AtomicInteger completedCount = new AtomicInteger(0);
        AtomicInteger notPendingCount = new AtomicInteger(0);
        AtomicInteger failedCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    WalletChargeConfirmResponse response = walletService.confirmCharge(
                        userId,
                        new WalletChargeConfirmRequest(fakePaymentKey, chargeId, chargeAmount)
                    );
                    if ("COMPLETED".equals(response.status())) {
                        completedCount.incrementAndGet();
                    } else if ("FAILED".equals(response.status())) {
                        failedCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    notPendingCount.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        done.await();

        // then
        int afterBalance = walletRepository.findByUserId(userId).orElseThrow().getBalance();
        int increased = afterBalance - beforeBalance;
        boolean txExists = walletTransactionRepository.existsByTransactionKey("CHARGE:" + fakePaymentKey);

        System.out.println("========== confirm 동시성 테스트 결과 ==========");
        System.out.println("COMPLETED 응답: " + completedCount.get() + "건");
        System.out.println("NOT_PENDING 거부: " + notPendingCount.get() + "건");
        System.out.println("FAILED 응답: " + failedCount.get() + "건");
        System.out.println("잔액 변화: " + beforeBalance + " → " + afterBalance + " (+" + increased + "원)");
        System.out.println("WalletTransaction 존재: " + txExists);

        assertThat(increased).isEqualTo(chargeAmount);
        assertThat(txExists).isTrue();
        assertThat(completedCount.get()).isEqualTo(1);
        assertThat(notPendingCount.get()).isEqualTo(threadCount - 1);

        executor.shutdown();
    }
}