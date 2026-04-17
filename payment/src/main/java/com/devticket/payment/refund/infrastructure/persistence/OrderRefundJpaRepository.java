package com.devticket.payment.refund.infrastructure.persistence;

import com.devticket.payment.refund.domain.model.OrderRefund;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRefundJpaRepository extends JpaRepository<OrderRefund, Long> {

    Optional<OrderRefund> findByOrderId(UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrderRefund o where o.orderId = :orderId")
    Optional<OrderRefund> findByOrderIdForUpdate(@Param("orderId") UUID orderId);
}
