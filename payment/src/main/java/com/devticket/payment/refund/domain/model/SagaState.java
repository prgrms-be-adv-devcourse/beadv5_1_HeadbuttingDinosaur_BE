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
    @Column(name = "status", nullable = false, length = 20)
    private SagaStatus status;

    public static SagaState create(UUID refundId, UUID orderId, PaymentMethod paymentMethod, SagaStep initialStep) {
        SagaState state = new SagaState();
        state.refundId = refundId;
        state.orderId = orderId;
        state.paymentMethod = paymentMethod;
        state.currentStep = initialStep;
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

    public void markFailed() {
        this.currentStep = SagaStep.FAILED;
        this.status = SagaStatus.FAILED;
    }

    public void markCompensating() {
        this.currentStep = SagaStep.COMPENSATING;
        this.status = SagaStatus.COMPENSATING;
    }
}
