package com.devticket.payment.refund.application.service;

import com.devticket.payment.payment.application.dto.PgPaymentCancelCommand;
import com.devticket.payment.payment.application.dto.PgPaymentCancelResult;
import com.devticket.payment.payment.domain.enums.PaymentMethod;
import com.devticket.payment.payment.domain.enums.PaymentStatus;
import com.devticket.payment.payment.domain.exception.PaymentException;
import com.devticket.payment.payment.domain.model.Payment;
import com.devticket.payment.payment.domain.repository.PaymentRepository;
import com.devticket.payment.payment.infrastructure.client.CommerceInternalClient;
import com.devticket.payment.payment.infrastructure.client.dto.InternalOrderItemInfoResponse;
import com.devticket.payment.payment.infrastructure.external.PgPaymentClient;
import com.devticket.payment.refund.domain.RefundPolicyConstants;
import com.devticket.payment.refund.domain.RefundRateConstants;
import com.devticket.payment.refund.domain.exception.RefundErrorCode;
import com.devticket.payment.refund.domain.exception.RefundException;
import com.devticket.payment.refund.domain.model.Refund;
import com.devticket.payment.refund.domain.repository.RefundRepository;
import com.devticket.payment.refund.infrastructure.client.EventInternalClient;
import com.devticket.payment.refund.infrastructure.client.dto.InternalEventInfoResponse;
import com.devticket.payment.refund.presentation.dto.RefundInfoResponse;
import com.devticket.payment.refund.presentation.dto.PgRefundRequest;
import com.devticket.payment.refund.presentation.dto.PgRefundResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefundServiceImpl implements RefundService {

    private final CommerceInternalClient commerceInternalClient;
    private final EventInternalClient eventInternalClient;
    private final PaymentRepository paymentRepository;
    private final PgPaymentClient pgPaymentClient;
    private final RefundRepository refundRepository;

    public RefundInfoResponse getRefundInfo(UUID userId, String ticketId) {

        InternalOrderItemInfoResponse orderItem = getOrderItemInfo(ticketId);
        InternalEventInfoResponse event = getEventInfo(orderItem.eventId());

        // 결제 정보 조회
        Payment payment = paymentRepository.findByOrderId(orderItem.orderId())
            .orElseThrow(() -> new PaymentException(RefundErrorCode.PAYMENT_NOT_FOUND));

        // 소유자 검증
        validateOrderOwner(orderItem.userId(), userId);

        // 환불 정책 계산
        LocalDateTime eventDateTime = LocalDateTime.parse(event.eventDateTime());
        long dDay = ChronoUnit.DAYS.between(LocalDate.now(), eventDateTime.toLocalDate());
        int refundRate = calculateRefundRate(eventDateTime);
        int refundAmount = calculateRefundAmount(orderItem.amount(), refundRate);

        boolean refundable = isRefundable(eventDateTime) && isPaymentRefundable(payment);

        return new RefundInfoResponse(
            ticketId,
            event.title(),
            eventDateTime,
            orderItem.amount(),
            refundAmount,
            refundRate,
            dDay,
            refundable,
            payment.getPaymentMethod().toString()
        );
    }

    // =========================================================
    // 환불 요청
    // =========================================================

    @Override
    public PgRefundResponse refundPgTicket(UUID userId, String ticketId, PgRefundRequest request) {
        InternalOrderItemInfoResponse orderItem = getOrderItemInfo(ticketId);
        validateOrderOwner(orderItem.userId(), userId);

        InternalEventInfoResponse event = getEventInfo(orderItem.eventId());
        LocalDateTime eventDateTime = LocalDateTime.parse(event.eventDateTime());

        Payment payment = paymentRepository.findByOrderId(orderItem.orderId())
            .orElseThrow(() -> new RefundException(RefundErrorCode.PAYMENT_NOT_FOUND));

        validatePgPayment(payment);
        validateRefundablePaymentStatus(payment);

        int refundRate = calculateRefundRate(eventDateTime);
        validateRefundPolicy(refundRate);

        int refundAmount = calculateRefundAmount(orderItem.amount(), refundRate);

        Refund refund = Refund.create(
            orderItem.orderId(),
            payment.getId(),
            userId,
            refundAmount,
            refundRate
        );

        try {
            PgPaymentCancelResult cancelResult = pgPaymentClient.cancelPartial(
                new PgPaymentCancelCommand(
                    payment.getPaymentKey(),
                    refundAmount,
                    request.reason()
                )
            );
            refund.complete(parseCanceledAt(cancelResult.canceledAt()));
            refundRepository.save(refund);

        } catch (Exception e) {
            refund.fail();
            refundRepository.save(refund);
            throw new RefundException(RefundErrorCode.PG_REFUND_FAILED);
        }

        //환불 완료 처리
        try {
            commerceInternalClient.completeRefund(ticketId);
        } catch (Exception e) {
            log.error("Commerce 환불 완료 통보 실패 — 수동 처리 필요: ticketId={}, refundAmount={}",
                ticketId, refundAmount, e);
        }

        return PgRefundResponse.of(
            ticketId,
            orderItem.orderId(),
            orderItem.amount(),
            refundAmount,
            refundRate,
            payment.getPaymentMethod().name(),
            refund.getStatus().name(),
            refund.getCompletedAt()
        );
    }

    private InternalOrderItemInfoResponse getOrderItemInfo(String ticketId) {
        try {
            return commerceInternalClient.getOrderItemInfoByTicketId(ticketId);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new RefundException(RefundErrorCode.TICKET_NOT_FOUND);
            }
            throw new RefundException(RefundErrorCode.REFUND_INVALID_REQUEST);
        }
    }

    private InternalEventInfoResponse getEventInfo(Long eventId) {
        try {
            return eventInternalClient.getEventInfo(eventId);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new RefundException(RefundErrorCode.EVENT_NOT_FOUND);
            }
            throw new RefundException(RefundErrorCode.REFUND_INVALID_REQUEST);
        }
    }

    private int calculateRefundRate(LocalDateTime eventDateTime) {
        long dDay = ChronoUnit.DAYS.between(LocalDate.now(), eventDateTime.toLocalDate());
        if (dDay >= RefundPolicyConstants.FULL_REFUND_DEADLINE_DAYS) return RefundRateConstants.FULL;
        if (dDay >= RefundPolicyConstants.HALF_REFUND_DEADLINE_DAYS) return RefundRateConstants.HALF;
        return RefundRateConstants.NONE;
    }

    private int calculateRefundAmount(int amount, int refundRate) {
        return amount * refundRate/100;
    }

    private boolean isRefundable(LocalDateTime eventDateTime) {
        return calculateRefundRate(eventDateTime) > 0;
    }

    private boolean isPaymentRefundable(Payment payment) {
        return payment.getStatus() == PaymentStatus.SUCCESS;
    }

    private void validateOrderOwner(UUID orderUserId, UUID userId) {
        if (!orderUserId.equals(userId)) {
            throw new RefundException(RefundErrorCode.REFUND_INVALID_REQUEST);
        }
    }

    private void validatePgPayment(Payment payment) {
        if (payment.getPaymentMethod() != PaymentMethod.PG) {
            throw new RefundException(RefundErrorCode.REFUND_INVALID_REQUEST);
        }
    }

    private void validateRefundablePaymentStatus(Payment payment) {
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new RefundException(RefundErrorCode.REFUND_INVALID_REQUEST);
        }
    }

    private void validateRefundPolicy(int refundRate) {
        if (refundRate <= 0) {
            throw new RefundException(RefundErrorCode.REFUND_NOT_AVAILABLE);
        }
    }

    private LocalDateTime parseCanceledAt(String canceledAt) {
        return OffsetDateTime.parse(canceledAt).toLocalDateTime();
    }
}