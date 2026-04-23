package com.devticket.payment.refund.infrastructure.persistence;

import com.devticket.payment.refund.domain.model.OrderRefund;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRefundJpaRepository extends JpaRepository<OrderRefund, Long> {
    Optional<OrderRefund> findByOrderId(UUID orderId);
    Optional<OrderRefund> findByOrderRefundId(UUID orderRefundId);
}
