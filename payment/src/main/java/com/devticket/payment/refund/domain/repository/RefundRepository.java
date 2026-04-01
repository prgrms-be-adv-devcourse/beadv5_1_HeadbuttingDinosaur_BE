package com.devticket.payment.refund.domain.repository;

import com.devticket.payment.refund.domain.model.Refund;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface RefundRepository {
    Refund save(Refund refund);
    int sumCompletedRefundAmountByPaymentId(Long paymentId);
    Page<Refund> findByUserId(UUID userId, Pageable pageable);
    Optional<Refund> findByRefundId(UUID refundId);
    Page<Refund> findByOrderIdIn(List<Long> orderIds, Pageable pageable);
}
