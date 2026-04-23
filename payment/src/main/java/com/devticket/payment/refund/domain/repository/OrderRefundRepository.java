package com.devticket.payment.refund.domain.repository;

import com.devticket.payment.refund.domain.model.OrderRefund;
import java.util.Optional;
import java.util.UUID;

public interface OrderRefundRepository {
    OrderRefund save(OrderRefund orderRefund);
    Optional<OrderRefund> findByOrderId(UUID orderId);
    Optional<OrderRefund> findByOrderRefundId(UUID orderRefundId);
}
