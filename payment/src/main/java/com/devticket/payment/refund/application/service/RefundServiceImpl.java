package com.devticket.payment.refund.application.service;

import com.devticket.payment.common.exception.BusinessException;
import com.devticket.payment.common.exception.CommonErrorCode;
import com.devticket.payment.common.messaging.KafkaTopics;
import com.devticket.payment.common.outbox.OutboxService;
import com.devticket.payment.payment.domain.enums.PaymentMethod;
import com.devticket.payment.payment.domain.enums.PaymentStatus;
import com.devticket.payment.payment.domain.exception.PaymentException;
import com.devticket.payment.payment.domain.model.Payment;
import com.devticket.payment.payment.domain.repository.PaymentRepository;
import com.devticket.payment.payment.infrastructure.client.CommerceInternalClient;
import com.devticket.payment.payment.infrastructure.client.dto.InternalOrderItemInfoResponse;
import com.devticket.payment.payment.infrastructure.client.dto.InternalOrderTicketsResponse;
import com.devticket.payment.refund.application.event.RefundRequestedEvent;
import com.devticket.payment.refund.application.event.TicketIssueFailedEvent;
import com.devticket.payment.refund.domain.RefundPolicyConstants;
import com.devticket.payment.refund.domain.RefundRateConstants;
import com.devticket.payment.refund.domain.enums.RefundStatus;
import com.devticket.payment.refund.domain.exception.RefundErrorCode;
import com.devticket.payment.refund.domain.exception.RefundException;
import com.devticket.payment.refund.domain.model.OrderRefund;
import com.devticket.payment.refund.domain.model.Refund;
import com.devticket.payment.refund.domain.model.RefundTicket;
import com.devticket.payment.refund.domain.repository.OrderRefundRepository;
import com.devticket.payment.refund.domain.repository.RefundRepository;
import com.devticket.payment.refund.infrastructure.client.EventInternalClient;
import com.devticket.payment.refund.infrastructure.client.dto.InternalEventInfoResponse;
import com.devticket.payment.refund.infrastructure.persistence.RefundTicketJpaRepository;
import com.devticket.payment.refund.presentation.dto.OrderRefundResponse;
import com.devticket.payment.refund.presentation.dto.PgRefundRequest;
import com.devticket.payment.refund.presentation.dto.PgRefundResponse;
import com.devticket.payment.refund.presentation.dto.RefundDetailResponse;
import com.devticket.payment.refund.presentation.dto.RefundInfoResponse;
import com.devticket.payment.refund.presentation.dto.RefundListItemResponse;
import com.devticket.payment.refund.presentation.dto.SellerRefundListItemResponse;
import com.devticket.payment.wallet.infrastructure.client.dto.InternalEventOrdersResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    private final RefundRepository refundRepository;
    private final OrderRefundRepository orderRefundRepository;
    private final RefundTicketJpaRepository refundTicketRepository;
    private final OutboxService outboxService;

    public RefundInfoResponse getRefundInfo(UUID userId, String ticketId) {

        InternalOrderItemInfoResponse orderItem = getOrderItemInfo(ticketId);
        InternalEventInfoResponse event = getEventInfo(orderItem.eventId());

        Payment payment = paymentRepository.findByOrderId(orderItem.orderId())
            .orElseThrow(() -> new PaymentException(RefundErrorCode.PAYMENT_NOT_FOUND));

        validateOrderOwner(orderItem.userId(), userId);

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
    // 티켓 단건 환불 — Saga 진입
    // =========================================================

    @Override
    @Transactional
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

        OrderRefund ledger = loadOrCreateLedger(
            orderItem.orderId(), userId, payment, payment.getAmount(),
            /* totalTickets */ estimateTotalTickets(orderItem.orderId(), 1)
        );

        if (ledger.isFullyRefunded()) {
            throw new RefundException(RefundErrorCode.ALREADY_REFUNDED);
        }

        UUID ticketUuid = UUID.fromString(ticketId);
        Refund refund = Refund.create(ledger, 1, refundAmount, refundRate);
        refundRepository.save(refund);
        refundTicketRepository.save(RefundTicket.of(refund.getRefundId(), ticketUuid));

        publishRefundRequested(refund, ledger, payment, List.of(ticketUuid), request.reason());

        return PgRefundResponse.of(
            ticketId,
            orderItem.orderId(),
            orderItem.amount(),
            refundAmount,
            refundRate,
            payment.getPaymentMethod().name(),
            RefundStatus.REQUESTED.name(),
            null
        );
    }

    // =========================================================
    // 오더 전체 환불 — 남은 ISSUED 티켓 전부 한 번에 Saga 진입
    // =========================================================

    @Override
    @Transactional
    public OrderRefundResponse refundOrder(UUID userId, UUID orderId, String reason) {
        InternalOrderTicketsResponse tickets = commerceInternalClient.getIssuedTicketsByOrder(orderId);
        if (tickets == null || tickets.tickets() == null || tickets.tickets().isEmpty()) {
            throw new RefundException(RefundErrorCode.REFUND_INVALID_REQUEST);
        }
        validateOrderOwner(tickets.userId(), userId);

        Payment payment = paymentRepository.findByOrderId(orderId)
            .orElseThrow(() -> new RefundException(RefundErrorCode.PAYMENT_NOT_FOUND));
        validateRefundablePaymentStatus(payment);

        List<InternalOrderTicketsResponse.TicketInfo> issued = tickets.tickets().stream()
            .filter(t -> "ISSUED".equalsIgnoreCase(t.status()))
            .toList();
        if (issued.isEmpty()) {
            throw new RefundException(RefundErrorCode.REFUND_INVALID_REQUEST);
        }

        UUID firstEventId = issued.get(0).eventId();
        InternalEventInfoResponse event = getEventInfo(firstEventId);
        LocalDateTime eventDateTime = LocalDateTime.parse(event.eventDateTime());

        int refundRate = calculateRefundRate(eventDateTime);
        validateRefundPolicy(refundRate);

        int perTicketRefund = calculateRefundAmount(issued.get(0).amount(), refundRate);
        int refundAmount = perTicketRefund * issued.size();

        OrderRefund ledger = loadOrCreateLedger(
            orderId, userId, payment, tickets.totalAmount(), tickets.tickets().size()
        );
        if (ledger.isFullyRefunded()) {
            throw new RefundException(RefundErrorCode.ALREADY_REFUNDED);
        }

        Refund refund = Refund.create(ledger, issued.size(), refundAmount, refundRate);
        refundRepository.save(refund);

        List<UUID> ticketIds = issued.stream()
            .map(InternalOrderTicketsResponse.TicketInfo::ticketId)
            .toList();
        for (UUID ticketId : ticketIds) {
            refundTicketRepository.save(RefundTicket.of(refund.getRefundId(), ticketId));
        }

        publishRefundRequested(refund, ledger, payment, ticketIds, reason);

        return new OrderRefundResponse(
            refund.getRefundId(),
            ledger.getOrderRefundId(),
            orderId,
            issued.size(),
            refundAmount,
            refundRate,
            payment.getPaymentMethod().name(),
            RefundStatus.REQUESTED.name()
        );
    }

    // =========================================================
    // ticket.issue-failed → 자동 100% 환불 진입점
    // =========================================================

    @Override
    @Transactional
    public void initiateAutoRefund(TicketIssueFailedEvent event) {
        Payment payment = paymentRepository.findByPaymentId(event.paymentId())
            .orElseThrow(() -> new RefundException(RefundErrorCode.PAYMENT_NOT_FOUND));

        OrderRefund ledger = loadOrCreateLedger(
            event.orderId(), event.userId(), payment,
            payment.getAmount(), event.ticketIds().size()
        );

        if (ledger.isFullyRefunded()) {
            log.info("[AutoRefund] 이미 완전 환불된 오더 — 스킵. orderId={}", event.orderId());
            return;
        }

        Refund refund = Refund.create(
            ledger, event.ticketIds().size(), event.refundAmount(), RefundRateConstants.FULL
        );
        refundRepository.save(refund);
        for (UUID ticketId : event.ticketIds()) {
            refundTicketRepository.save(RefundTicket.of(refund.getRefundId(), ticketId));
        }

        publishRefundRequested(refund, ledger, payment, event.ticketIds(),
            "ticket-issue-failed: " + event.reason());
    }

    // =========================================================
    // 내부 유틸
    // =========================================================

    private OrderRefund loadOrCreateLedger(
        UUID orderId, UUID userId, Payment payment, int totalAmount, int totalTickets
    ) {
        return orderRefundRepository.findByOrderIdForUpdate(orderId)
            .orElseGet(() -> orderRefundRepository.save(
                OrderRefund.create(
                    orderId,
                    userId,
                    payment.getPaymentId(),
                    payment.getPaymentMethod(),
                    totalAmount,
                    totalTickets
                )
            ));
    }

    /**
     * Commerce 조회 없이 단건 환불에서 사용하는 기본값.
     * 이미 Ledger 가 생성된 이후에는 영향을 주지 않는다.
     */
    private int estimateTotalTickets(UUID orderId, int fallback) {
        return orderRefundRepository.findByOrderId(orderId)
            .map(OrderRefund::getTotalTickets)
            .orElse(fallback);
    }

    private void publishRefundRequested(
        Refund refund, OrderRefund ledger, Payment payment,
        List<UUID> ticketIds, String reason
    ) {
        RefundRequestedEvent payload = RefundRequestedEvent.builder()
            .refundId(refund.getRefundId())
            .orderRefundId(ledger.getOrderRefundId())
            .orderId(refund.getOrderId())
            .userId(refund.getUserId())
            .paymentId(refund.getPaymentId())
            .paymentMethod(payment.getPaymentMethod())
            .ticketIds(ticketIds)
            .refundAmount(refund.getRefundAmount())
            .refundRate(refund.getRefundRate())
            .reason(reason)
            .timestamp(Instant.now())
            .build();

        outboxService.save(
            refund.getRefundId().toString(),
            KafkaTopics.REFUND_REQUESTED,
            KafkaTopics.REFUND_REQUESTED,
            refund.getOrderId().toString(),
            payload
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

    private InternalEventInfoResponse getEventInfo(UUID eventId) {
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
        return amount * refundRate / 100;
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

    @Override
    public Page<RefundListItemResponse> getRefundList(UUID userId, Pageable pageable) {
        return refundRepository.findByUserId(userId, pageable)
            .map(RefundListItemResponse::from);
    }

    @Override
    public RefundDetailResponse getRefundDetail(UUID userId, UUID refundId) {
        Refund refund = refundRepository.findByRefundId(refundId)
            .orElseThrow(() -> new RefundException(RefundErrorCode.REFUND_NOT_FOUND));
        validateOrderOwner(refund.getUserId(), userId);
        Payment payment = paymentRepository.findByPaymentId(refund.getPaymentId())
            .orElseThrow(() -> new RefundException(RefundErrorCode.PAYMENT_NOT_FOUND));
        return RefundDetailResponse.of(refund, payment.getPaymentMethod().name());
    }

    @Override
    public Page<SellerRefundListItemResponse> getSellerRefundListByEventId(UUID sellerId, String eventId, Pageable pageable) {
        UUID eventUUID;
        try {
            eventUUID = UUID.fromString(eventId);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }

        InternalEventInfoResponse event = getEventInfo(eventUUID);
        if (!event.sellerId().equals(sellerId)) {
            throw new RefundException(RefundErrorCode.REFUND_INVALID_REQUEST);
        }

        InternalEventOrdersResponse eventOrders = commerceInternalClient.getOrdersByEvent(UUID.fromString(eventId));

        List<UUID> orderIds = (eventOrders == null || eventOrders.getOrders() == null)
            ? Collections.emptyList()
            : eventOrders.getOrders().stream()
                .map(InternalEventOrdersResponse.OrderInfo::getOrderId)
                .toList();

        if (orderIds.isEmpty()) {
            return Page.empty(pageable);
        }

        return refundRepository.findByOrderIdInAndStatus(orderIds, RefundStatus.COMPLETED, pageable)
            .map(refund -> {
                String paymentMethod = paymentRepository.findByPaymentId(refund.getPaymentId())
                    .map(payment -> payment.getPaymentMethod().name())
                    .orElse(null);
                return SellerRefundListItemResponse.of(refund, paymentMethod);
            });
    }
}
