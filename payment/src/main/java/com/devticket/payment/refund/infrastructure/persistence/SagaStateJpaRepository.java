package com.devticket.payment.refund.infrastructure.persistence;

import com.devticket.payment.refund.domain.model.SagaState;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SagaStateJpaRepository extends JpaRepository<SagaState, Long> {
    Optional<SagaState> findByRefundId(UUID refundId);
}
