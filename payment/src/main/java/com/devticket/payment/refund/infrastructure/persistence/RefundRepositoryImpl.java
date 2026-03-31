package com.devticket.payment.refund.infrastructure.persistence;

import com.devticket.payment.refund.domain.model.Refund;
import com.devticket.payment.refund.domain.repository.RefundRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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

}
