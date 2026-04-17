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

    private final OrderRefundJpaRepository jpaRepository;

    @Override
    public OrderRefund save(OrderRefund orderRefund) {
        return jpaRepository.save(orderRefund);
    }

    @Override
    public Optional<OrderRefund> findByOrderId(UUID orderId) {
        return jpaRepository.findByOrderId(orderId);
    }

    @Override
    public Optional<OrderRefund> findByOrderIdForUpdate(UUID orderId) {
        return jpaRepository.findByOrderIdForUpdate(orderId);
    }
}
