package com.devticket.payment.refund.domain.model;

import com.devticket.payment.common.entity.BaseEntity;
import com.devticket.payment.refund.domain.enums.RefundStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "refund", schema = "payment")
public class Refund extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "refund_id", nullable = false, unique = true)
    private UUID refundId;

    @Column(name = "order_refund_id")
    private UUID orderRefundId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "refund_amount", nullable = false)
    private Integer refundAmount;

    @Column(name = "refund_rate", nullable = false)
    private Integer refundRate;

    @Column(name = "ticket_count", nullable = false)
    private Integer ticketCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RefundStatus status;

    @Column(name = "requested_at")
    private LocalDateTime requestedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /* =======================
        정적 팩토리 메서드
       ======================= */

    public static Refund create(
        UUID orderId,
        UUID paymentId,
        UUID userId,
        Integer amount,
        Integer refundRate
    ) {
        Refund refund = new Refund();
        refund.refundId = UUID.randomUUID();
        refund.orderId = orderId;
        refund.paymentId = paymentId;
        refund.userId = userId;
        refund.refundAmount = amount;
        refund.refundRate = refundRate;
        refund.ticketCount = 1;
        refund.status = RefundStatus.REQUESTED;
        refund.requestedAt = LocalDateTime.now();
        return refund;
    }

    public static Refund create(
        OrderRefund orderRefund,
        int ticketCount,
        int refundAmount,
        int refundRate
    ) {
        Refund refund = new Refund();
        refund.refundId = UUID.randomUUID();
        refund.orderRefundId = orderRefund.getOrderRefundId();
        refund.orderId = orderRefund.getOrderId();
        refund.userId = orderRefund.getUserId();
        refund.paymentId = orderRefund.getPaymentId();
        refund.refundAmount = refundAmount;
        refund.refundRate = refundRate;
        refund.ticketCount = ticketCount;
        refund.status = RefundStatus.REQUESTED;
        refund.requestedAt = LocalDateTime.now();
        return refund;
    }

    /* =======================
        상태 변경
       ======================= */

    public void approve() {
        this.status = RefundStatus.APPROVED;
    }

    public void complete(LocalDateTime canceledAt) {
        this.status = RefundStatus.COMPLETED;
        this.completedAt = canceledAt;
    }

    public void reject() {
        this.status = RefundStatus.REJECTED;
    }

    public void fail() {
        this.status = RefundStatus.FAILED;
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }
}
