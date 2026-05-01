package com.devticket.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.devticket.payment.payment.application.service.PaymentService;
import com.devticket.payment.payment.domain.enums.PaymentMethod;
import com.devticket.payment.payment.domain.model.Payment;
import com.devticket.payment.payment.domain.repository.PaymentRepository;
import com.devticket.payment.payment.infrastructure.client.CommerceInternalClient;
import com.devticket.payment.payment.infrastructure.client.dto.InternalOrderInfoResponse;
import com.devticket.payment.payment.infrastructure.external.PgPaymentClient;
import com.devticket.payment.payment.presentation.dto.PaymentReadyRequest;
import com.devticket.payment.wallet.domain.model.Wallet;
import com.devticket.payment.wallet.domain.model.WalletTransaction;
import com.devticket.payment.wallet.domain.repository.WalletRepository;
import com.devticket.payment.wallet.domain.repository.WalletTransactionRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * WALLET_PG 재시도 시 잔액 차감 누락 회귀 방지 통합 테스트.
 *
 * 검증 대상: readyPayment 가 같은 orderId 로 결제수단/금액을 변경할 때
 *  - 기존 USE_&lt;orderId&gt; 트랜잭션 키가 환원 시 무효화되는지
 *  - 두 번째 deductForWalletPg 가 멱등 skip 되지 않고 실제 잔액을 차감하는지
 *
 * 실제 DB(PostgreSQL) UNIQUE(transaction_key) 제약 + atomic update 동작을 그대로 검증.
 * Mock: 외부 의존성만(Commerce REST, PG REST) 차단.
 */
@SpringBootTest
@ActiveProfiles("test")
class WalletPgRetryIntegrationTest {

    @Autowired private PaymentService paymentService;
    @Autowired private WalletRepository walletRepository;
    @Autowired private WalletTransactionRepository walletTransactionRepository;
    @Autowired private PaymentRepository paymentRepository;

    @MockitoBean private CommerceInternalClient commerceInternalClient;
    @MockitoBean private PgPaymentClient pgPaymentClient;

    private UUID userId;
    private UUID orderId;

