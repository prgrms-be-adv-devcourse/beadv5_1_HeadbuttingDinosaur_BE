package com.devticket.payment.payment.infrastructure.persistence;

import com.devticket.payment.payment.domain.enums.PaymentMethod;
import com.devticket.payment.payment.domain.enums.PaymentStatus;
import com.devticket.payment.payment.domain.model.Payment;
import com.devticket.payment.payment.domain.repository.PaymentRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PaymentRepositoryImpl implements PaymentRepository {

    private final PaymentJpaRepository paymentJpaRepository;

    @Override
    public Payment save(Payment payment) {
        return paymentJpaRepository.save(payment);
    }

    @Override
    public Optional<Payment> findByOrderId(UUID orderId) { return paymentJpaRepository.findByOrderId(orderId); }

    @Override
    public Optional<Payment> findByPaymentId(UUID paymentId) {
        return paymentJpaRepository.findByPaymentId(paymentId);
    }

    @Override
    public List<Payment> findExpiredReadyPayments(PaymentStatus status, PaymentMethod method, LocalDateTime cutoff) {
        return paymentJpaRepository.findByStatusAndPaymentMethodAndCreatedAtBefore(status, method, cutoff);
    }
}
