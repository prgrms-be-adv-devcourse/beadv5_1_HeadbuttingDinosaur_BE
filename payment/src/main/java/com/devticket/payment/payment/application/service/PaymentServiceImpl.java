package com.devticket.payment.payment.application.service;

import com.devticket.payment.common.exception.BusinessException;
import com.devticket.payment.payment.application.dto.PgPaymentConfirmCommand;
import com.devticket.payment.payment.application.dto.PgPaymentConfirmResult;
import com.devticket.payment.payment.domain.enums.PaymentMethod;
import com.devticket.payment.payment.domain.enums.PaymentStatus;
import com.devticket.payment.payment.domain.exception.PaymentErrorCode;
import com.devticket.payment.payment.domain.exception.PaymentException;
import com.devticket.payment.payment.domain.model.Payment;
import com.devticket.payment.payment.domain.repository.PaymentRepository;
import com.devticket.payment.payment.infrastructure.client.CommerceInternalClient;
import com.devticket.payment.payment.infrastructure.client.dto.InternalOrderInfoResponse;
import com.devticket.payment.payment.infrastructure.external.PgPaymentClient;
import com.devticket.payment.payment.presentation.dto.PaymentConfirmRequest;
import com.devticket.payment.payment.presentation.dto.PaymentConfirmResponse;
import com.devticket.payment.payment.presentation.dto.PaymentFailRequest;
import com.devticket.payment.payment.presentation.dto.PaymentFailResponse;
import com.devticket.payment.payment.presentation.dto.PaymentReadyRequest;
import com.devticket.payment.payment.presentation.dto.PaymentReadyResponse;
import com.devticket.payment.wallet.domain.exception.WalletErrorCode;
import com.devticket.payment.wallet.domain.exception.WalletException;
import com.devticket.payment.wallet.domain.model.Wallet;
import com.devticket.payment.wallet.domain.model.WalletTransaction;
import com.devticket.payment.wallet.domain.repository.WalletRepository;
import com.devticket.payment.wallet.domain.repository.WalletTransactionRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private static final int MAX_FAILURE_REASON_LENGTH = 255;

    private final PaymentRepository paymentRepository;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final CommerceInternalClient commerceInternalClient;
    private final PgPaymentClient pgPaymentClient;

    //결제 준비
    @Override
    @Transactional
    public PaymentReadyResponse readyPayment(UUID userId, PaymentReadyRequest request) {
        // 주문 조회 및 검증
        InternalOrderInfoResponse order = commerceInternalClient.getOrderInfo(request.orderId());
        validateOrderOwner(userId, order.userId());
        validateOrderPayable(order);

        //Payment 생성
        Payment payment = Payment.create(
            order.id(),
            userId,
            request.paymentMethod(),
            order.totalAmount()
        );

        Payment savedPayment = paymentRepository.save(payment);

        //예치금 결제일 경우 - 바로 결제
        if (request.paymentMethod() == PaymentMethod.WALLET) {
            return processWalletPayment(userId, order, request.orderId(), savedPayment);
        }

        return PaymentReadyResponse.from(
            savedPayment,
            request.orderId(),
            order.orderNumber(),
            order.status()
        );
    }

    @Override
    public PaymentConfirmResponse confirmPgPayment(UUID userId, PaymentConfirmRequest request) {
        InternalOrderInfoResponse order = commerceInternalClient.getOrderInfo(request.orderId());

        Payment payment = paymentRepository.findByOrderId(order.id())
            .orElseThrow(() -> new PaymentException(PaymentErrorCode.INVALID_PAYMENT_REQUEST));

        validateOrderOwner(payment.getUserId(), userId); // 올바른 사용자인지 확인
        validatePaymentStatus(payment, request.orderId()); // 결제 상태 검증 (중복 승인 방지)
        validatePaymentAmount(payment, request); // 금액 검증

        // PG 승인 요청
        PgPaymentConfirmResult result = confirmWithPg(request);

        // 결제 승인 상태 반영
        payment.approve(result.paymentKey(), parseApprovedAt(result.approvedAt()));
        paymentRepository.save(payment);

        // 주문 완료 처리 — 실패 시 PG 취소 후 보상
        try {
            commerceInternalClient.completePayment(payment.getOrderId());
        } catch (Exception e) {
            log.error(
                "주문 완료 처리 실패. PG 취소 보상 트랜잭션 시도: orderId={}, paymentKey={}",
                payment.getOrderId(),
                payment.getPaymentKey(),
                e
            );
            cancelPgPayment(payment);

            throw new PaymentException(PaymentErrorCode.ORDER_COMPLETE_FAILED);
        }

        return PaymentConfirmResponse.from(payment);
    }

    //예치금 결제
    private PaymentReadyResponse processWalletPayment(
        UUID userId,
        InternalOrderInfoResponse order,
        String orderId,
        Payment payment
    ) {
        Wallet wallet = walletRepository.findByUserId(userId)
            .orElseThrow(() -> new WalletException(WalletErrorCode.WALLET_NOT_FOUND));

        String transactionKey = "WALLET:PAY:" + orderId;

        boolean exists = walletTransactionRepository.existsByTransactionKey(transactionKey);
        if (exists) {
            throw new PaymentException(PaymentErrorCode.ALREADY_PROCESSED_PAYMENT);
        }

        if (wallet.getBalance() < order.totalAmount()) {
            throw new WalletException(WalletErrorCode.INSUFFICIENT_BALANCE);
        }

        //잔액 차감
        wallet.use(payment.getAmount());

        //wallet transaction 생성
        WalletTransaction walletTransaction = WalletTransaction.createUse(
            wallet.getId(),
            userId,
            transactionKey,
            payment.getAmount(),
            wallet.getBalance(),
            payment.getOrderId()
        );

        walletTransactionRepository.save(walletTransaction);

        //결제 상태 SUCCESS로 변경
        payment.approve("WALLET-" + payment.getPaymentId());

        //TODO: 주문 완료 처리 추가

        return PaymentReadyResponse.from(
            payment,
            orderId,
            order.orderNumber(),
            order.status()
        );
    }

    //올바른 사용자인지 확인
    private void validateOrderOwner(UUID requestUserId, UUID orderUserId) {
        if (!requestUserId.equals(orderUserId)) {
            throw new PaymentException(PaymentErrorCode.INVALID_PAYMENT_REQUEST);
        }
    }

    //주문이 결제 가능 상태인지 확인
    private void validateOrderPayable(InternalOrderInfoResponse order) {
        if (!order.status().equals("PAYMENT_PENDING")) {
            throw new BusinessException(PaymentErrorCode.INVALID_PAYMENT_REQUEST);
        }
    }

    // 결제 상태 검증 (중복 승인 방지)
    private void validatePaymentStatus(Payment payment, String orderId) {
        if (payment.getStatus() != PaymentStatus.READY) {
            log.warn("이미 처리된 결제 요청: orderId={}, status={}", orderId, payment.getStatus());
            throw new PaymentException(PaymentErrorCode.ALREADY_PROCESSED_PAYMENT);
        }
    }

    // 금액 검증
    private void validatePaymentAmount(Payment payment, PaymentConfirmRequest request) {
        if (!payment.getAmount().equals(request.amount())) {
            log.warn("결제 금액 불일치: expected={}, actual={}, orderId={}",
                payment.getAmount(), request.amount(), request.orderId());
            throw new PaymentException(PaymentErrorCode.INVALID_PAYMENT_REQUEST);
        }
    }

    @Override
    @Transactional
    public PaymentFailResponse failPgPayment(UUID userId, PaymentFailRequest request) {
        InternalOrderInfoResponse order = commerceInternalClient.getOrderInfo(request.orderId());

        Payment payment = paymentRepository.findByOrderId(order.id())
            .orElseThrow(() -> new PaymentException(PaymentErrorCode.INVALID_PAYMENT_REQUEST));

        validateOrderOwner(payment.getUserId(), userId);
        validatePaymentStatus(payment, request.orderId());

        String reason = buildFailureReason(request.code(), request.message());
        payment.fail(reason);
        paymentRepository.save(payment);

        log.info("PG 결제 실패 처리 완료: orderId={}, code={}, message={}",
            request.orderId(), request.code(), request.message());

        try {
            commerceInternalClient.failOrder(order.id());
        } catch (Exception e) {
            log.error("주문 실패 처리 중 Commerce 연동 오류: orderId={}", order.id(), e);
        }

        return PaymentFailResponse.from(payment);
    }

    private String buildFailureReason(String code, String message) {
        String reason;

        if (code != null && message != null) {
            reason = "[" + code + "] " + message;
        } else if (code != null) {
            reason = "[" + code + "]";
        } else if (message != null) {
            reason = message;
        } else {
            reason = "PG 결제 실패";
        }

        // 255자 초과 시 절단
        if (reason.length() > MAX_FAILURE_REASON_LENGTH) {
            return reason.substring(0, MAX_FAILURE_REASON_LENGTH);
        }
        return reason;
    }

    // PG 승인 요청
    private PgPaymentConfirmResult confirmWithPg(PaymentConfirmRequest request) {
        return pgPaymentClient.confirm(
            new PgPaymentConfirmCommand(
                request.paymentKey(),
                request.orderId(),
                request.amount()
            )
        );
    }

    private void cancelPgPayment(Payment payment) {
        try {
            pgPaymentClient.cancel(payment.getPaymentKey(), "주문 완료 처리 실패로 인한 자동 취소");

            payment.fail("주문 완료 처리 실패로 승인 후 자동 취소됨");
            paymentRepository.save(payment);

            log.warn(
                "PG 자동 취소 성공: orderId={}, paymentKey={}",
                payment.getOrderId(),
                payment.getPaymentKey()
            );
        } catch (Exception e) {
            log.error(
                "PG 자동 취소 실패 - 수동 확인 필요: orderId={}, paymentKey={}",
                payment.getOrderId(),
                payment.getPaymentKey(),
                e
            );

            payment.fail("주문 완료 처리 실패 및 PG 자동 취소 실패");
            paymentRepository.save(payment);

            throw new PaymentException(PaymentErrorCode.PG_REFUND_FAILED);
        }
    }


    private LocalDateTime parseApprovedAt(String approvedAt) {
        try {
            return LocalDateTime.parse(approvedAt, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (Exception e) {
            log.error("approvedAt 파싱 실패: {}", approvedAt);
            throw new PaymentException(PaymentErrorCode.PG_CONFIRM_FAILED);
        }
    }
}
