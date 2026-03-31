package com.devticket.payment.payment.domain.repository;

import com.devticket.payment.payment.domain.model.Payment;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findByOrderId(Long orderId);

    Optional<Payment> findById(Long id);

}
