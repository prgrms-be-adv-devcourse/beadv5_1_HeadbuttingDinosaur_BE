package com.devticket.payment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
import com.devticket.payment.wallet.domain.exception.WalletException;
import com.devticket.payment.wallet.domain.model.Wallet;
import com.devticket.payment.wallet.domain.repository.WalletRepository;
import com.devticket.payment.wallet.domain.repository.WalletTransactionRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock private WalletRepository walletRepository;
    @Mock private WalletTransactionRepository walletTransactionRepository;
    @Mock private CommerceInternalClient commerceInternalClient;
    @Mock private PgPaymentClient pgPaymentClient;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID OTHER_USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
    private static final String EXTERNAL_ORDER_ID = "order-uuid-123";
    private static final String PAYMENT_KEY = "toss_pg_key_123";
    private static final String APPROVED_AT = "2024-01-01T00:00:00+09:00";

    private InternalOrderInfoResponse orderInfo;

    @BeforeEach
    void setUp() {
        orderInfo = new InternalOrderInfoResponse(
            123L,
            USER_ID,
            "ORD-20250815-001",
            130000,
            "PAYMENT_PENDING",
            LocalDateTime.of(2025, 8, 15, 14, 30).toString()
        );
    }

    private Payment createReadyPayment() {
        return Payment.create(orderInfo.id(), USER_ID, PaymentMethod.PG, orderInfo.totalAmount());
    }

    private PgPaymentConfirmResult createPgConfirmResult() {
        return new PgPaymentConfirmResult(
            PAYMENT_KEY,
            EXTERNAL_ORDER_ID,
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
            PaymentReadyRequest request = new PaymentReadyRequest(EXTERNAL_ORDER_ID, PaymentMethod.PG);

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
            PaymentReadyRequest request = new PaymentReadyRequest(EXTERNAL_ORDER_ID, PaymentMethod.PG);

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
            PaymentReadyRequest request = new PaymentReadyRequest(EXTERNAL_ORDER_ID, PaymentMethod.PG);
            InternalOrderInfoResponse paidOrder = new InternalOrderInfoResponse(
                123L, USER_ID, "ORD-001", 130000, "PAID",
                LocalDateTime.of(2025, 8, 15, 14, 30).toString()
            );

            given(commerceInternalClient.getOrderInfo(EXTERNAL_ORDER_ID)).willReturn(paidOrder);

            // when & then
            assertThatThrownBy(() -> paymentService.readyPayment(USER_ID, request))
                .isInstanceOf(RuntimeException.class);

            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("예치금 결제 준비 성공 — SUCCESS 상태, 잔액 차감")
        void 예치금_결제_준비_성공() {
            // given
            PaymentReadyRequest request = new PaymentReadyRequest(EXTERNAL_ORDER_ID, PaymentMethod.WALLET);
            Wallet wallet = Wallet.create(USER_ID);
            wallet.charge(200000);

            given(commerceInternalClient.getOrderInfo(EXTERNAL_ORDER_ID)).willReturn(orderInfo);
            given(paymentRepository.save(any(Payment.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
            given(walletRepository.findByUserId(USER_ID)).willReturn(Optional.of(wallet));
            given(walletTransactionRepository.existsByTransactionKey("WALLET:PAY:" + EXTERNAL_ORDER_ID))
                .willReturn(false);

            // when
            PaymentReadyResponse response = paymentService.readyPayment(USER_ID, request);

            // then
            assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(response.approvedAt()).isNotNull();
            assertThat(wallet.getBalance()).isEqualTo(70000);
        }

        @Test
        @DisplayName("예치금 잔액 부족 — 예외")
        void 예치금_잔액_부족() {
            // given
            PaymentReadyRequest request = new PaymentReadyRequest(EXTERNAL_ORDER_ID, PaymentMethod.WALLET);
            Wallet wallet = Wallet.create(USER_ID);
            wallet.charge(10000);

            given(commerceInternalClient.getOrderInfo(EXTERNAL_ORDER_ID)).willReturn(orderInfo);
            given(paymentRepository.save(any(Payment.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
            given(walletRepository.findByUserId(USER_ID)).willReturn(Optional.of(wallet));
            given(walletTransactionRepository.existsByTransactionKey("WALLET:PAY:" + EXTERNAL_ORDER_ID))
                .willReturn(false);

            // when & then
            assertThatThrownBy(() -> paymentService.readyPayment(USER_ID, request))
                .isInstanceOf(WalletException.class);
        }

        @Test
        @DisplayName("이미_처리된_예치금_거래 — 예외")
        void 이미_처리된_예치금_거래() {
            // given
            PaymentReadyRequest request = new PaymentReadyRequest(EXTERNAL_ORDER_ID, PaymentMethod.WALLET);
            Wallet wallet = Wallet.create(USER_ID);
            wallet.charge(200000);

            given(commerceInternalClient.getOrderInfo(EXTERNAL_ORDER_ID)).willReturn(orderInfo);
            given(paymentRepository.save(any(Payment.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
            given(walletRepository.findByUserId(USER_ID)).willReturn(Optional.of(wallet));
            given(walletTransactionRepository.existsByTransactionKey("WALLET:PAY:" + EXTERNAL_ORDER_ID))
                .willReturn(true);

            // when & then
            assertThatThrownBy(() -> paymentService.readyPayment(USER_ID, request))
                .isInstanceOf(PaymentException.class);
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
                PAYMENT_KEY, EXTERNAL_ORDER_ID, orderInfo.totalAmount());

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
            verify(commerceInternalClient).completePayment(orderInfo.id());
        }

        @Test
        @DisplayName("결제 정보 미존재 — INVALID_PAYMENT_REQUEST 예외")
        void 결제_정보_미존재() {
            // given
            PaymentConfirmRequest request = new PaymentConfirmRequest(
                PAYMENT_KEY, EXTERNAL_ORDER_ID, orderInfo.totalAmount());

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
                PAYMENT_KEY, EXTERNAL_ORDER_ID, orderInfo.totalAmount());

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
                PAYMENT_KEY, EXTERNAL_ORDER_ID, orderInfo.totalAmount());

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
                PAYMENT_KEY, EXTERNAL_ORDER_ID, 99999);

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
                PAYMENT_KEY, EXTERNAL_ORDER_ID, orderInfo.totalAmount());

            given(commerceInternalClient.getOrderInfo(EXTERNAL_ORDER_ID)).willReturn(orderInfo);
            given(paymentRepository.findByOrderId(orderInfo.id())).willReturn(Optional.of(payment));
            given(pgPaymentClient.confirm(any()))
                .willThrow(new PaymentException(PaymentErrorCode.PG_CONFIRM_FAILED));

            // when & then
            assertThatThrownBy(() -> paymentService.confirmPgPayment(USER_ID, request))
                .isInstanceOf(PaymentException.class)
                .extracting(e -> ((PaymentException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.PG_CONFIRM_FAILED);

            verify(commerceInternalClient, never()).completePayment(any());
        }

        @Test
        @DisplayName("Commerce 주문 완료 실패 + PG 취소 성공 — ORDER_COMPLETE_FAILED 예외, payment FAILED 저장")
        void Commerce_실패_PG_취소_성공() {
            // given
            Payment payment = createReadyPayment();
            PaymentConfirmRequest request = new PaymentConfirmRequest(
                PAYMENT_KEY, EXTERNAL_ORDER_ID, orderInfo.totalAmount());

            given(commerceInternalClient.getOrderInfo(EXTERNAL_ORDER_ID)).willReturn(orderInfo);
            given(paymentRepository.findByOrderId(orderInfo.id())).willReturn(Optional.of(payment));
            given(pgPaymentClient.confirm(any())).willReturn(createPgConfirmResult());
            given(paymentRepository.save(any(Payment.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
            willThrow(new RuntimeException("Commerce 오류"))
                .given(commerceInternalClient).completePayment(orderInfo.id());

            // when & then
            assertThatThrownBy(() -> paymentService.confirmPgPayment(USER_ID, request))
                .isInstanceOf(PaymentException.class)
                .extracting(e -> ((PaymentException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.ORDER_COMPLETE_FAILED);

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            verify(pgPaymentClient).cancel(any(), any());
        }

        @Test
        @DisplayName("Commerce 주문 완료 실패 + PG 취소도 실패 — PG_REFUND_FAILED 예외, payment FAILED 저장")
        void Commerce_실패_PG_취소_실패() {
            // given
            Payment payment = createReadyPayment();
            PaymentConfirmRequest request = new PaymentConfirmRequest(
                PAYMENT_KEY, EXTERNAL_ORDER_ID, orderInfo.totalAmount());

            given(commerceInternalClient.getOrderInfo(EXTERNAL_ORDER_ID)).willReturn(orderInfo);
            given(paymentRepository.findByOrderId(orderInfo.id())).willReturn(Optional.of(payment));
            given(pgPaymentClient.confirm(any())).willReturn(createPgConfirmResult());
            given(paymentRepository.save(any(Payment.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
            willThrow(new RuntimeException("Commerce 오류"))
                .given(commerceInternalClient).completePayment(orderInfo.id());
            willThrow(new PaymentException(PaymentErrorCode.PG_CANCEL_FAILED))
                .given(pgPaymentClient).cancel(any(), any());

            // when & then
            assertThatThrownBy(() -> paymentService.confirmPgPayment(USER_ID, request))
                .isInstanceOf(PaymentException.class)
                .extracting(e -> ((PaymentException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.PG_REFUND_FAILED);

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        }
    }

    // =========================================================
    // failPgPayment
    // =========================================================

    @Nested
    @DisplayName("PG 결제 실패 처리")
    class FailPgPaymentTest {

        @Test
        @DisplayName("성공 — payment가 FAILED 상태, failureReason 저장, Commerce failOrder 호출")
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
            verify(commerceInternalClient).failOrder(orderInfo.id());
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

        @Test
        @DisplayName("Commerce failOrder 실패 — 예외 없이 FAILED 상태 정상 반환")
        void Commerce_failOrder_실패_시_정상_완료() {
            // given
            Payment payment = createReadyPayment();
            PaymentFailRequest request = new PaymentFailRequest(EXTERNAL_ORDER_ID, null, null);

            given(commerceInternalClient.getOrderInfo(EXTERNAL_ORDER_ID)).willReturn(orderInfo);
            given(paymentRepository.findByOrderId(orderInfo.id())).willReturn(Optional.of(payment));
            given(paymentRepository.save(any(Payment.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
            willThrow(new RuntimeException("Commerce 오류"))
                .given(commerceInternalClient).failOrder(orderInfo.id());

            // when
            PaymentFailResponse response = paymentService.failPgPayment(USER_ID, request);

            // then — Commerce 연동 실패와 무관하게 결제 실패 처리는 완료
            assertThat(response.status()).isEqualTo(PaymentStatus.FAILED);
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
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
    // getPaymentByOrderId
    // =========================================================

    @Nested
    @DisplayName("주문 ID로 결제 정보 조회")
    class GetPaymentByOrderIdTest {

        @Test
        @DisplayName("성공 — 결제 정보 반환")
        void 성공() {
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
