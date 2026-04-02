package com.devticket.payment.refund.infrastructure.persistence;

import com.devticket.payment.refund.domain.enums.RefundStatus;
import com.devticket.payment.refund.domain.model.Refund;
import com.devticket.payment.refund.domain.repository.RefundRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RefundRepositoryImpl implements RefundRepository {

    private final RefundJpaRepository refundJpaRepository;

    @Override
    public Refund save(Refund refund) {
        return refundJpaRepository.save(refund);
    }

    @Override
    public int sumCompletedRefundAmountByPaymentId(Long paymentId) {
        return refundJpaRepository.sumCompletedRefundAmountByPaymentId(paymentId)
            .orElse(0);
    }

    @Override
    public Page<Refund> findByUserId(UUID userId, Pageable pageable) {
        return refundJpaRepository.findByUserId(userId, pageable);
    }

    @Override
    public Optional<Refund> findByRefundId(UUID refundId) {
        return refundJpaRepository.findByRefundId(refundId);
    }

    @Override
    public Page<Refund> findByOrderIdInAndStatus(List<UUID> orderIds, RefundStatus status, Pageable pageable) {
        return refundJpaRepository.findByOrderIdInAndStatus(orderIds, status, pageable);
    }
}
