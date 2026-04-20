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
import com.devticket.payment.payment.infrastructure.client.dto.InternalOrderInfoResponse;
import com.devticket.payment.payment.infrastructure.client.dto.InternalOrderItemInfoResponse;
import com.devticket.payment.refund.application.saga.event.RefundRequestedEvent;
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
import com.devticket.payment.refund.domain.repository.RefundTicketRepository;
import com.devticket.payment.refund.infrastructure.client.EventInternalClient;
import com.devticket.payment.refund.infrastructure.client.dto.InternalEventInfoResponse;
import com.devticket.payment.refund.presentation.dto.OrderRefundResponse;
import com.devticket.payment.refund.presentation.dto.RefundDetailResponse;
import com.devticket.payment.refund.presentation.dto.RefundInfoResponse;
import com.devticket.payment.refund.presentation.dto.RefundListItemResponse;
import com.devticket.payment.refund.presentation.dto.SellerRefundListItemResponse;
import com.devticket.payment.refund.presentation.dto.PgRefundRequest;
import com.devticket.payment.refund.presentation.dto.PgRefundResponse;
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
@Transactional
@RequiredArgsConstructor
@Slf4j
public class RefundServiceImpl implements RefundService {

    private final CommerceInternalClient commerceInternalClient;
    private final EventInternalClient eventInternalClient;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final OrderRefundRepository orderRefundRepository;
    private final RefundTicketRepository refundTicketRepository;
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
    // 티켓 단건 환불 — Saga 진입점 B (사용자 요청)
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

        UUID ticketUuid = UUID.fromString(ticketId);

        OrderRefund ledger = upsertOrderRefund(
            orderItem.orderId(), userId, payment.getPaymentId(),
            payment.getPaymentMethod(), payment.getAmount(),
            inferOrderTotalTickets(orderItem.orderId(), payment)
        );

        if (ledger.isFullyRefunded()) {
            throw new RefundException(RefundErrorCode.ALREADY_REFUNDED);
        }

        Refund refund = Refund.create(
            ledger.getOrderRefundId(),
            orderItem.orderId(),
            payment.getPaymentId(),
            userId,
            refundAmount,
            refundRate
        );
        refundRepository.save(refund);
        refundTicketRepository.save(RefundTicket.of(refund.getRefundId(), ticketUuid));

        RefundRequestedEvent requested = new RefundRequestedEvent(
            refund.getRefundId(),
            orderItem.orderId(),
            userId,
            payment.getPaymentId(),
            payment.getPaymentMethod(),
            List.of(ticketUuid),
            refundAmount,
            refundRate,
            false,
            Instant.now()
        );
        outboxService.save(
            refund.getRefundId().toString(),
            KafkaTopics.REFUND_REQUESTED,
            KafkaTopics.REFUND_REQUESTED,
            orderItem.orderId().toString(),
            requested
        );

        log.info("[Refund] 티켓 단건 환불 요청 — refundId={}, ticketId={}, amount={}",
            refund.getRefundId(), ticketId, refundAmount);

