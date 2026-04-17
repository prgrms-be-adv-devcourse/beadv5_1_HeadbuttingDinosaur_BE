package com.devticket.payment.refund.domain.repository;

import com.devticket.payment.refund.domain.model.SagaState;
import java.util.Optional;
import java.util.UUID;

public interface SagaStateRepository {
    SagaState save(SagaState state);
    Optional<SagaState> findByRefundId(UUID refundId);
}
