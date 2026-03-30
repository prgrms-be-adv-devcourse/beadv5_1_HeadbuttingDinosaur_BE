package com.devticket.payment.payment.application.service;

import com.devticket.payment.payment.presentation.dto.InternalPaymentInfoResponse;
import com.devticket.payment.payment.presentation.dto.PaymentConfirmRequest;
import com.devticket.payment.payment.presentation.dto.PaymentConfirmResponse;
import com.devticket.payment.payment.presentation.dto.PaymentFailRequest;
import com.devticket.payment.payment.presentation.dto.PaymentFailResponse;
import com.devticket.payment.payment.presentation.dto.PaymentReadyRequest;
import com.devticket.payment.payment.presentation.dto.PaymentReadyResponse;
import java.util.UUID;

public interface PaymentService {
    PaymentReadyResponse readyPayment(UUID userId, PaymentReadyRequest request);
    PaymentConfirmResponse confirmPgPayment(UUID userId, PaymentConfirmRequest request);
    PaymentFailResponse failPgPayment(UUID userId, PaymentFailRequest request);
    InternalPaymentInfoResponse getPaymentByOrderId(Long orderId);
}
