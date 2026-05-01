package com.devticket.payment.payment.domain.repository;

import com.devticket.payment.payment.domain.enums.PaymentMethod;
import com.devticket.payment.payment.domain.enums.PaymentStatus;
import com.devticket.payment.payment.domain.model.Payment;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findByOrderId(UUID orderId);

    Optional<Payment> findByPaymentId(UUID id);

    List<Payment> findExpiredReadyPayments(PaymentStatus status, PaymentMethod method, LocalDateTime cutoff);

}
