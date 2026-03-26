package com.devticket.payment.payment.infrastructure.persistence;

import com.devticket.payment.payment.domain.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentJpaRepository extends JpaRepository<Payment, Long> {


}
