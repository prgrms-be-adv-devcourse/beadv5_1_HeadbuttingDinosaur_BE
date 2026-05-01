package com.devticket.payment.payment.domain.model;

import com.devticket.payment.common.entity.BaseEntity;
import com.devticket.payment.payment.domain.enums.PaymentMethod;
import com.devticket.payment.payment.domain.enums.PaymentStatus;
import com.devticket.payment.payment.domain.exception.PaymentErrorCode;
import com.devticket.payment.payment.domain.exception.PaymentException;
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
@Table(name = "payment", schema = "payment")
public class Payment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "payment_id", nullable = false, unique = true)
    private UUID paymentId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "payment_key")
    private String paymentKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;

    @Column(nullable = false)
    private Integer amount;

    @Column(name = "wallet_amount")
    private Integer walletAmount;

    @Column(name = "pg_amount")
    private Integer pgAmount;

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
        UUID orderId,
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
        payment.walletAmount = 0;
        payment.pgAmount = 0;
        payment.status = PaymentStatus.READY;
        return payment;
    }

    public static Payment create(
        UUID orderId,
        UUID userId,
        PaymentMethod method,
        Integer amount,
        Integer walletAmount,
        Integer pgAmount
    ) {
        Payment payment = new Payment();
        payment.paymentId = UUID.randomUUID();
        payment.orderId = orderId;
        payment.userId = userId;
        payment.paymentMethod = method;
        payment.amount = amount;
        payment.walletAmount = walletAmount;
        payment.pgAmount = pgAmount;
        payment.status = PaymentStatus.READY;
        return payment;
    }

    /* =======================
        상태 변경 메서드
       ======================= */

    /**
     * READY 상태인 Payment를 다른 결제수단/금액으로 재초기화.
     * status / paymentId / orderId / userId 는 보존, 결제 정보만 갱신.
     */
    public void resetForRetry(PaymentMethod method, Integer amount, Integer walletAmount, Integer pgAmount) {
        if (this.status != PaymentStatus.READY) {
            throw new PaymentException(PaymentErrorCode.INVALID_STATUS_TRANSITION);
        }
        this.paymentMethod = method;
        this.amount = amount;
        this.walletAmount = walletAmount != null ? walletAmount : 0;
        this.pgAmount = pgAmount != null ? pgAmount : 0;
    }

    public void approve(String paymentKey) {
        validateTransition(PaymentStatus.SUCCESS);
        this.paymentKey = paymentKey;
        this.status = PaymentStatus.SUCCESS;
        this.approvedAt = LocalDateTime.now();
    }

    public void approve(String paymentKey, LocalDateTime approvedAt) {
        validateTransition(PaymentStatus.SUCCESS);
        this.paymentKey = paymentKey;
        this.status = PaymentStatus.SUCCESS;
        this.approvedAt = approvedAt;
    }

    public void fail(String reason) {
        validateTransition(PaymentStatus.FAILED);
        this.status = PaymentStatus.FAILED;
        this.failureReason = reason;
    }

    public void cancel() {
        validateTransition(PaymentStatus.CANCELLED);
        this.status = PaymentStatus.CANCELLED;
    }

    public void refund() {
        validateTransition(PaymentStatus.REFUNDED);
        this.status = PaymentStatus.REFUNDED;
    }

    public boolean canTransitionTo(PaymentStatus target) {
        return this.status.canTransitionTo(target);
    }

    private void validateTransition(PaymentStatus target) {
        if (!this.status.canTransitionTo(target)) {
            throw new PaymentException(PaymentErrorCode.INVALID_STATUS_TRANSITION);
        }
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }
}