        return PgRefundResponse.of(
            ticketId,
            orderItem.orderId(),
            orderItem.amount(),
            refundAmount,
            refundRate,
            payment.getPaymentMethod().name(),
            refund.getStatus().name(),
            null
        );
    }

    // =========================================================
    // 오더 전체 환불 — Saga 진입점 C (사용자 요청)
    // =========================================================

    @Override
    public OrderRefundResponse refundOrder(UUID userId, UUID orderId, String reason) {
        InternalOrderInfoResponse orderInfo = commerceInternalClient.getOrderInfo(orderId);
        if (orderInfo == null) {
            throw new RefundException(RefundErrorCode.TICKET_NOT_FOUND);
        }
        validateOrderOwner(orderInfo.userId(), userId);

        Payment payment = paymentRepository.findByOrderId(orderId)
            .orElseThrow(() -> new RefundException(RefundErrorCode.PAYMENT_NOT_FOUND));
        validateRefundablePaymentStatus(payment);

        // 주문의 첫 이벤트 기준으로 환불율 산정 (다중 이벤트 주문은 별도 정책 필요 — 단일 이벤트 기준)
        int refundRate = RefundRateConstants.FULL;
        if (orderInfo.orderItems() != null && !orderInfo.orderItems().isEmpty()) {
            UUID firstEventId = orderInfo.orderItems().get(0).eventId();
            InternalEventInfoResponse event = getEventInfo(firstEventId);
            LocalDateTime eventDateTime = LocalDateTime.parse(event.eventDateTime());
            refundRate = calculateRefundRate(eventDateTime);
        }
        validateRefundPolicy(refundRate);

        int totalTickets = orderInfo.orderItems() == null ? 1
            : orderInfo.orderItems().stream().mapToInt(InternalOrderInfoResponse.OrderItem::quantity).sum();

        OrderRefund ledger = upsertOrderRefund(
            orderId, userId, payment.getPaymentId(),
            payment.getPaymentMethod(), payment.getAmount(),
            totalTickets
        );

        if (ledger.isFullyRefunded()) {
            throw new RefundException(RefundErrorCode.ALREADY_REFUNDED);
        }

        int refundAmount = calculateRefundAmount(ledger.getRemainingAmount(), refundRate);

        Refund refund = Refund.create(
            ledger.getOrderRefundId(),
            orderId,
            payment.getPaymentId(),
            userId,
            refundAmount,
            refundRate
        );
        refundRepository.save(refund);
        // wholeOrder = true — ticketIds 는 Commerce 측이 ticket.done 회신 시 채움

        RefundRequestedEvent requested = new RefundRequestedEvent(
            refund.getRefundId(),
            orderId,
            userId,
            payment.getPaymentId(),
            payment.getPaymentMethod(),
            Collections.emptyList(),
            refundAmount,
            refundRate,
            true,
            Instant.now()
        );
        outboxService.save(
            refund.getRefundId().toString(),
            KafkaTopics.REFUND_REQUESTED,
            KafkaTopics.REFUND_REQUESTED,
            orderId.toString(),
            requested
        );

        log.info("[Refund] 오더 전체 환불 요청 — refundId={}, orderId={}, amount={}, reason={}",
            refund.getRefundId(), orderId, refundAmount, reason);

        return new OrderRefundResponse(
            refund.getRefundId(),
            ledger.getOrderRefundId(),
            orderId,
            refundAmount,
            refundRate,
            payment.getPaymentMethod().name(),
            refund.getStatus().name()
        );
    }

    // =========================================================
    // Helpers
    // =========================================================

    private OrderRefund upsertOrderRefund(
        UUID orderId, UUID userId, UUID paymentId,
        PaymentMethod method, int totalAmount, int totalTickets
    ) {
        return orderRefundRepository.findByOrderId(orderId)
            .orElseGet(() -> orderRefundRepository.save(
                OrderRefund.create(orderId, userId, paymentId, method, totalAmount, totalTickets)
            ));
    }

    private int inferOrderTotalTickets(UUID orderId, Payment payment) {
        try {
            InternalOrderInfoResponse info = commerceInternalClient.getOrderInfo(orderId);
            if (info != null && info.orderItems() != null) {
                return Math.max(1, info.orderItems().stream()
                    .mapToInt(InternalOrderInfoResponse.OrderItem::quantity)
                    .sum());
            }
        } catch (Exception e) {
            log.warn("[Refund] order info 조회 실패, totalTickets=1 로 보정 — orderId={}", orderId);
        }
        return 1;
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
    @Transactional(readOnly = true)
    public Page<RefundListItemResponse> getRefundList(UUID userId, Pageable pageable) {
        return refundRepository.findByUserId(userId, pageable)
            .map(RefundListItemResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public RefundDetailResponse getRefundDetail(UUID userId, UUID refundId) {
        Refund refund = refundRepository.findByRefundId(refundId)
            .orElseThrow(() -> new RefundException(RefundErrorCode.REFUND_NOT_FOUND));
        validateOrderOwner(refund.getUserId(), userId);
        Payment payment = paymentRepository.findByPaymentId(refund.getPaymentId())
            .orElseThrow(() -> new RefundException(RefundErrorCode.PAYMENT_NOT_FOUND));
        return RefundDetailResponse.of(refund, payment.getPaymentMethod().name());
    }

    @Override
    @Transactional(readOnly = true)
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
