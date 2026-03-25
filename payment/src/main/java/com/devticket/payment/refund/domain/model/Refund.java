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
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "refund")
public class Refund extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "refund_id", nullable = false, unique = true)
    private UUID refundId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(name = "refund_amount", nullable = false)
    private Integer refundAmount;

    @Column(name = "refund_rate", nullable = false)
    private Integer refundRate;

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
        Long orderId,
        Long paymentId,
        Long userId,
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

    public void complete() {
        this.status = RefundStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
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
