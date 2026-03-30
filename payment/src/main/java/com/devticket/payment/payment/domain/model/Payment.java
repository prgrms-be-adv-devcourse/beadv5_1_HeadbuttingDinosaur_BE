package com.devticket.payment.payment.domain.model;

import com.devticket.payment.common.entity.BaseEntity;
import com.devticket.payment.payment.domain.enums.PaymentMethod;
import com.devticket.payment.payment.domain.enums.PaymentStatus;
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
@Table(name = "payment")
public class Payment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false, unique = true)
    private UUID paymentId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "payment_key")
    private String paymentKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;

    @Column(nullable = false)
    private Integer amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /* =======================
        정적 팩토리 메서드
       ======================= */

    public static Payment create(
        Long orderId,
        UUID userId,
        PaymentMethod method,
        Integer amount
    ) {
        Payment payment = new Payment();
        payment.paymentId = UUID.randomUUID();
        payment.orderId = orderId;
        payment.userId = userId;
        payment.paymentMethod = method;
        payment.amount = amount;
        payment.status = PaymentStatus.READY;
        return payment;
    }

    /* =======================
        상태 변경 메서드
       ======================= */

    public void approve(String paymentKey) {
        this.paymentKey = paymentKey;
        this.status = PaymentStatus.SUCCESS;
        this.approvedAt = LocalDateTime.now();
    }

    public void approve(String paymentKey, LocalDateTime approvedAt) {
        this.paymentKey = paymentKey;
        this.status = PaymentStatus.SUCCESS;
        this.approvedAt = approvedAt;
    }

    public void fail(String reason) {
        this.status = PaymentStatus.FAILED;
        this.failureReason = reason;
    }

    public void cancel() {
        this.status = PaymentStatus.CANCELLED;
    }

    public void refund() {
        this.status = PaymentStatus.REFUNDED;
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }
}
