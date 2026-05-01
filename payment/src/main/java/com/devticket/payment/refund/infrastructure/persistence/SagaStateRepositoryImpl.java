package com.devticket.payment.refund.infrastructure.persistence;

import com.devticket.payment.refund.domain.model.SagaState;
import com.devticket.payment.refund.domain.repository.SagaStateRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SagaStateRepositoryImpl implements SagaStateRepository {

    private final SagaStateJpaRepository jpa;

    @Override
    public SagaState save(SagaState sagaState) {
        return jpa.save(sagaState);
    }

    @Override
    public Optional<SagaState> findByRefundId(UUID refundId) {
        return jpa.findByRefundId(refundId);
    }
}
