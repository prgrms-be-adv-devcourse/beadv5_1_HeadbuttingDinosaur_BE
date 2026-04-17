package com.devticket.payment.refund.domain.model;

import com.devticket.payment.common.entity.BaseEntity;
import com.devticket.payment.payment.domain.enums.PaymentMethod;
import com.devticket.payment.refund.domain.saga.SagaStatus;
import com.devticket.payment.refund.domain.saga.SagaStep;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Refund 1건당 Saga 진행 상태를 추적한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "saga_state", schema = "payment")
public class SagaState extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "refund_id", nullable = false, unique = true)
    private UUID refundId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_step", nullable = false, length = 30)
    private SagaStep currentStep;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SagaStatus status;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    public static SagaState start(UUID refundId, UUID orderId, PaymentMethod paymentMethod) {
        SagaState state = new SagaState();
        state.refundId = refundId;
        state.orderId = orderId;
        state.paymentMethod = paymentMethod;
        state.currentStep = SagaStep.ORDER_CANCELLING;
        state.status = SagaStatus.IN_PROGRESS;
        return state;
    }

    public void advance(SagaStep next) {
        this.currentStep = next;
    }

    public void markCompleted() {
        this.currentStep = SagaStep.COMPLETED;
        this.status = SagaStatus.COMPLETED;
    }

    public void markFailed(String reason) {
        this.currentStep = SagaStep.FAILED;
        this.status = SagaStatus.FAILED;
        this.failureReason = reason;
    }

    public void markCompensating(SagaStep step) {
        this.currentStep = step;
        this.status = SagaStatus.COMPENSATING;
    }

    public boolean isTerminal() {
        return this.status == SagaStatus.COMPLETED || this.status == SagaStatus.FAILED;
    }
}
