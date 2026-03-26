package com.devticket.payment.payment.application.service;

import com.devticket.payment.payment.presentation.dto.PaymentReadyRequest;
import com.devticket.payment.payment.presentation.dto.PaymentReadyResponse;

public interface PaymentService {
    PaymentReadyResponse readyPayment(Long userId, PaymentReadyRequest request);
}
