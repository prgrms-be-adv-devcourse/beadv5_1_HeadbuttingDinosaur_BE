package com.devticket.payment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.devticket.payment.common.outbox.OutboxService;
import com.devticket.payment.payment.application.dto.PgPaymentConfirmResult;
import com.devticket.payment.payment.application.service.PaymentServiceImpl;
import com.devticket.payment.payment.domain.enums.PaymentMethod;
import com.devticket.payment.payment.domain.enums.PaymentStatus;
import com.devticket.payment.payment.domain.exception.PaymentErrorCode;
import com.devticket.payment.payment.domain.exception.PaymentException;
import com.devticket.payment.payment.domain.model.Payment;
import com.devticket.payment.payment.domain.repository.PaymentRepository;
import com.devticket.payment.payment.infrastructure.client.CommerceInternalClient;
import com.devticket.payment.payment.infrastructure.client.dto.InternalOrderInfoResponse;
import com.devticket.payment.payment.infrastructure.external.PgPaymentClient;
import com.devticket.payment.payment.presentation.dto.InternalPaymentInfoResponse;
import com.devticket.payment.payment.presentation.dto.PaymentConfirmRequest;
import com.devticket.payment.payment.presentation.dto.PaymentConfirmResponse;
import com.devticket.payment.payment.presentation.dto.PaymentFailRequest;
import com.devticket.payment.payment.presentation.dto.PaymentFailResponse;
import com.devticket.payment.payment.presentation.dto.PaymentReadyRequest;
import com.devticket.payment.payment.presentation.dto.PaymentReadyResponse;
import com.devticket.payment.wallet.application.event.PaymentCompletedEvent;
import com.devticket.payment.wallet.application.service.WalletService;
import java.util.List;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock private CommerceInternalClient commerceInternalClient;
    @Mock private PgPaymentClient pgPaymentClient;
    @Mock private OutboxService outboxService;
    @Mock private WalletService walletService;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID OTHER_USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
    private static final UUID ORDER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440002");
    private static final UUID EXTERNAL_ORDER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440003");
    private static final UUID PAYMENT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440004");
    private static final String PAYMENT_KEY = "toss_pg_key_123";
    private static final String APPROVED_AT = "2024-01-01T00:00:00+09:00";

    private InternalOrderInfoResponse orderInfo;

    @BeforeEach
    void setUp() {
        orderInfo = new InternalOrderInfoResponse(
            ORDER_ID,
            USER_ID,
            "ORD-20250815-001",
            130000,
            "PAYMENT_PENDING",
            LocalDateTime.of(2025, 8, 15, 14, 30).toString(),
            List.of()
        );
    }

    private Payment createReadyPayment() {
        return Payment.create(orderInfo.id(), USER_ID, PaymentMethod.PG, orderInfo.totalAmount());
    }

    private PgPaymentConfirmResult createPgConfirmResult() {
        return new PgPaymentConfirmResult(
            PAYMENT_KEY,
            EXTERNAL_ORDER_ID.toString(),
            "카드",
            "DONE",
            orderInfo.totalAmount(),
            APPROVED_AT
        );
    }

    // =========================================================
    // readyPayment
    // =========================================================

    @Nested
    @DisplayName("결제 준비")
    class ReadyPaymentTest {

        @Test
        @DisplayName("PG 결제 준비 성공 — READY 상태 반환")
        void PG_결제_준비_성공() {
            // given
            PaymentReadyRequest request = new PaymentReadyRequest(EXTERNAL_ORDER_ID, PaymentMethod.PG, null);

            given(commerceInternalClient.getOrderInfo(EXTERNAL_ORDER_ID)).willReturn(orderInfo);
            given(paymentRepository.save(any(Payment.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

            // when
            PaymentReadyResponse response = paymentService.readyPayment(USER_ID, request);

            // then
            assertThat(response.orderId()).isEqualTo(EXTERNAL_ORDER_ID);
            assertThat(response.orderNumber()).isEqualTo(orderInfo.orderNumber());
            assertThat(response.paymentMethod()).isEqualTo(PaymentMethod.PG.name());
            assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.READY);
            assertThat(response.amount()).isEqualTo(orderInfo.totalAmount());
            assertThat(response.approvedAt()).isNull();
        }

        @Test
        @DisplayName("다른 사용자의 주문 — 예외")
        void 다른_사용자의_주문() {
            // given
            PaymentReadyRequest request = new PaymentReadyRequest(EXTERNAL_ORDER_ID, PaymentMethod.PG, null);

            given(commerceInternalClient.getOrderInfo(EXTERNAL_ORDER_ID)).willReturn(orderInfo);

            // when & then
            assertThatThrownBy(() -> paymentService.readyPayment(OTHER_USER_ID, request))
                .isInstanceOf(PaymentException.class);

            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("주문 상태가 PAYMENT_PENDING이 아닌 경우 — 예외")
        void 주문_상태가_결제_대기가_아닌_경우() {
            // given
            PaymentReadyRequest request = new PaymentReadyRequest(EXTERNAL_ORDER_ID, PaymentMethod.PG, null);
            InternalOrderInfoResponse paidOrder = new InternalOrderInfoResponse(
                ORDER_ID, USER_ID, "ORD-001", 130000, "PAID",
                LocalDateTime.of(2025, 8, 15, 14, 30).toString(),
                List.of()
            );

            given(commerceInternalClient.getOrderInfo(EXTERNAL_ORDER_ID)).willReturn(paidOrder);

            // when & then
            assertThatThrownBy(() -> paymentService.readyPayment(USER_ID, request))
                .isInstanceOf(RuntimeException.class);

            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("예치금 결제 준비 성공 — WalletService로 위임")
        void 예치금_결제_준비_성공() {
            // given
            PaymentReadyRequest request = new PaymentReadyRequest(EXTERNAL_ORDER_ID, PaymentMethod.WALLET, null);
            Payment savedPayment = createWalletReadyPayment();
            savedPayment.approve("WALLET-" + savedPayment.getPaymentId());

            given(commerceInternalClient.getOrderInfo(EXTERNAL_ORDER_ID)).willReturn(orderInfo);
            given(paymentRepository.findByOrderId(orderInfo.id()))
                .willReturn(Optional.empty())
                .willReturn(Optional.of(savedPayment));
            given(paymentRepository.save(any(Payment.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

            // when
            PaymentReadyResponse response = paymentService.readyPayment(USER_ID, request);

            // then
            verify(walletService).processWalletPayment(eq(USER_ID), eq(EXTERNAL_ORDER_ID), eq(orderInfo.totalAmount()), any());
            assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        }

        // =====================================================================
        // 재시도 시나리오 — 기존 READY Payment 가 있을 때
        // =====================================================================

        @Test
        @DisplayName("재시도 #1 — READY+PG 동일 PG 재요청: 기존 그대로 반환, save 호출 0회")
        void 재시도_PG_동일요청_멱등재사용() {
            // given
            PaymentReadyRequest request = new PaymentReadyRequest(EXTERNAL_ORDER_ID, PaymentMethod.PG, null);
            Payment existing = Payment.create(orderInfo.id(), USER_ID, PaymentMethod.PG, orderInfo.totalAmount());

            given(commerceInternalClient.getOrderInfo(EXTERNAL_ORDER_ID)).willReturn(orderInfo);
            given(paymentRepository.findByOrderId(orderInfo.id())).willReturn(Optional.of(existing));

            // when
            PaymentReadyResponse response = paymentService.readyPayment(USER_ID, request);

            // then
            assertThat(response.paymentMethod()).isEqualTo(PaymentMethod.PG.name());
            assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.READY);
            verify(paymentRepository, never()).save(any());
            verify(walletService, never()).deductForWalletPg(any(), any(), org.mockito.ArgumentMatchers.anyInt());
            verify(walletService, never()).restoreForWalletPgFail(any(), org.mockito.ArgumentMatchers.anyInt(), any());
        }

        @Test
        @DisplayName("재시도 #2 — READY+WALLET_PG(2000) 동일 WALLET_PG(2000): 기존 그대로 반환, deductForWalletPg 호출 0회")
        void 재시도_WALLET_PG_동일금액_멱등재사용() {
            // given
            int totalAmount = orderInfo.totalAmount();
            int walletAmount = 2000;
            int pgAmount = totalAmount - walletAmount;
            Payment existing = Payment.create(
                orderInfo.id(), USER_ID, PaymentMethod.WALLET_PG, totalAmount, walletAmount, pgAmount);
            PaymentReadyRequest request = new PaymentReadyRequest(EXTERNAL_ORDER_ID, PaymentMethod.WALLET_PG, walletAmount);

            given(commerceInternalClient.getOrderInfo(EXTERNAL_ORDER_ID)).willReturn(orderInfo);
            given(paymentRepository.findByOrderId(orderInfo.id())).willReturn(Optional.of(existing));

            // when
            PaymentReadyResponse response = paymentService.readyPayment(USER_ID, request);

            // then
            assertThat(response.paymentMethod()).isEqualTo(PaymentMethod.WALLET_PG.name());
            assertThat(response.walletAmount()).isEqualTo(walletAmount);
            assertThat(response.pgAmount()).isEqualTo(pgAmount);
            verify(paymentRepository, never()).save(any());
            verify(walletService, never()).deductForWalletPg(any(), any(), org.mockito.ArgumentMatchers.anyInt());
            verify(walletService, never()).restoreForWalletPgFail(any(), org.mockito.ArgumentMatchers.anyInt(), any());
        }

        @Test
        @DisplayName("재시도 #3 — READY+PG → WALLET 변경: in-place 갱신, processWalletPayment 호출")
        void 재시도_PG에서_WALLET으로_변경() {
            // given
            Payment existing = Payment.create(orderInfo.id(), USER_ID, PaymentMethod.PG, orderInfo.totalAmount());
            UUID preservedPaymentId = existing.getPaymentId();
            PaymentReadyRequest request = new PaymentReadyRequest(EXTERNAL_ORDER_ID, PaymentMethod.WALLET, null);

            // 변경 후 WALLET 결제가 SUCCESS로 처리되는 흐름 시뮬레이션
            given(commerceInternalClient.getOrderInfo(EXTERNAL_ORDER_ID)).willReturn(orderInfo);
            given(paymentRepository.findByOrderId(orderInfo.id())).willReturn(Optional.of(existing));
            given(paymentRepository.save(any(Payment.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

            // when
            PaymentReadyResponse response = paymentService.readyPayment(USER_ID, request);

            // then
            assertThat(existing.getPaymentMethod()).isEqualTo(PaymentMethod.WALLET);
            assertThat(existing.getPaymentId()).isEqualTo(preservedPaymentId);
            verify(walletService).processWalletPayment(eq(USER_ID), eq(EXTERNAL_ORDER_ID), eq(orderInfo.totalAmount()), any());
            verify(walletService, never()).restoreForWalletPgFail(any(), org.mockito.ArgumentMatchers.anyInt(), any());
            assertThat(response.paymentMethod()).isEqualTo(PaymentMethod.WALLET.name());
        }

        @Test
        @DisplayName("재시도 #4 — READY+PG → WALLET_PG 변경: deductForWalletPg 호출, restoreForWalletPgFail 호출 0회")
        void 재시도_PG에서_WALLET_PG로_변경() {
            // given
            int totalAmount = orderInfo.totalAmount();
            int walletAmount = 2000;
            int pgAmount = totalAmount - walletAmount;
            Payment existing = Payment.create(orderInfo.id(), USER_ID, PaymentMethod.PG, totalAmount);
            UUID preservedPaymentId = existing.getPaymentId();
            PaymentReadyRequest request = new PaymentReadyRequest(EXTERNAL_ORDER_ID, PaymentMethod.WALLET_PG, walletAmount);

            given(commerceInternalClient.getOrderInfo(EXTERNAL_ORDER_ID)).willReturn(orderInfo);
            given(paymentRepository.findByOrderId(orderInfo.id())).willReturn(Optional.of(existing));
            given(paymentRepository.save(any(Payment.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

            // when
            PaymentReadyResponse response = paymentService.readyPayment(USER_ID, request);

            // then
            assertThat(existing.getPaymentMethod()).isEqualTo(PaymentMethod.WALLET_PG);
            assertThat(existing.getWalletAmount()).isEqualTo(walletAmount);
            assertThat(existing.getPgAmount()).isEqualTo(pgAmount);
            assertThat(existing.getPaymentId()).isEqualTo(preservedPaymentId);
            verify(walletService).deductForWalletPg(USER_ID, orderInfo.id(), walletAmount);
            verify(walletService, never()).restoreForWalletPgFail(any(), org.mockito.ArgumentMatchers.anyInt(), any());
            assertThat(response.paymentMethod()).isEqualTo(PaymentMethod.WALLET_PG.name());
            assertThat(response.walletAmount()).isEqualTo(walletAmount);
        }

        @Test
        @DisplayName("재시도 #5 — READY+WALLET_PG(2000) → WALLET_PG(3000): 기존 환원 + 새 차감")
        void 재시도_WALLET_PG_금액변경() {
            // given
            int totalAmount = orderInfo.totalAmount();
            int oldWalletAmount = 2000;
            int oldPgAmount = totalAmount - oldWalletAmount;
            int newWalletAmount = 3000;
            int newPgAmount = totalAmount - newWalletAmount;
            Payment existing = Payment.create(
                orderInfo.id(), USER_ID, PaymentMethod.WALLET_PG, totalAmount, oldWalletAmount, oldPgAmount);
            UUID preservedPaymentId = existing.getPaymentId();
            PaymentReadyRequest request = new PaymentReadyRequest(EXTERNAL_ORDER_ID, PaymentMethod.WALLET_PG, newWalletAmount);

            given(commerceInternalClient.getOrderInfo(EXTERNAL_ORDER_ID)).willReturn(orderInfo);
            given(paymentRepository.findByOrderId(orderInfo.id())).willReturn(Optional.of(existing));
            given(paymentRepository.save(any(Payment.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

            // when
            PaymentReadyResponse response = paymentService.readyPayment(USER_ID, request);

            // then
            verify(walletService).restoreForWalletPgFail(USER_ID, oldWalletAmount, orderInfo.id());
            verify(walletService).deductForWalletPg(USER_ID, orderInfo.id(), newWalletAmount);
            assertThat(existing.getWalletAmount()).isEqualTo(newWalletAmount);
            assertThat(existing.getPgAmount()).isEqualTo(newPgAmount);
            assertThat(existing.getPaymentId()).isEqualTo(preservedPaymentId);
            assertThat(response.walletAmount()).isEqualTo(newWalletAmount);
        }

        @Test
        @DisplayName("재시도 #6 — READY+WALLET_PG(2000) → WALLET 단독: restoreForWalletPgFail + processWalletPayment 호출")
        void 재시도_WALLET_PG에서_WALLET으로_변경() {
            // given
            int totalAmount = orderInfo.totalAmount();
            int oldWalletAmount = 2000;
            int oldPgAmount = totalAmount - oldWalletAmount;
            Payment existing = Payment.create(
                orderInfo.id(), USER_ID, PaymentMethod.WALLET_PG, totalAmount, oldWalletAmount, oldPgAmount);
            PaymentReadyRequest request = new PaymentReadyRequest(EXTERNAL_ORDER_ID, PaymentMethod.WALLET, null);

            given(commerceInternalClient.getOrderInfo(EXTERNAL_ORDER_ID)).willReturn(orderInfo);
            given(paymentRepository.findByOrderId(orderInfo.id())).willReturn(Optional.of(existing));
            given(paymentRepository.save(any(Payment.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

            // when
            paymentService.readyPayment(USER_ID, request);

            // then
            verify(walletService).restoreForWalletPgFail(USER_ID, oldWalletAmount, orderInfo.id());
            verify(walletService).processWalletPayment(eq(USER_ID), eq(EXTERNAL_ORDER_ID), eq(totalAmount), any());
            assertThat(existing.getPaymentMethod()).isEqualTo(PaymentMethod.WALLET);
            assertThat(existing.getWalletAmount()).isEqualTo(0);
            assertThat(existing.getPgAmount()).isEqualTo(0);
        }

        @Test
        @DisplayName("재시도 #7 — READY+WALLET_PG(2000) → PG 단독: restoreForWalletPgFail 호출, walletAmount/pgAmount=0")
        void 재시도_WALLET_PG에서_PG로_변경() {
            // given
            int totalAmount = orderInfo.totalAmount();
            int oldWalletAmount = 2000;
            int oldPgAmount = totalAmount - oldWalletAmount;
            Payment existing = Payment.create(
                orderInfo.id(), USER_ID, PaymentMethod.WALLET_PG, totalAmount, oldWalletAmount, oldPgAmount);
            UUID preservedPaymentId = existing.getPaymentId();
            PaymentReadyRequest request = new PaymentReadyRequest(EXTERNAL_ORDER_ID, PaymentMethod.PG, null);

            given(commerceInternalClient.getOrderInfo(EXTERNAL_ORDER_ID)).willReturn(orderInfo);
            given(paymentRepository.findByOrderId(orderInfo.id())).willReturn(Optional.of(existing));
            given(paymentRepository.save(any(Payment.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

            // when
            PaymentReadyResponse response = paymentService.readyPayment(USER_ID, request);

            // then
            verify(walletService).restoreForWalletPgFail(USER_ID, oldWalletAmount, orderInfo.id());
            verify(walletService, never()).deductForWalletPg(any(), any(), org.mockito.ArgumentMatchers.anyInt());
            verify(walletService, never()).processWalletPayment(any(), any(), org.mockito.ArgumentMatchers.anyInt(), any());
            assertThat(existing.getPaymentMethod()).isEqualTo(PaymentMethod.PG);
            assertThat(existing.getAmount()).isEqualTo(totalAmount);
            assertThat(existing.getWalletAmount()).isEqualTo(0);
            assertThat(existing.getPgAmount()).isEqualTo(0);
            assertThat(existing.getPaymentId()).isEqualTo(preservedPaymentId);
            assertThat(response.paymentMethod()).isEqualTo(PaymentMethod.PG.name());
        }

        @Test
        @DisplayName("재시도 #8 — SUCCESS Payment 재요청: ALREADY_PROCESSED_PAYMENT 예외 (회귀)")
        void 재시도_종단상태_거부() {
            // given
            Payment existing = createReadyPayment();
            existing.approve(PAYMENT_KEY);
            PaymentReadyRequest request = new PaymentReadyRequest(EXTERNAL_ORDER_ID, PaymentMethod.PG, null);

            given(commerceInternalClient.getOrderInfo(EXTERNAL_ORDER_ID)).willReturn(orderInfo);
            given(paymentRepository.findByOrderId(orderInfo.id())).willReturn(Optional.of(existing));

            // when & then
            assertThatThrownBy(() -> paymentService.readyPayment(USER_ID, request))
                .isInstanceOf(PaymentException.class)
                .extracting(e -> ((PaymentException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.ALREADY_PROCESSED_PAYMENT);

            verify(paymentRepository, never()).save(any());
            verify(walletService, never()).restoreForWalletPgFail(any(), org.mockito.ArgumentMatchers.anyInt(), any());
            verify(walletService, never()).deductForWalletPg(any(), any(), org.mockito.ArgumentMatchers.anyInt());
        }

        private Payment createWalletReadyPayment() {
            return Payment.create(orderInfo.id(), USER_ID, PaymentMethod.WALLET, orderInfo.totalAmount());
        }
    }

    // =========================================================
    // Payment.resetForRetry — 도메인 단위 테스트
    // =========================================================

    @Nested
    @DisplayName("Payment.resetForRetry")
    class ResetForRetryTest {

        @Test
        @DisplayName("READY 상태에서 결제수단/금액 재초기화 — 정상")
        void READY에서_재초기화_성공() {
            // given
            Payment payment = Payment.create(ORDER_ID, USER_ID, PaymentMethod.PG, 130000);

            // when
            payment.resetForRetry(PaymentMethod.WALLET_PG, 130000, 30000, 100000);

            // then
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
            assertThat(payment.getPaymentMethod()).isEqualTo(PaymentMethod.WALLET_PG);
            assertThat(payment.getAmount()).isEqualTo(130000);
            assertThat(payment.getWalletAmount()).isEqualTo(30000);
            assertThat(payment.getPgAmount()).isEqualTo(100000);
        }

        @Test
        @DisplayName("walletAmount/pgAmount null 입력 — 0으로 정규화")
        void null_금액_0으로_정규화() {
            // given
            Payment payment = Payment.create(ORDER_ID, USER_ID, PaymentMethod.WALLET_PG, 100000, 30000, 70000);

            // when
            payment.resetForRetry(PaymentMethod.PG, 100000, null, null);

            // then
            assertThat(payment.getWalletAmount()).isEqualTo(0);
            assertThat(payment.getPgAmount()).isEqualTo(0);
        }

        @Test
        @DisplayName("SUCCESS 상태 — INVALID_STATUS_TRANSITION 예외")
        void SUCCESS_상태에서_재초기화_거부() {
            // given
            Payment payment = Payment.create(ORDER_ID, USER_ID, PaymentMethod.PG, 130000);
            payment.approve(PAYMENT_KEY);

            // when & then
            assertThatThrownBy(() -> payment.resetForRetry(PaymentMethod.WALLET, 130000, 0, 0))
                .isInstanceOf(PaymentException.class)
                .extracting(e -> ((PaymentException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.INVALID_STATUS_TRANSITION);
        }

        @Test
        @DisplayName("FAILED 상태 — INVALID_STATUS_TRANSITION 예외")
        void FAILED_상태에서_재초기화_거부() {
            // given
            Payment payment = Payment.create(ORDER_ID, USER_ID, PaymentMethod.PG, 130000);
            payment.fail("test failure");

            // when & then
            assertThatThrownBy(() -> payment.resetForRetry(PaymentMethod.WALLET, 130000, 0, 0))
                .isInstanceOf(PaymentException.class)
                .extracting(e -> ((PaymentException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.INVALID_STATUS_TRANSITION);
        }
    }

    // =========================================================
    // confirmPgPayment
    // =========================================================

    @Nested
    @DisplayName("PG 결제 승인")
    class ConfirmPgPaymentTest {

        @Test
        @DisplayName("성공 — payment가 SUCCESS 상태, paymentKey·approvedAt 저장")
        void 성공() {
            // given
            Payment payment = createReadyPayment();
            PaymentConfirmRequest request = new PaymentConfirmRequest(
                PAYMENT_KEY, PAYMENT_ID, EXTERNAL_ORDER_ID, orderInfo.totalAmount());

            given(commerceInternalClient.getOrderInfo(EXTERNAL_ORDER_ID)).willReturn(orderInfo);
            given(paymentRepository.findByOrderId(orderInfo.id())).willReturn(Optional.of(payment));
            given(pgPaymentClient.confirm(any())).willReturn(createPgConfirmResult());
            given(paymentRepository.save(any(Payment.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

            // when
            PaymentConfirmResponse response = paymentService.confirmPgPayment(USER_ID, request);

            // then
            assertThat(response.status()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(payment.getPaymentKey()).isEqualTo(PAYMENT_KEY);
            assertThat(payment.getApprovedAt()).isNotNull();
            verify(outboxService).save(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("결제 정보 미존재 — INVALID_PAYMENT_REQUEST 예외")
        void 결제_정보_미존재() {
            // given
            PaymentConfirmRequest request = new PaymentConfirmRequest(
                PAYMENT_KEY, PAYMENT_ID, EXTERNAL_ORDER_ID, orderInfo.totalAmount());

            given(commerceInternalClient.getOrderInfo(EXTERNAL_ORDER_ID)).willReturn(orderInfo);
            given(paymentRepository.findByOrderId(orderInfo.id())).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> paymentService.confirmPgPayment(USER_ID, request))
                .isInstanceOf(PaymentException.class)
                .extracting(e -> ((PaymentException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.INVALID_PAYMENT_REQUEST);

            verify(pgPaymentClient, never()).confirm(any());
        }

        @Test
        @DisplayName("다른 사용자의 결제 — INVALID_PAYMENT_REQUEST 예외")
        void 다른_사용자의_결제() {
            // given
            Payment payment = createReadyPayment();
            PaymentConfirmRequest request = new PaymentConfirmRequest(
                PAYMENT_KEY, PAYMENT_ID, EXTERNAL_ORDER_ID, orderInfo.totalAmount());

            given(commerceInternalClient.getOrderInfo(EXTERNAL_ORDER_ID)).willReturn(orderInfo);
            given(paymentRepository.findByOrderId(orderInfo.id())).willReturn(Optional.of(payment));

            // when & then
            assertThatThrownBy(() -> paymentService.confirmPgPayment(OTHER_USER_ID, request))
                .isInstanceOf(PaymentException.class)
                .extracting(e -> ((PaymentException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.INVALID_PAYMENT_REQUEST);

            verify(pgPaymentClient, never()).confirm(any());
        }

        @Test
        @DisplayName("이미 처리된 결제 — ALREADY_PROCESSED_PAYMENT 예외")
        void 이미_처리된_결제() {
            // given
            Payment payment = createReadyPayment();
            payment.approve(PAYMENT_KEY);
            PaymentConfirmRequest request = new PaymentConfirmRequest(
                PAYMENT_KEY, PAYMENT_ID, EXTERNAL_ORDER_ID, orderInfo.totalAmount());

            given(commerceInternalClient.getOrderInfo(EXTERNAL_ORDER_ID)).willReturn(orderInfo);
            given(paymentRepository.findByOrderId(orderInfo.id())).willReturn(Optional.of(payment));

            // when & then
            assertThatThrownBy(() -> paymentService.confirmPgPayment(USER_ID, request))
                .isInstanceOf(PaymentException.class)
                .extracting(e -> ((PaymentException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.ALREADY_PROCESSED_PAYMENT);

            verify(pgPaymentClient, never()).confirm(any());
        }

        @Test
        @DisplayName("요청 금액 불일치 — INVALID_PAYMENT_REQUEST 예외")
        void 요청_금액_불일치() {
            // given
            Payment payment = createReadyPayment();
            PaymentConfirmRequest request = new PaymentConfirmRequest(
                PAYMENT_KEY, PAYMENT_ID, EXTERNAL_ORDER_ID, 99999);

            given(commerceInternalClient.getOrderInfo(EXTERNAL_ORDER_ID)).willReturn(orderInfo);
            given(paymentRepository.findByOrderId(orderInfo.id())).willReturn(Optional.of(payment));

            // when & then
            assertThatThrownBy(() -> paymentService.confirmPgPayment(USER_ID, request))
                .isInstanceOf(PaymentException.class)
                .extracting(e -> ((PaymentException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.INVALID_PAYMENT_REQUEST);

            verify(pgPaymentClient, never()).confirm(any());
        }

        @Test
        @DisplayName("PG 승인 실패 — PaymentException 전파")
        void PG_승인_실패() {
            // given
            Payment payment = createReadyPayment();
            PaymentConfirmRequest request = new PaymentConfirmRequest(
                PAYMENT_KEY, PAYMENT_ID, EXTERNAL_ORDER_ID, orderInfo.totalAmount());

            given(commerceInternalClient.getOrderInfo(EXTERNAL_ORDER_ID)).willReturn(orderInfo);
            given(paymentRepository.findByOrderId(orderInfo.id())).willReturn(Optional.of(payment));
            given(pgPaymentClient.confirm(any()))
                .willThrow(new PaymentException(PaymentErrorCode.PG_CONFIRM_FAILED));

            // when & then
            assertThatThrownBy(() -> paymentService.confirmPgPayment(USER_ID, request))
                .isInstanceOf(PaymentException.class)
                .extracting(e -> ((PaymentException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.PG_CONFIRM_FAILED);

            verify(outboxService, never()).save(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("PG 승인 성공 후 Outbox에 payment.completed 이벤트 저장")
        void PG_승인_성공_후_Outbox_저장() {
            // given
            Payment payment = createReadyPayment();
            PaymentConfirmRequest request = new PaymentConfirmRequest(
                PAYMENT_KEY, PAYMENT_ID, EXTERNAL_ORDER_ID, orderInfo.totalAmount());

            given(commerceInternalClient.getOrderInfo(EXTERNAL_ORDER_ID)).willReturn(orderInfo);
            given(paymentRepository.findByOrderId(orderInfo.id())).willReturn(Optional.of(payment));
            given(pgPaymentClient.confirm(any())).willReturn(createPgConfirmResult());
            given(paymentRepository.save(any(Payment.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

            // when
            paymentService.confirmPgPayment(USER_ID, request);

            // then
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            verify(outboxService).save(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("confirmPgPayment → PaymentCompletedEvent 에 orderItems가 포함된다")
        void confirmPgPayment_orderItems_Outbox_전달() {
            // given
            Payment payment = createReadyPayment();
            UUID eventId1 = UUID.randomUUID();
            UUID eventId2 = UUID.randomUUID();
            InternalOrderInfoResponse orderWithItems = new InternalOrderInfoResponse(
                ORDER_ID, USER_ID, "ORD-001", orderInfo.totalAmount(), "PAYMENT_PENDING",
                LocalDateTime.of(2025, 8, 15, 14, 30).toString(),
                List.of(
                    new InternalOrderInfoResponse.OrderItem(eventId1, 2),
                    new InternalOrderInfoResponse.OrderItem(eventId2, 1)
                )
            );
            PaymentConfirmRequest request = new PaymentConfirmRequest(
                PAYMENT_KEY, PAYMENT_ID, EXTERNAL_ORDER_ID, orderInfo.totalAmount());

            given(commerceInternalClient.getOrderInfo(EXTERNAL_ORDER_ID)).willReturn(orderWithItems);
            given(paymentRepository.findByOrderId(orderWithItems.id())).willReturn(Optional.of(payment));
            given(pgPaymentClient.confirm(any())).willReturn(createPgConfirmResult());
            given(paymentRepository.save(any(Payment.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

            // when
            paymentService.confirmPgPayment(USER_ID, request);

            // then: outboxService.save에 전달된 PaymentCompletedEvent 페이로드 검증
            ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
            verify(outboxService).save(any(), any(), any(), any(), payloadCaptor.capture());

            Object captured = payloadCaptor.getValue();
            assertThat(captured).isInstanceOf(PaymentCompletedEvent.class);
            PaymentCompletedEvent event = (PaymentCompletedEvent) captured;
            assertThat(event.orderItems()).hasSize(2);
            assertThat(event.orderItems().get(0).eventId()).isEqualTo(eventId1);
            assertThat(event.orderItems().get(0).quantity()).isEqualTo(2);
            assertThat(event.orderItems().get(1).eventId()).isEqualTo(eventId2);
            assertThat(event.orderItems().get(1).quantity()).isEqualTo(1);
        }

        @Test
        @DisplayName("WALLET_PG 결제 승인 시에도 orderItems가 PaymentCompletedEvent에 포함된다")
        void WALLET_PG_confirmPgPayment_orderItems_포함() {
            // given: WALLET_PG 결제 — totalAmount=100,000, walletAmount=30,000, pgAmount=70,000
            int totalAmount = 100_000;
            int walletAmount = 30_000;
            int pgAmount = 70_000;
            Payment walletPgPayment = Payment.create(
                ORDER_ID, USER_ID, PaymentMethod.WALLET_PG, totalAmount, walletAmount, pgAmount
            );

            UUID eventId = UUID.randomUUID();
            InternalOrderInfoResponse orderWithItems = new InternalOrderInfoResponse(
                ORDER_ID, USER_ID, "ORD-WPG-001", totalAmount, "PAYMENT_PENDING",
                LocalDateTime.of(2025, 8, 15, 14, 30).toString(),
                List.of(new InternalOrderInfoResponse.OrderItem(eventId, 5))
            );
            // WALLET_PG: request.amount()는 pgAmount와 비교됨
            PaymentConfirmRequest request = new PaymentConfirmRequest(
                PAYMENT_KEY, PAYMENT_ID, EXTERNAL_ORDER_ID, pgAmount);

            given(commerceInternalClient.getOrderInfo(EXTERNAL_ORDER_ID)).willReturn(orderWithItems);
            given(paymentRepository.findByOrderId(orderWithItems.id())).willReturn(Optional.of(walletPgPayment));
            given(pgPaymentClient.confirm(any())).willReturn(new PgPaymentConfirmResult(
                PAYMENT_KEY, EXTERNAL_ORDER_ID.toString(), "카드", "DONE", pgAmount, APPROVED_AT
            ));
            given(paymentRepository.save(any(Payment.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

            // when
            paymentService.confirmPgPayment(USER_ID, request);

            // then: totalAmount는 payment.getAmount()(총액), orderItems는 그대로 전달
            ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
            verify(outboxService).save(any(), any(), any(), any(), payloadCaptor.capture());

            PaymentCompletedEvent event = (PaymentCompletedEvent) payloadCaptor.getValue();
            assertThat(event.paymentMethod()).isEqualTo(PaymentMethod.WALLET_PG);
            assertThat(event.totalAmount()).isEqualTo(totalAmount);
            assertThat(event.orderItems()).hasSize(1);
            assertThat(event.orderItems().get(0).eventId()).isEqualTo(eventId);
            assertThat(event.orderItems().get(0).quantity()).isEqualTo(5);
        }

        @Test
        @DisplayName("order.orderItems가 null이면 빈 리스트로 매핑된다")
        void confirmPgPayment_orderItems_null_빈_리스트() {
            // given
            Payment payment = createReadyPayment();
            InternalOrderInfoResponse orderWithNullItems = new InternalOrderInfoResponse(
                ORDER_ID, USER_ID, "ORD-001", orderInfo.totalAmount(), "PAYMENT_PENDING",
                LocalDateTime.of(2025, 8, 15, 14, 30).toString(),
                null
            );
            PaymentConfirmRequest request = new PaymentConfirmRequest(
                PAYMENT_KEY, PAYMENT_ID, EXTERNAL_ORDER_ID, orderInfo.totalAmount());

            given(commerceInternalClient.getOrderInfo(EXTERNAL_ORDER_ID)).willReturn(orderWithNullItems);
            given(paymentRepository.findByOrderId(orderWithNullItems.id())).willReturn(Optional.of(payment));
            given(pgPaymentClient.confirm(any())).willReturn(createPgConfirmResult());
            given(paymentRepository.save(any(Payment.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

            // when
            paymentService.confirmPgPayment(USER_ID, request);

            // then
            ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
            verify(outboxService).save(any(), any(), any(), any(), payloadCaptor.capture());

            PaymentCompletedEvent event = (PaymentCompletedEvent) payloadCaptor.getValue();
            assertThat(event.orderItems()).isNotNull().isEmpty();
        }
    }

    // =========================================================
    // failPgPayment
    // =========================================================

    @Nested
    @DisplayName("PG 결제 실패 처리")
    class FailPgPaymentTest {

        @Test
        @DisplayName("성공 — payment가 FAILED 상태, failureReason 저장, Outbox에 payment.failed 저장")
        void 성공() {
            // given
            Payment payment = createReadyPayment();
            PaymentFailRequest request = new PaymentFailRequest(
                EXTERNAL_ORDER_ID, "PAY_PROCESS_CANCELED", "사용자가 결제를 취소했습니다.");

            given(commerceInternalClient.getOrderInfo(EXTERNAL_ORDER_ID)).willReturn(orderInfo);
            given(paymentRepository.findByOrderId(orderInfo.id())).willReturn(Optional.of(payment));
            given(paymentRepository.save(any(Payment.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

            // when
            PaymentFailResponse response = paymentService.failPgPayment(USER_ID, request);

            // then
            assertThat(response.status()).isEqualTo(PaymentStatus.FAILED);
            assertThat(response.failureReason()).isEqualTo("[PAY_PROCESS_CANCELED] 사용자가 결제를 취소했습니다.");
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            verify(outboxService).save(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("결제 정보 미존재 — INVALID_PAYMENT_REQUEST 예외")
        void 결제_정보_미존재() {
            // given
            PaymentFailRequest request = new PaymentFailRequest(EXTERNAL_ORDER_ID, null, null);

            given(commerceInternalClient.getOrderInfo(EXTERNAL_ORDER_ID)).willReturn(orderInfo);
            given(paymentRepository.findByOrderId(orderInfo.id())).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> paymentService.failPgPayment(USER_ID, request))
                .isInstanceOf(PaymentException.class)
                .extracting(e -> ((PaymentException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.INVALID_PAYMENT_REQUEST);
        }

        @Test
        @DisplayName("다른 사용자의 결제 — INVALID_PAYMENT_REQUEST 예외")
        void 다른_사용자의_결제() {
            // given
            Payment payment = createReadyPayment();
            PaymentFailRequest request = new PaymentFailRequest(EXTERNAL_ORDER_ID, null, null);

            given(commerceInternalClient.getOrderInfo(EXTERNAL_ORDER_ID)).willReturn(orderInfo);
            given(paymentRepository.findByOrderId(orderInfo.id())).willReturn(Optional.of(payment));

            // when & then
            assertThatThrownBy(() -> paymentService.failPgPayment(OTHER_USER_ID, request))
                .isInstanceOf(PaymentException.class)
                .extracting(e -> ((PaymentException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.INVALID_PAYMENT_REQUEST);

            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("이미 처리된 결제 — ALREADY_PROCESSED_PAYMENT 예외")
        void 이미_처리된_결제() {
            // given
            Payment payment = createReadyPayment();
            payment.approve(PAYMENT_KEY);
            PaymentFailRequest request = new PaymentFailRequest(EXTERNAL_ORDER_ID, null, null);

            given(commerceInternalClient.getOrderInfo(EXTERNAL_ORDER_ID)).willReturn(orderInfo);
            given(paymentRepository.findByOrderId(orderInfo.id())).willReturn(Optional.of(payment));

            // when & then
            assertThatThrownBy(() -> paymentService.failPgPayment(USER_ID, request))
                .isInstanceOf(PaymentException.class)
                .extracting(e -> ((PaymentException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.ALREADY_PROCESSED_PAYMENT);

            verify(paymentRepository, never()).save(any());
        }

        @Nested
        @DisplayName("failureReason 구성")
        class FailureReasonTest {

            @Test
            @DisplayName("code + message 모두 있을 때 — [code] message 형식")
            void code와_message_모두_있을_때() {
                // given
                Payment payment = createReadyPayment();
                PaymentFailRequest request = new PaymentFailRequest(
                    EXTERNAL_ORDER_ID, "PAY_PROCESS_CANCELED", "사용자가 취소했습니다.");

                given(commerceInternalClient.getOrderInfo(EXTERNAL_ORDER_ID)).willReturn(orderInfo);
                given(paymentRepository.findByOrderId(orderInfo.id())).willReturn(Optional.of(payment));
                given(paymentRepository.save(any())).willAnswer(i -> i.getArgument(0));

                // when
                PaymentFailResponse response = paymentService.failPgPayment(USER_ID, request);

                // then
                assertThat(response.failureReason()).isEqualTo("[PAY_PROCESS_CANCELED] 사용자가 취소했습니다.");
            }

            @Test
            @DisplayName("code만 있을 때 — [code] 형식")
            void code만_있을_때() {
                // given
                Payment payment = createReadyPayment();
                PaymentFailRequest request = new PaymentFailRequest(
                    EXTERNAL_ORDER_ID, "PAY_PROCESS_CANCELED", null);

                given(commerceInternalClient.getOrderInfo(EXTERNAL_ORDER_ID)).willReturn(orderInfo);
                given(paymentRepository.findByOrderId(orderInfo.id())).willReturn(Optional.of(payment));
                given(paymentRepository.save(any())).willAnswer(i -> i.getArgument(0));

                // when
                PaymentFailResponse response = paymentService.failPgPayment(USER_ID, request);

                // then
                assertThat(response.failureReason()).isEqualTo("[PAY_PROCESS_CANCELED]");
            }

            @Test
            @DisplayName("message만 있을 때 — message 그대로")
            void message만_있을_때() {
                // given
                Payment payment = createReadyPayment();
                PaymentFailRequest request = new PaymentFailRequest(
                    EXTERNAL_ORDER_ID, null, "사용자가 취소했습니다.");

                given(commerceInternalClient.getOrderInfo(EXTERNAL_ORDER_ID)).willReturn(orderInfo);
                given(paymentRepository.findByOrderId(orderInfo.id())).willReturn(Optional.of(payment));
                given(paymentRepository.save(any())).willAnswer(i -> i.getArgument(0));

                // when
                PaymentFailResponse response = paymentService.failPgPayment(USER_ID, request);

                // then
                assertThat(response.failureReason()).isEqualTo("사용자가 취소했습니다.");
            }

            @Test
            @DisplayName("code·message 모두 없을 때 — 기본 메시지")
            void code와_message_모두_없을_때() {
                // given
                Payment payment = createReadyPayment();
                PaymentFailRequest request = new PaymentFailRequest(EXTERNAL_ORDER_ID, null, null);

                given(commerceInternalClient.getOrderInfo(EXTERNAL_ORDER_ID)).willReturn(orderInfo);
                given(paymentRepository.findByOrderId(orderInfo.id())).willReturn(Optional.of(payment));
                given(paymentRepository.save(any())).willAnswer(i -> i.getArgument(0));

                // when
                PaymentFailResponse response = paymentService.failPgPayment(USER_ID, request);

                // then
                assertThat(response.failureReason()).isEqualTo("PG 결제 실패");
            }
        }
    }

    // =========================================================
    // failPgPayment — orderItems 매핑
    // =========================================================

    @Nested
    @DisplayName("PG 결제 실패 — orderItems 매핑")
    class FailPgPaymentOrderItemsTest {

        @Test
        @DisplayName("orderItems가 있으면 PaymentFailedEvent에 매핑")
        void orderItems_매핑_성공() {
            // given
            Payment payment = createReadyPayment();
            InternalOrderInfoResponse orderWithItems = new InternalOrderInfoResponse(
                ORDER_ID, USER_ID, "ORD-001", 130000, "PAYMENT_PENDING",
                LocalDateTime.of(2025, 8, 15, 14, 30).toString(),
                List.of(
                    new InternalOrderInfoResponse.OrderItem(UUID.randomUUID(), 2),
                    new InternalOrderInfoResponse.OrderItem(UUID.randomUUID(), 1)
                )
            );
            PaymentFailRequest request = new PaymentFailRequest(
                EXTERNAL_ORDER_ID, "STOCK_FAIL", "stock shortage");

            given(commerceInternalClient.getOrderInfo(EXTERNAL_ORDER_ID)).willReturn(orderWithItems);
            given(paymentRepository.findByOrderId(orderWithItems.id())).willReturn(Optional.of(payment));
            given(paymentRepository.save(any())).willAnswer(i -> i.getArgument(0));

            // when
            paymentService.failPgPayment(USER_ID, request);

            // then
            verify(outboxService).save(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("orderItems가 null이면 빈 리스트로 매핑")
        void orderItems_null이면_빈_리스트() {
            // given
            Payment payment = createReadyPayment();
            InternalOrderInfoResponse orderWithNullItems = new InternalOrderInfoResponse(
                ORDER_ID, USER_ID, "ORD-001", 130000, "PAYMENT_PENDING",
                LocalDateTime.of(2025, 8, 15, 14, 30).toString(),
                null
            );
            PaymentFailRequest request = new PaymentFailRequest(
                EXTERNAL_ORDER_ID, "ERROR", "internal error");

            given(commerceInternalClient.getOrderInfo(EXTERNAL_ORDER_ID)).willReturn(orderWithNullItems);
            given(paymentRepository.findByOrderId(orderWithNullItems.id())).willReturn(Optional.of(payment));
            given(paymentRepository.save(any())).willAnswer(i -> i.getArgument(0));

            // when
            paymentService.failPgPayment(USER_ID, request);

            // then
            verify(outboxService).save(any(), any(), any(), any(), any());
        }
    }

    // =========================================================
    // getPaymentByOrderId
    // =========================================================

    @Nested
    @DisplayName("주문 ID로 결제 정보 조회")
    class GetPaymentByOrderIdTest {

        @Test
        @DisplayName("성공 — 결제 정보 반환")
        void 주문_ID로_결제_정보_조회성공() {
            // given
            Payment payment = createReadyPayment();
            given(paymentRepository.findByOrderId(orderInfo.id())).willReturn(Optional.of(payment));

            // when
            InternalPaymentInfoResponse response = paymentService.getPaymentByOrderId(orderInfo.id());

            // then
            assertThat(response.orderId()).isEqualTo(orderInfo.id());
            assertThat(response.amount()).isEqualTo(orderInfo.totalAmount());
            assertThat(response.status()).isEqualTo(PaymentStatus.READY.name());
        }

        @Test
        @DisplayName("결제 정보 미존재 — INVALID_PAYMENT_REQUEST 예외")
        void 결제_정보_미존재() {
            // given
            given(paymentRepository.findByOrderId(orderInfo.id())).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> paymentService.getPaymentByOrderId(orderInfo.id()))
                .isInstanceOf(PaymentException.class)
                .extracting(e -> ((PaymentException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.INVALID_PAYMENT_REQUEST);
        }
    }
}
