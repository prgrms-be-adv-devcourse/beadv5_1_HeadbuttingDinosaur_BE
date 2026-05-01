package com.devticket.payment.payment.infrastructure.persistence;

import com.devticket.payment.payment.domain.enums.PaymentMethod;
import com.devticket.payment.payment.domain.enums.PaymentStatus;
import com.devticket.payment.payment.domain.model.Payment;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentJpaRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByOrderId(UUID orderId);
    Optional<Payment> findByPaymentId(UUID paymentId);
    List<Payment> findByStatusAndPaymentMethodAndCreatedAtBefore(
        PaymentStatus status, PaymentMethod paymentMethod, LocalDateTime cutoff);
}
