package com.devticket.payment.refund.infrastructure.persistence;

import com.devticket.payment.refund.domain.model.OrderRefund;
import com.devticket.payment.refund.domain.repository.OrderRefundRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OrderRefundRepositoryImpl implements OrderRefundRepository {

    private final OrderRefundJpaRepository jpa;

    @Override
    public OrderRefund save(OrderRefund orderRefund) {
        return jpa.save(orderRefund);
    }

    @Override
    public Optional<OrderRefund> findByOrderId(UUID orderId) {
        return jpa.findByOrderId(orderId);
    }

    @Override
    public Optional<OrderRefund> findByOrderRefundId(UUID orderRefundId) {
        return jpa.findByOrderRefundId(orderRefundId);
    }
}
