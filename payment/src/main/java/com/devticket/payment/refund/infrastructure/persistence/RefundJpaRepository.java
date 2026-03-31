package com.devticket.payment.refund.infrastructure.persistence;

import com.devticket.payment.refund.domain.model.Refund;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefundJpaRepository extends JpaRepository<Refund, Long> {

    @Query("""
    select coalesce(sum(r.refundAmount), 0)
    from Refund r
    where r.paymentId = :paymentId
      and r.status = com.devticket.payment.refund.domain.enums.RefundStatus.COMPLETED
""")
    Optional<Integer> sumCompletedRefundAmountByPaymentId(@Param("paymentId") Long paymentId);
}
