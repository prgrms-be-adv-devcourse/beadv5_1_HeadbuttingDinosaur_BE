package com.devticket.payment.payment.domain.repository;

import com.devticket.payment.payment.domain.model.Payment;

public interface PaymentRepository {

    Payment save(Payment payment);

}
