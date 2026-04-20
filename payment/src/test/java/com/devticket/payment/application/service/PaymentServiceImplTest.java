package com.devticket.payment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
            Payment savedPayment = createReadyPayment();
            savedPayment.approve("WALLET-" + savedPayment.getPaymentId());

            given(commerceInternalClient.getOrderInfo(EXTERNAL_ORDER_ID)).willReturn(orderInfo);
            given(paymentRepository.save(any(Payment.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
            given(paymentRepository.findByOrderId(orderInfo.id()))
                .willReturn(Optional.empty())
                .willReturn(Optional.of(savedPayment));

            // when
            PaymentReadyResponse response = paymentService.readyPayment(USER_ID, request);

            // then
            verify(walletService).processWalletPayment(USER_ID, EXTERNAL_ORDER_ID, orderInfo.totalAmount());
            assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
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
