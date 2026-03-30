package com.devticket.payment.payment.presentation.controller;

import com.devticket.payment.payment.application.service.PaymentService;
import com.devticket.payment.payment.presentation.dto.InternalPaymentInfoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/payments")
@RequiredArgsConstructor
public class PaymentInternalController {

    private final PaymentService paymentService;

    @GetMapping("/by-order/{orderId}")
    public ResponseEntity<InternalPaymentInfoResponse> getPaymentByOrderId(
        @PathVariable Long orderId) {
        InternalPaymentInfoResponse response = paymentService.getPaymentByOrderId(orderId);
        return ResponseEntity.ok(response);
    }
}
