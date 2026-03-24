package com.devticket.payment.payment.presentation.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mock/pg")
public class MockPgController {

    public static class MockPgConfirmResponse{
        String paymentKey;
        String status;

        public MockPgConfirmResponse(String paymentKey, String status) {
            this.paymentKey = paymentKey;
            this.status = status;
        }
    }

    public static class MockPgRefundResponse{
        String status;

        public MockPgRefundResponse(String status) {
            this.status = status;
        }
    }

    @PostMapping("/payments/confirm")
    public MockPgConfirmResponse confirmPayment() {
        return new MockPgConfirmResponse(
            "mock_pg_key",
            "SUCCESS"
        );
    }

    @PostMapping("/refunds")
    public MockPgRefundResponse refundPayment() {
        return new MockPgRefundResponse(
            "REFUND_SUCCESS"
        );
    }
}