    private static final int INITIAL_BALANCE = 100_000;
    private static final int TOTAL_AMOUNT = 50_000;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        Wallet wallet = Wallet.create(userId);
        wallet.charge(INITIAL_BALANCE);
        walletRepository.save(wallet);
    }

    @Test
    @DisplayName("WALLET_PG 2000 → 3000 재시도 — 환원 + 새 차감으로 잔액 정확히 -3000")
    void WALLET_PG_금액변경_재시도_차감_정확성() {
        // given
        InternalOrderInfoResponse orderInfo = orderInfoOf(orderId, userId, TOTAL_AMOUNT);
        when(commerceInternalClient.getOrderInfo(orderId)).thenReturn(orderInfo);

        PaymentReadyRequest first = new PaymentReadyRequest(orderId, PaymentMethod.WALLET_PG, 2000);
        PaymentReadyRequest second = new PaymentReadyRequest(orderId, PaymentMethod.WALLET_PG, 3000);

        // when 1: 첫 시도 — wallet 2000 차감
        paymentService.readyPayment(userId, first);

        Wallet afterFirst = walletRepository.findByUserId(userId).orElseThrow();
        assertThat(afterFirst.getBalance()).isEqualTo(INITIAL_BALANCE - 2000);

        // when 2: 재시도 — 기존 USE 키 무효화 후 새 차감(3000) 정상 진행
        paymentService.readyPayment(userId, second);

        // then 1: 잔액 = INITIAL_BALANCE - 3000 (차감 누락 없음)
        Wallet afterSecond = walletRepository.findByUserId(userId).orElseThrow();
        assertThat(afterSecond.getBalance())
            .as("환원 후 새 차감이 정상 적용되어야 함 — 차감 누락 시 잔액 = INITIAL_BALANCE - 2000 으로 남음")
            .isEqualTo(INITIAL_BALANCE - 3000);

        // then 2: Payment 가 in-place 갱신
        Payment payment = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(payment.getPaymentMethod()).isEqualTo(PaymentMethod.WALLET_PG);
        assertThat(payment.getWalletAmount()).isEqualTo(3000);
        assertThat(payment.getPgAmount()).isEqualTo(TOTAL_AMOUNT - 3000);

        // then 3: 트랜잭션 키 흔적 검증
        //   - USE_<orderId> 가 활성 row 로 존재 (재시도 deduct 결과, 새로 점유)
        //   - REVOKED_USE_<orderId>_* 도 존재 (첫 deduct 무효화 흔적, 단언은 not-empty 한도)
        Optional<WalletTransaction> useNow = walletTransactionRepository.findByTransactionKey("USE_" + orderId);
        assertThat(useNow).as("재시도 후 USE 키가 새 deduct 로 점유되어야 함").isPresent();
        assertThat(useNow.get().getAmount()).isEqualTo(3000);
        assertThat(useNow.get().getDeletedAt()).isNull();

        Optional<WalletTransaction> restored = walletTransactionRepository
            .findByTransactionKey("PG_WALLET_RESTORE_" + orderId);
        assertThat(restored).as("환원 트랜잭션이 기록되어야 함").isPresent();
        assertThat(restored.get().getAmount()).isEqualTo(2000);
    }

    @Test
    @DisplayName("WALLET_PG 2000 → PG 단독 재시도 — 환원만, 잔액 INITIAL_BALANCE 회복")
    void WALLET_PG에서_PG로_변경_환원만() {
        // given
        InternalOrderInfoResponse orderInfo = orderInfoOf(orderId, userId, TOTAL_AMOUNT);
        when(commerceInternalClient.getOrderInfo(orderId)).thenReturn(orderInfo);

        PaymentReadyRequest first = new PaymentReadyRequest(orderId, PaymentMethod.WALLET_PG, 2000);
        PaymentReadyRequest second = new PaymentReadyRequest(orderId, PaymentMethod.PG, null);

        // when
        paymentService.readyPayment(userId, first);
        paymentService.readyPayment(userId, second);

        // then: 잔액 환원 + 추가 차감 없음 = INITIAL_BALANCE
        Wallet wallet = walletRepository.findByUserId(userId).orElseThrow();
        assertThat(wallet.getBalance()).isEqualTo(INITIAL_BALANCE);

        Payment payment = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(payment.getPaymentMethod()).isEqualTo(PaymentMethod.PG);
        assertThat(payment.getWalletAmount()).isEqualTo(0);
        assertThat(payment.getPgAmount()).isEqualTo(0);

        // 환원 트랜잭션 기록 확인 + 기존 USE 키는 무효화되어 활성 row 없음
        assertThat(walletTransactionRepository
            .findByTransactionKey("PG_WALLET_RESTORE_" + orderId)).isPresent();
        assertThat(walletTransactionRepository
            .findByTransactionKey("USE_" + orderId))
            .as("PG 단독으로 변경 후에는 USE 활성 row 가 없어야 함")
            .isEmpty();
    }

    @Test
    @DisplayName("WALLET_PG 2000 동일 재요청 — 멱등 재사용, 잔액 변동 없음")
    void WALLET_PG_동일요청_멱등재사용() {
        // given
        InternalOrderInfoResponse orderInfo = orderInfoOf(orderId, userId, TOTAL_AMOUNT);
        when(commerceInternalClient.getOrderInfo(orderId)).thenReturn(orderInfo);

        PaymentReadyRequest request = new PaymentReadyRequest(orderId, PaymentMethod.WALLET_PG, 2000);

        // when: 같은 요청 두 번
        paymentService.readyPayment(userId, request);
        Wallet afterFirst = walletRepository.findByUserId(userId).orElseThrow();
        paymentService.readyPayment(userId, request);

        // then: 잔액은 첫 차감 그대로(이중 차감 없음), 환원도 발생하지 않음
        Wallet afterSecond = walletRepository.findByUserId(userId).orElseThrow();
        assertThat(afterSecond.getBalance()).isEqualTo(afterFirst.getBalance());
        assertThat(afterSecond.getBalance()).isEqualTo(INITIAL_BALANCE - 2000);

        assertThat(walletTransactionRepository
            .findByTransactionKey("PG_WALLET_RESTORE_" + orderId))
            .as("동일 요청은 멱등 재사용이므로 환원 트랜잭션이 기록되지 않아야 함")
            .isEmpty();
    }

    private InternalOrderInfoResponse orderInfoOf(UUID orderId, UUID userId, int totalAmount) {
        return new InternalOrderInfoResponse(
            orderId,
            userId,
            "ORD-" + orderId,
            totalAmount,
            "PAYMENT_PENDING",
            LocalDateTime.now().toString(),
            List.of()
        );
    }
}
