package com.devticket.payment.payment.application.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devticket.payment.common.exception.BusinessException;
import com.devticket.payment.common.messaging.KafkaTopics;
import com.devticket.payment.common.outbox.OutboxService;
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
import com.devticket.payment.payment.presentation.dto.InternalPaymentInfoResponse;
import com.devticket.payment.payment.presentation.dto.PaymentConfirmRequest;
import com.devticket.payment.payment.presentation.dto.PaymentConfirmResponse;
import com.devticket.payment.payment.presentation.dto.PaymentFailRequest;
import com.devticket.payment.payment.presentation.dto.PaymentFailResponse;
import com.devticket.payment.payment.presentation.dto.PaymentReadyRequest;
import com.devticket.payment.payment.presentation.dto.PaymentReadyResponse;
import com.devticket.payment.wallet.application.event.PaymentCompletedEvent;
import com.devticket.payment.wallet.application.event.PaymentFailedEvent;
import com.devticket.payment.wallet.application.service.WalletService;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PaymentServiceImpl implements PaymentService {

    private static final int MAX_FAILURE_REASON_LENGTH = 255;

    private final PaymentRepository paymentRepository;
    private final CommerceInternalClient commerceInternalClient;
    private final PgPaymentClient pgPaymentClient;
    private final OutboxService outboxService;
    private final WalletService walletService;

    //결제 준비
    @Override
    @Transactional
    public PaymentReadyResponse readyPayment(UUID userId, PaymentReadyRequest request) {
        // 주문 조회 및 검증
        InternalOrderInfoResponse order = commerceInternalClient.getOrderInfo(request.orderId());
        validateOrderOwner(userId, order.userId());
        validateOrderPayable(order);

        // 멱등성 가드: 기존 Payment 존재 여부 확인 (PG/WALLET/WALLET_PG 공통)
        Optional<Payment> existing = paymentRepository.findByOrderId(order.id());
        if (existing.isPresent()) {
            Payment existingPayment = existing.get();
            if (existingPayment.getStatus() == PaymentStatus.READY) {
                log.info("[ReadyPayment] 기존 READY Payment 반환 — orderId={}", order.id());
                return PaymentReadyResponse.from(existingPayment, request.orderId(), order.orderNumber(), order.status());
            }
            throw new PaymentException(PaymentErrorCode.ALREADY_PROCESSED_PAYMENT);
        }

        // WALLET_PG 복합결제
        if (request.paymentMethod() == PaymentMethod.WALLET_PG) {
            return readyWalletPgPayment(userId, request, order);
        }

        // PG 결제
        if (request.paymentMethod() == PaymentMethod.PG) {
            Payment payment = Payment.create(order.id(), userId, PaymentMethod.PG, order.totalAmount());
            Payment savedPayment = paymentRepository.save(payment);
            return PaymentReadyResponse.from(savedPayment, request.orderId(), order.orderNumber(), order.status());
        }

        // WALLET 결제
        Payment payment = Payment.create(order.id(), userId, PaymentMethod.WALLET, order.totalAmount());
        paymentRepository.save(payment);
        walletService.processWalletPayment(userId, request.orderId(), order.totalAmount());
        Payment updated = paymentRepository.findByOrderId(order.id())
            .orElseThrow(() -> new PaymentException(PaymentErrorCode.INVALID_PAYMENT_REQUEST));
        return PaymentReadyResponse.from(updated, request.orderId(), order.orderNumber(), order.status());
    }

    private PaymentReadyResponse readyWalletPgPayment(UUID userId, PaymentReadyRequest request, InternalOrderInfoResponse order) {
        int totalAmount = order.totalAmount();
        int walletAmount = request.walletAmount();

        // 입력값 검증
        if (walletAmount <= 0 || walletAmount >= totalAmount) {
            throw new PaymentException(PaymentErrorCode.INVALID_PAYMENT_REQUEST);
        }

        int pgAmount = totalAmount - walletAmount;

        // 예치금 차감 (WalletTransaction USE 기록 포함)
        walletService.deductForWalletPg(userId, order.id(), walletAmount);

        // Payment 생성 (READY, WALLET_PG)
        Payment payment = Payment.create(order.id(), userId, PaymentMethod.WALLET_PG, totalAmount, walletAmount, pgAmount);
        Payment savedPayment = paymentRepository.save(payment);

        log.info("[ReadyPayment] WALLET_PG 결제 준비 — orderId={}, walletAmount={}, pgAmount={}",
            order.id(), walletAmount, pgAmount);

        return PaymentReadyResponse.from(savedPayment, request.orderId(), order.orderNumber(), order.status());
    }

    @Override
    @Transactional
    public PaymentConfirmResponse confirmPgPayment(UUID userId, PaymentConfirmRequest request) {

        InternalOrderInfoResponse order = commerceInternalClient.getOrderInfo(request.orderId());
        log.info("[Confirm Debug] order.id={}, order.status={}", order.id(), order.status());
        Payment payment = paymentRepository.findByOrderId(order.id())
            .orElseThrow(() -> new PaymentException(PaymentErrorCode.INVALID_PAYMENT_REQUEST));

        validateOrderOwner(payment.getUserId(), userId);
        validatePaymentStatus(payment, request.paymentId());
        validatePaymentAmount(payment, request);

        // PG 승인 요청
        PgPaymentConfirmResult result = confirmWithPg(request);

        // 결제 승인 상태 반영
        payment.approve(result.paymentKey(), parseApprovedAt(result.approvedAt()));
        paymentRepository.save(payment);

        // payment.completed Outbox 발행 (트랜잭션 내)
        PaymentCompletedEvent event = PaymentCompletedEvent.builder()
            .orderId(payment.getOrderId())
            .userId(payment.getUserId())
            .paymentId(payment.getPaymentId())
            .paymentMethod(payment.getPaymentMethod())
            .totalAmount(payment.getAmount())
            .timestamp(Instant.now())
            .build();
        outboxService.save(
            payment.getPaymentId().toString(),
            KafkaTopics.PAYMENT_COMPLETED,
            KafkaTopics.PAYMENT_COMPLETED,
            payment.getOrderId().toString(),
            event
        );

        return PaymentConfirmResponse.from(payment);
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
    private void validatePaymentStatus(Payment payment, UUID orderId) {
        if (payment.getStatus() != PaymentStatus.READY) {
            log.warn("이미 처리된 결제 요청: orderId={}, status={}", orderId, payment.getStatus());
            throw new PaymentException(PaymentErrorCode.ALREADY_PROCESSED_PAYMENT);
        }
    }

    // 금액 검증: WALLET_PG이면 pgAmount 기준, 그 외는 총액 기준
    private void validatePaymentAmount(Payment payment, PaymentConfirmRequest request) {
        int expectedAmount = payment.getPaymentMethod() == PaymentMethod.WALLET_PG
            ? payment.getPgAmount()
            : payment.getAmount();

        if (expectedAmount != request.amount()) {
            log.warn("결제 금액 불일치: expected={}, actual={}, orderId={}",
                expectedAmount, request.amount(), request.paymentId());
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

        // WALLET_PG인 경우 예치금 복구
        if (payment.getPaymentMethod() == PaymentMethod.WALLET_PG) {
            walletService.restoreForWalletPgFail(payment.getUserId(), payment.getWalletAmount(), payment.getOrderId());
            log.info("[FailPgPayment] WALLET_PG 예치금 복구 — orderId={}, walletAmount={}",
                payment.getOrderId(), payment.getWalletAmount());
        }

        log.info("PG 결제 실패 처리 완료: orderId={}, code={}, message={}",
            request.orderId(), request.code(), request.message());

        // payment.failed Outbox 발행 (트랜잭션 내)
        List<PaymentFailedEvent.OrderItem> orderItems = order.orderItems() == null
            ? List.of()
            : order.orderItems().stream()
                .map(item -> new PaymentFailedEvent.OrderItem(item.eventId(), item.quantity()))
                .toList();

        PaymentFailedEvent event = PaymentFailedEvent.builder()
            .orderId(payment.getOrderId())
            .userId(payment.getUserId())
            .orderItems(orderItems)
            .reason(reason)
            .timestamp(Instant.now())
            .build();
        outboxService.save(
            payment.getPaymentId().toString(),
            KafkaTopics.PAYMENT_FAILED,
            KafkaTopics.PAYMENT_FAILED,
            payment.getOrderId().toString(),
            event
        );

        return PaymentFailResponse.from(payment);
    }

    @Override
    public InternalPaymentInfoResponse getPaymentByOrderId(UUID orderId) {

        Payment payment = paymentRepository.findByOrderId(orderId)
            .orElseThrow(() -> new PaymentException(PaymentErrorCode.INVALID_PAYMENT_REQUEST));

        return InternalPaymentInfoResponse.from(payment);
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
                request.paymentId().toString(),
                request.amount()
            )
        );
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
