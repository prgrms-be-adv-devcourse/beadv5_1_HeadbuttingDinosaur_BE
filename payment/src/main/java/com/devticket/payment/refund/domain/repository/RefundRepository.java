package com.devticket.payment.refund.domain.repository;

import com.devticket.payment.refund.domain.model.Refund;
import java.util.UUID;

public interface RefundRepository {
    Refund save(Refund refund);
    int sumCompletedRefundAmountByPaymentId(Long paymentId);
}
