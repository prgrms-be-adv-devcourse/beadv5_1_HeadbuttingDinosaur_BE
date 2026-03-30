package com.devticket.payment.refund.application.service;

import com.devticket.payment.payment.domain.exception.PaymentErrorCode;
import com.devticket.payment.payment.domain.exception.PaymentException;
import com.devticket.payment.payment.domain.model.Payment;
import com.devticket.payment.payment.domain.repository.PaymentRepository;
import com.devticket.payment.payment.infrastructure.client.CommerceInternalClient;
import com.devticket.payment.payment.infrastructure.client.dto.InternalOrderItemInfoResponse;
import com.devticket.payment.refund.domain.RefundPolicyConstants;
import com.devticket.payment.refund.domain.RefundRateConstants;
import com.devticket.payment.refund.domain.exception.RefundErrorCode;
import com.devticket.payment.refund.infrastructure.client.EventInternalClient;
import com.devticket.payment.refund.infrastructure.client.dto.InternalEventInfoResponse;
import com.devticket.payment.refund.presentation.dto.RefundInfoResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefundServiceImpl implements RefundService {

    private final CommerceInternalClient commerceInternalClient;
    private final EventInternalClient eventInternalClient;
    private final PaymentRepository paymentRepository;

    public RefundInfoResponse getRefundInfo(UUID userId, String ticketId) {

        // Commerce — orderItem 정보 조회
        InternalOrderItemInfoResponse orderItem = commerceInternalClient.getOrderItemInfoByTicketId(ticketId);

        // Event — 행사 정보 조회
        InternalEventInfoResponse event = eventInternalClient.getEventInfo(orderItem.eventId());

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

        return new RefundInfoResponse(
            ticketId,
            event.title(),
            eventDateTime,
            orderItem.amount(),
            refundAmount,
            refundRate,
            dDay,
            isRefundable(eventDateTime),
            payment.getPaymentMethod().toString()
        );
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

    private void validateOrderOwner(UUID orderUserId, UUID userId) {
        if (!orderUserId.equals(userId)) {
            throw new PaymentException(RefundErrorCode.REFUND_INVALID_REQUEST);
        }
    }
}
